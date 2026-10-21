package com.uydutv.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** M3U oynatma listesini indirir, ayrıştırır ve çevrimdışı kullanım için diske kaydeder. */
object PlaylistLoader {
    private const val CACHE_FILE = "playlist.m3u"
    private val attr = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    fun parse(text: String): List<TvChannel> {
        val out = mutableListOf<TvChannel>()
        var name: String? = null
        var tvgId: String? = null
        var logo: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    name = line.substringAfterLast(",", "").trim().ifBlank { attrs["tvg-name"] ?: "Kanal" }
                }
                line.isNotEmpty() && !line.startsWith("#") && name != null -> {
                    if (line.startsWith("http://") || line.startsWith("https://")) {
                        val n = out.size + 1
                        out.add(TvChannel(
                            number = n, id = "pl$n", name = name!!, logoUrl = logo, streamUrl = line,
                            xmltvId = tvgId,
                            logoStatus = VerificationStatus.NOT_VERIFIED,
                            streamStatus = VerificationStatus.USER_PROVIDED,
                            epgStatus = VerificationStatus.NOT_VERIFIED,
                            notes = "Kullanıcı oynatma listesinden"
                        ))
                    }
                    name = null; tvgId = null; logo = null
                }
            }
        }
        return ChannelOrder.apply(out)
    }

    fun loadCached(ctx: Context): List<TvChannel>? = try {
        val f = File(ctx.filesDir, CACHE_FILE)
        if (f.exists()) parse(f.readText()).takeIf { it.isNotEmpty() } else null
    } catch (_: Exception) { null }

    /** Ağ iş parçacığında çağrılmalı. Başarısızsa null döner. */
    fun download(ctx: Context): List<TvChannel>? = try {
        val conn = (URL(ChannelCatalog.PLAYLIST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000; readTimeout = 20000
            setRequestProperty("User-Agent", "CNDNTV/1.0 AndroidTV")
        }
        val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val list = parse(text)
        if (list.isNotEmpty()) File(ctx.filesDir, CACHE_FILE).writeText(text)
        list.takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }
}
