package com.uydutv.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * M3U oynatma listesini indirir, ayrıştırır ve çevrimdışı kullanım için diske önbelleğe kaydeder.
 */
object PlaylistLoader {
    private const val TAG = "PlaylistLoader"
    private const val CACHE_FILE = "playlist_tr.m3u"
    private const val LEGACY_CACHE_FILE = "playlist.m3u"

    // #EXTINF satırındaki anahtar="değer" veya anahtar='değer' veya anahtar=değer niteliklerini yakalar
    private val attrRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""")

    /**
     * M3U metnini ayrıştırır.
     * UTF-8 Türkçe karakterleri, tvg-id, tvg-name, tvg-logo, group-title ve #EXTGRP etiketlerini destekler.
     * Boş ad, boş yayın adresi veya doğrudan .m3u dosyası olan kayıtlar filtrelenir.
     */
    fun parse(text: String): List<TvChannel> {
        val cleanText = if (text.startsWith("\uFEFF")) text.substring(1) else text
        val out = mutableListOf<TvChannel>()
        var name: String? = null
        var tvgId: String? = null
        var logo: String? = null
        var groups: List<String> = emptyList()
        val seenUrls = HashSet<String>()

        for (raw in cleanText.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = mutableMapOf<String, String>()
                    for (match in attrRegex.findAll(line)) {
                        val key = match.groupValues[1].lowercase(Locale.ROOT)
                        val value = match.groupValues[2].ifEmpty { match.groupValues[3] }.ifEmpty { match.groupValues[4] }
                        if (value.isNotBlank()) {
                            attrs[key] = value
                        }
                    }

                    tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }

                    val groupAttr = attrs["group-title"] ?: attrs["group"]
                    groups = groupAttr?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

                    val commaIndex = line.lastIndexOf(',')
                    val titleAfterComma = if (commaIndex >= 0) line.substring(commaIndex + 1).trim() else ""
                    name = titleAfterComma.ifBlank {
                        attrs["tvg-name"]?.trim() ?: attrs["tvg-id"]?.trim() ?: ""
                    }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val grp = line.substringAfter(":").trim()
                    if (grp.isNotEmpty() && groups.isEmpty()) {
                        groups = listOf(grp)
                    }
                }
                !line.startsWith("#") && name != null -> {
                    val streamUrl = line.trim()
                    // URL ve kanal adı doğrulaması:
                    // 1. Boş kanal adı veya boş URL olmamalı
                    // 2. M3U dosyasının kendisi video akışı olarak eklenmemeli (.m3u8 hariç)
                    // 3. Desteklenen protokol olmalı
                    val isPlayableUrl = (streamUrl.startsWith("http://", ignoreCase = true) ||
                            streamUrl.startsWith("https://", ignoreCase = true) ||
                            streamUrl.startsWith("rtmp://", ignoreCase = true) ||
                            streamUrl.startsWith("rtsp://", ignoreCase = true)) &&
                            (!streamUrl.endsWith(".m3u", ignoreCase = true) || streamUrl.endsWith(".m3u8", ignoreCase = true))

                    if (name.isNotBlank() && isPlayableUrl && seenUrls.add(streamUrl)) {
                        val n = out.size + 1
                        val cleanId = "ch_${Math.abs(streamUrl.hashCode().toLong())}_$n"
                        out.add(
                            TvChannel(
                                number = n,
                                id = cleanId,
                                name = name,
                                logoUrl = logo,
                                streamUrl = streamUrl,
                                xmltvId = tvgId,
                                logoStatus = if (logo != null) VerificationStatus.USER_PROVIDED else VerificationStatus.NOT_VERIFIED,
                                streamStatus = VerificationStatus.USER_PROVIDED,
                                epgStatus = if (tvgId != null) VerificationStatus.USER_PROVIDED else VerificationStatus.NOT_VERIFIED,
                                notes = "M3U akışı",
                                groups = groups
                            )
                        )
                    }
                    name = null
                    tvgId = null
                    logo = null
                    groups = emptyList()
                }
            }
        }

        return ChannelOrder.apply(out)
    }

    /**
     * Cihazda kayıtlı son geçerli M3U önbelleğini yükler.
     */
    fun loadCached(ctx: Context): List<TvChannel>? {
        return try {
            val f = File(ctx.filesDir, CACHE_FILE).let {
                if (it.exists() && it.length() > 0) it else File(ctx.filesDir, LEGACY_CACHE_FILE)
            }
            if (f.exists() && f.length() > 0) {
                val content = f.readText(StandardCharsets.UTF_8)
                parse(content).takeIf { it.isNotEmpty() }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Önbellek okunamadı: ${e.message}")
            null
        }
    }

    /**
     * M3U listesini internet üzerinden indirir.
     * Öncelikli olarak `ChannelCatalog.PRIMARY_PLAYLIST_URL` kullanılır.
     * Başarılı indirme ve ayrıştırma sonucunda içerik cihaza önbelleğe alınır.
     * Ağ hatası veya bozuk içerik durumunda çökmez; null döndürür.
     */
    fun download(ctx: Context): List<TvChannel>? {
        val urlsToTry = ChannelCatalog.PLAYLIST_URLS

        for (urlStr in urlsToTry) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 25000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "CNDNTV/1.0 AndroidTV")
                    setRequestProperty("Accept", "*/*")
                }

                val responseCode = try { conn.responseCode } catch (e: Exception) {
                    conn.disconnect()
                    Log.w(TAG, "Bağlantı hatası ($urlStr): ${e.message}")
                    continue
                }

                if (responseCode in 200..299) {
                    val content = conn.inputStream.use { input ->
                        InputStreamReader(input, StandardCharsets.UTF_8).buffered().use { reader ->
                            reader.readText()
                        }
                    }
                    conn.disconnect()

                    if (content.isNotBlank()) {
                        val parsed = parse(content)
                        if (parsed.isNotEmpty()) {
                            // Başarılı listeyi güvenli şekilde önbelleğe kaydet
                            try {
                                val cacheFile = File(ctx.filesDir, CACHE_FILE)
                                cacheFile.writeText(content, StandardCharsets.UTF_8)
                            } catch (e: Exception) {
                                Log.w(TAG, "Önbelleğe kaydedilemedi: ${e.message}")
                            }
                            return parsed
                        }
                    }
                } else {
                    conn.disconnect()
                    Log.w(TAG, "Sunucu yanıt kodu ($responseCode) - $urlStr")
                }
            } catch (e: Exception) {
                Log.w(TAG, "İndirme hatası ($urlStr): ${e.message}")
            }
        }

        return null
    }
}
