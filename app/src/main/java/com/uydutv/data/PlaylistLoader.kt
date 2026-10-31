package com.uydutv.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * M3U oynatma listelerini indirir, ayrıştırır, birden fazla kaynaktan gelen
 * aynı kanalların akışlarını birleştirir, şifreli platformları eler ve diske önbelleğe kaydeder.
 */
object PlaylistLoader {
    private const val TAG = "PlaylistLoader"
    private const val CACHE_JSON_FILE = "merged_playlist.json"
    private const val CACHE_FILE = "playlist_tr.m3u"
    private const val LEGACY_CACHE_FILE = "playlist.m3u"

    // #EXTINF satırındaki anahtar="değer" veya anahtar='değer' veya anahtar=değer niteliklerini yakalar
    private val attrRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""")

    // Şifreli ve abonelikli platform yayınlarını ayıklayan kara liste
    private val blockedKeywords = listOf(
        "digiturk", "beinsports", "bein sports", "bein connect", "d-smart", "dsmart",
        "tivibu", "exxen", "s sport", "ssport", "s-sport", "tod tv", "blutv", "gain"
    )

    /**
     * Şifreli / ücretli platform yayını olup olmadığını denetler. Yalnızca şifresiz (FTA) kanallar kalır.
     */
    fun isPaywalledOrRestricted(name: String, groups: List<String>, streamUrl: String): Boolean {
        val text = "$name ${groups.joinToString(" ")} $streamUrl".lowercase(Locale.ROOT)
        return blockedKeywords.any { text.contains(it) }
    }

    private val parenthesisRegex = Regex("""\([^)]*\)|\[[^\]]*\]|\{[^}]*\}""")

    /**
     * Kanal isimlerindeki parantez ve köşeli parantez içindeki açıklamaları temizler.
     * Sadece net kanal ismini bırakır (Örn: "Kanal D (Canlı)" -> "Kanal D", "TRT 1 (1080p)" -> "TRT 1").
     */
    fun cleanChannelName(raw: String): String {
        val stripped = raw.replace(parenthesisRegex, " ").replace(Regex("""\s+"""), " ").trim()
        return stripped.ifEmpty { raw.trim() }
    }

    /**
     * M3U metnini ayrıştırır.
     * UTF-8 Türkçe karakterleri, tvg-id, tvg-name, tvg-logo, group-title ve #EXTGRP etiketlerini destekler.
     * Boş ad, boş yayın adresi, doğrudan .m3u dosyası veya şifreli platform yayınları filtrelenir.
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
                    val rawTitle = titleAfterComma.ifBlank {
                        attrs["tvg-name"]?.trim() ?: attrs["tvg-id"]?.trim() ?: ""
                    }
                    name = cleanChannelName(rawTitle)
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val grp = line.substringAfter(":").trim()
                    if (grp.isNotEmpty() && groups.isEmpty()) {
                        groups = listOf(grp)
                    }
                }
                !line.startsWith("#") && name != null -> {
                    val streamUrl = line.trim()
                    val isPlayableUrl = (streamUrl.startsWith("http://", ignoreCase = true) ||
                            streamUrl.startsWith("https://", ignoreCase = true) ||
                            streamUrl.startsWith("rtmp://", ignoreCase = true) ||
                            streamUrl.startsWith("rtsp://", ignoreCase = true)) &&
                            (!streamUrl.endsWith(".m3u", ignoreCase = true) || streamUrl.endsWith(".m3u8", ignoreCase = true))

                    val isAllowed = !isPaywalledOrRestricted(name, groups, streamUrl)

                    if (name.isNotBlank() && isPlayableUrl && isAllowed && seenUrls.add(streamUrl)) {
                        val n = out.size + 1
                        val cleanId = "ch_${Math.abs(streamUrl.hashCode().toLong())}_$n"
                        val catalogMatch = ChannelCatalog.findOfficialChannel(name)
                        out.add(
                            TvChannel(
                                number = n,
                                id = cleanId,
                                name = name,
                                logoUrl = logo ?: catalogMatch?.logoUrl,
                                streamUrl = streamUrl,
                                streamUrls = listOf(streamUrl),
                                officialLivePage = catalogMatch?.officialLivePage,
                                xmltvId = tvgId ?: catalogMatch?.xmltvId,
                                logoStatus = if (logo != null) VerificationStatus.USER_PROVIDED else (catalogMatch?.logoStatus ?: VerificationStatus.NOT_VERIFIED),
                                streamStatus = VerificationStatus.USER_PROVIDED,
                                epgStatus = if (tvgId != null) VerificationStatus.USER_PROVIDED else (catalogMatch?.epgStatus ?: VerificationStatus.NOT_VERIFIED),
                                notes = "M3U akışı",
                                groups = if (groups.isNotEmpty()) groups else (catalogMatch?.groups ?: emptyList()),
                                isFreeAccess = true,
                                permissionStatus = catalogMatch?.permissionStatus ?: "M3U Yayını (FTA / Serbest)",
                                sourceOrigin = "M3U Kaynağı",
                                lastCheckDate = catalogMatch?.lastCheckDate ?: "2026-10-30"
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

    @Volatile
    private var cachedDogusToken: String = "de13baf2a095e421aad499fa34d560d9205ec5b4b28a3371"
    private var lastTokenFetchTime: Long = 0L

    /**
     * Doğuş Yayın Grubu (Star TV, Euro Star, NTV) için geçerli canlı yayın yetkilendirme belirtecini alır.
     */
    fun getDogusToken(): String {
        val now = System.currentTimeMillis()
        if (now - lastTokenFetchTime > 12 * 60 * 60 * 1000L) {
            try {
                val conn = (URL("https://www.eurostartv.com.tr/canli-izle").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
                if (conn.responseCode in 200..299) {
                    val html = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    val match = Regex("""token=([a-f0-9]{30,})""").find(html)
                    if (match != null) {
                        cachedDogusToken = match.groupValues[1]
                        lastTokenFetchTime = now
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Doğuş token yenilenemedi, önbellekteki token kullanılıyor: ${e.message}")
            }
        }
        return cachedDogusToken
    }

    /**
     * Birden fazla M3U listesini birleştirir.
     * Aynı kanalların akışlarını tek bir TvChannel altında streamUrls listesinde toplar.
     * Hızlı açılan akışları öne alacak şekilde sıralar.
     */
    fun mergeChannelLists(lists: List<List<TvChannel>>, ctx: Context? = null): List<TvChannel> {
        val grouped = linkedMapOf<String, MutableList<TvChannel>>()

        for (list in lists) {
            for (channel in list) {
                if (isPaywalledOrRestricted(channel.name, channel.groups, channel.streamUrl ?: "")) {
                    continue
                }
                val normName = ChannelOrder.normalize(channel.name)
                if (normName.isBlank()) continue
                val key = ChannelOrder.slotKeyForName(channel.name) ?: normName
                grouped.getOrPut(key) { mutableListOf() }.add(channel)
            }
        }

        val result = mutableListOf<TvChannel>()
        for ((key, groupChannels) in grouped) {
            val primary = groupChannels.first()

            // Tüm aday akış adreslerini birleştir
            val allStreams = mutableListOf<String>()
            val seen = HashSet<String>()
            for (ch in groupChannels) {
                val streams = if (ch.streamUrls.isNotEmpty()) ch.streamUrls else listOfNotNull(ch.streamUrl)
                for (st in streams) {
                    if (seen.add(st)) {
                        allStreams.add(st)
                    }
                }
            }

            // Star TV (slot_4) için bölgesel kısıtlamasız (Avrupa / Doğuş MNCDN) akışları önceliklendir
            if (key == "slot_4" || primary.id == "startv" || primary.name.contains("Star", ignoreCase = true)) {
                val token = getDogusToken()
                val dogusFeeds = listOf(
                    "https://dygvideo.dygdigital.com/live/hls/staravrupa?token=$token",
                    "https://dygvideo.dygdigital.com/live/hls/startv?token=$token",
                    "https://dogus.daioncdn.net/startv/startv.m3u8?app=startv_web"
                )
                // Kısıtlamasız akışları listenin başına al
                for (feed in dogusFeeds.reversed()) {
                    allStreams.remove(feed)
                    allStreams.add(0, feed)
                }
                // Bölgesel yayın hakkı kısıtlaması (ce=3) içeren akışı en sona taşı
                val restrictedStreams = allStreams.filter { it.contains("ce=3") }
                if (restrictedStreams.isNotEmpty()) {
                    allStreams.removeAll(restrictedStreams)
                    allStreams.addAll(restrictedStreams)
                }
            }

            // IP adresli akışları (kullanıcının tanımladığı doğrudan IP yayınlarını) en başa al
            val ipStreams = allStreams.filter { StreamHealthManager.isIpStream(it) }
            if (ipStreams.isNotEmpty()) {
                allStreams.removeAll(ipStreams)
                allStreams.addAll(0, ipStreams)
            }

            // Gecikme ve başarı durumuna göre akışları sırala
            val sortedStreams = if (ctx != null) {
                StreamHealthManager.orderStreams(ctx, primary.id, allStreams)
            } else {
                allStreams
            }

            val catalogMatch = ChannelCatalog.findOfficialChannel(primary.name)
            val allGroups = (groupChannels.flatMap { it.groups } + (catalogMatch?.groups ?: emptyList())).distinct()
            val xmltv = groupChannels.mapNotNull { it.xmltvId }.firstOrNull { it.isNotBlank() } ?: catalogMatch?.xmltvId
            val logo = groupChannels.mapNotNull { it.logoUrl }.firstOrNull { it.isNotBlank() } ?: catalogMatch?.logoUrl
            val official = groupChannels.mapNotNull { it.officialLivePage }.firstOrNull { it.isNotBlank() } ?: catalogMatch?.officialLivePage
            val latency = if (ctx != null) StreamHealthManager.getChannelLatency(ctx, primary.id) else primary.lastStartupLatencyMs
            val freeAccess = catalogMatch?.isFreeAccess ?: primary.isFreeAccess
            val permStatus = catalogMatch?.permissionStatus ?: primary.permissionStatus
            val origin = catalogMatch?.sourceOrigin ?: primary.sourceOrigin ?: "M3U Yayın Kaynağı (Doğrudan)"
            val checkDate = catalogMatch?.lastCheckDate ?: primary.lastCheckDate

            val mergedChannel = primary.copy(
                streamUrl = sortedStreams.firstOrNull(),
                streamUrls = sortedStreams,
                xmltvId = xmltv,
                logoUrl = logo,
                officialLivePage = official,
                groups = allGroups,
                lastStartupLatencyMs = latency,
                isFreeAccess = freeAccess,
                permissionStatus = permStatus,
                sourceOrigin = origin,
                lastCheckDate = checkDate,
                streamStatus = if (sortedStreams.isNotEmpty()) VerificationStatus.USER_PROVIDED else VerificationStatus.PERMISSION_NOT_CONFIRMED
            )
            result.add(mergedChannel)
        }

        val ordered = ChannelOrder.apply(result)
        return if (ctx != null) ChannelCustomizationManager.applyCustomizations(ctx, ordered) else ordered
    }

    /**
     * Cihazda kayıtlı son geçerli M3U veya JSON önbelleğini yükler.
     */
    fun loadCached(ctx: Context): List<TvChannel>? {
        // 1. Önce zengin JSON önbelleğini dene (çoklu akış ve gecikme bilgileri korunur)
        val jsonFile = File(ctx.filesDir, CACHE_JSON_FILE)
        if (jsonFile.exists() && jsonFile.length() > 0) {
            try {
                val content = jsonFile.readText(StandardCharsets.UTF_8)
                val jsonArray = JSONArray(content)
                val channels = mutableListOf<TvChannel>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val streamUrls = mutableListOf<String>()
                    val streamsArr = obj.optJSONArray("streamUrls")
                    if (streamsArr != null) {
                        for (j in 0 until streamsArr.length()) {
                            streamUrls.add(streamsArr.getString(j))
                        }
                    }
                    val groups = mutableListOf<String>()
                    val groupsArr = obj.optJSONArray("groups")
                    if (groupsArr != null) {
                        for (j in 0 until groupsArr.length()) {
                            groups.add(groupsArr.getString(j))
                        }
                    }
                    val streamUrl = obj.optString("streamUrl").takeIf { it.isNotBlank() }
                    if (streamUrl != null && !streamUrls.contains(streamUrl)) {
                        streamUrls.add(0, streamUrl)
                    }

                    val chNum = obj.optInt("number", 0)
                    val chId = obj.optString("id", "")
                    if (chNum == 4 || chId == "startv" || obj.optString("name").contains("Star", ignoreCase = true)) {
                        val token = getDogusToken()
                        val dogusFeeds = listOf(
                            "https://dygvideo.dygdigital.com/live/hls/staravrupa?token=$token",
                            "https://dygvideo.dygdigital.com/live/hls/startv?token=$token",
                            "https://dogus.daioncdn.net/startv/startv.m3u8?app=startv_web"
                        )
                        for (f in dogusFeeds.reversed()) {
                            streamUrls.remove(f)
                            streamUrls.add(0, f)
                        }
                        val restricted = streamUrls.filter { it.contains("ce=3") }
                        if (restricted.isNotEmpty()) {
                            streamUrls.removeAll(restricted)
                            streamUrls.addAll(restricted)
                        }
                    }

                    val latency = if (obj.has("latency")) obj.getLong("latency") else null

                    val rawName = obj.getString("name")
                    val cleanedName = cleanChannelName(rawName)
                    val officialLivePage = obj.optString("officialLivePage").takeIf { it.isNotBlank() }
                    val isFreeAccess = if (obj.has("isFreeAccess")) obj.getBoolean("isFreeAccess") else true
                    val permissionStatus = obj.optString("permissionStatus").takeIf { it.isNotBlank() } ?: "Resmî Yayın / FTA"
                    val sourceOrigin = obj.optString("sourceOrigin").takeIf { it.isNotBlank() }
                    val lastCheckDate = obj.optString("lastCheckDate").takeIf { it.isNotBlank() } ?: "2026-10-30"

                    channels.add(
                        TvChannel(
                            number = obj.getInt("number"),
                            id = obj.getString("id"),
                            name = cleanedName,
                            logoUrl = obj.optString("logoUrl").takeIf { it.isNotBlank() },
                            streamUrl = streamUrls.firstOrNull() ?: streamUrl,
                            streamUrls = streamUrls,
                            officialLivePage = officialLivePage,
                            xmltvId = obj.optString("xmltvId").takeIf { it.isNotBlank() },
                            logoStatus = if (obj.optString("logoUrl").isNotBlank()) VerificationStatus.USER_PROVIDED else VerificationStatus.NOT_VERIFIED,
                            streamStatus = VerificationStatus.USER_PROVIDED,
                            epgStatus = if (obj.optString("xmltvId").isNotBlank()) VerificationStatus.USER_PROVIDED else VerificationStatus.NOT_VERIFIED,
                            notes = obj.optString("notes").takeIf { it.isNotBlank() },
                            groups = groups,
                            lastStartupLatencyMs = latency,
                            isFreeAccess = isFreeAccess,
                            permissionStatus = permissionStatus,
                            sourceOrigin = sourceOrigin,
                            lastCheckDate = lastCheckDate
                        )
                    )
                }
                if (channels.isNotEmpty()) {
                    return ChannelCustomizationManager.applyCustomizations(ctx, channels)
                }
            } catch (e: Exception) {
                Log.w(TAG, "JSON önbellek okunamadı: ${e.message}")
            }
        }

        // 2. Yedek olarak M3U önbelleğini dene
        return try {
            val f = File(ctx.filesDir, CACHE_FILE).let {
                if (it.exists() && it.length() > 0) it else File(ctx.filesDir, LEGACY_CACHE_FILE)
            }
            if (f.exists() && f.length() > 0) {
                val content = f.readText(StandardCharsets.UTF_8)
                val parsed = parse(content).takeIf { it.isNotEmpty() }
                if (parsed != null) ChannelCustomizationManager.applyCustomizations(ctx, parsed) else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "M3U önbellek okunamadı: ${e.message}")
            null
        }
    }

    /**
     * Birleştirilmiş kanal listesini JSON ve M3U formatında diske kaydeder.
     */
    fun saveMergedCache(ctx: Context, channels: List<TvChannel>) {
        try {
            val jsonArray = JSONArray()
            val m3uBuilder = StringBuilder("#EXTM3U\n")

            for (ch in channels) {
                val obj = JSONObject().apply {
                    put("number", ch.number)
                    put("id", ch.id)
                    put("name", ch.name)
                    put("logoUrl", ch.logoUrl ?: "")
                    put("streamUrl", ch.streamUrl ?: "")
                    put("officialLivePage", ch.officialLivePage ?: "")
                    put("xmltvId", ch.xmltvId ?: "")
                    put("notes", ch.notes ?: "")
                    put("isFreeAccess", ch.isFreeAccess)
                    put("permissionStatus", ch.permissionStatus)
                    put("sourceOrigin", ch.sourceOrigin ?: "")
                    put("lastCheckDate", ch.lastCheckDate)
                    if (ch.lastStartupLatencyMs != null) put("latency", ch.lastStartupLatencyMs)

                    val streamsArr = JSONArray()
                    ch.streamUrls.forEach { streamsArr.put(it) }
                    put("streamUrls", streamsArr)

                    val groupsArr = JSONArray()
                    ch.groups.forEach { groupsArr.put(it) }
                    put("groups", groupsArr)
                }
                jsonArray.put(obj)

                val tvgId = if (!ch.xmltvId.isNullOrBlank()) " tvg-id=\"${ch.xmltvId}\"" else ""
                val tvgLogo = if (!ch.logoUrl.isNullOrBlank()) " tvg-logo=\"${ch.logoUrl}\"" else ""
                val groupTitle = if (ch.groups.isNotEmpty()) " group-title=\"${ch.groups.joinToString(";")}\"" else ""
                m3uBuilder.append("#EXTINF:-1$tvgId$tvgLogo$groupTitle,${ch.name}\n")
                m3uBuilder.append("${ch.streamUrl ?: ""}\n")
            }

            File(ctx.filesDir, CACHE_JSON_FILE).writeText(jsonArray.toString(), StandardCharsets.UTF_8)
            File(ctx.filesDir, CACHE_FILE).writeText(m3uBuilder.toString(), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Önbellek diske kaydedilemedi: ${e.message}")
        }
    }

    /**
     * Verilen URL'den (M3U veya Göktürk IPTV gibi web sayfası) kanalları indirir ve ayrıştırır.
     * Web sayfası ise içerisindeki M3U çalma listesi bağlantısını veya doğrudan yayın etiketlerini bulur.
     */
    fun fetchFromUrlOrWebPage(urlStr: String, defaultOrigin: String = "M3U Yayını"): List<TvChannel>? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 10000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml,text/plain,*/*")
            }
            if (conn.responseCode in 200..299) {
                val content = conn.inputStream.use { input ->
                    InputStreamReader(input, StandardCharsets.UTF_8).buffered().use { it.readText() }
                }
                conn.disconnect()

                if (content.isBlank()) return null

                // 1. Doğrudan M3U formatıysa
                if (content.contains("#EXTINF") || content.startsWith("#EXTM3U")) {
                    return parse(content).map {
                        it.copy(
                            sourceOrigin = defaultOrigin,
                            notes = it.notes ?: "$defaultOrigin Türkiye"
                        )
                    }
                }

                // 2. HTML sayfası ise: Sayfa içerisindeki M3U bağlantısını tespit et
                val m3uLinkRegex = Regex("""href=["'](https?://[^"']+\.m3u8?)["']""", RegexOption.IGNORE_CASE)
                val m3uMatch = m3uLinkRegex.find(content)
                if (m3uMatch != null) {
                    val m3uUrl = m3uMatch.groupValues[1]
                    Log.i(TAG, "$defaultOrigin sayfasından M3U bağlantısı tespit edildi: $m3uUrl")
                    val parsed = fetchFromUrlOrWebPage(m3uUrl, defaultOrigin)
                    if (!parsed.isNullOrEmpty()) {
                        return parsed.map {
                            it.copy(
                                sourceOrigin = defaultOrigin,
                                notes = "$defaultOrigin / " + (it.notes ?: "Türkiye")
                            )
                        }
                    }
                }

                // 3. HTML içindeki doğrudan yayın akışlarını regex ile topla
                val extracted = extractChannelsFromHtml(content, defaultOrigin)
                if (extracted.isNotEmpty()) {
                    return extracted
                }
            } else {
                conn.disconnect()
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Web / M3U ayrıştırma hatası ($urlStr): ${e.message}")
            null
        }
    }

    /**
     * Web sayfasındaki doğrudan video/akış bağlantılarını ayıklar.
     */
    fun extractChannelsFromHtml(html: String, defaultOrigin: String = "Göktürk IPTV"): List<TvChannel> {
        val results = mutableListOf<TvChannel>()
        val streamRegex = Regex("""(?i)(?:href|data-src|data-url|src)=["'](https?://[^"']+\.m3u8?[^"']*)["'][^>]*>([^<]+)<""")
        for (match in streamRegex.findAll(html)) {
            val streamUrl = match.groupValues[1].trim()
            val name = cleanChannelName(match.groupValues[2].trim())
            if (name.isNotBlank() && streamUrl.isNotBlank()) {
                val catalogMatch = ChannelCatalog.findOfficialChannel(name)
                results.add(
                    TvChannel(
                        number = results.size + 1,
                        id = "ch_${Math.abs(streamUrl.hashCode().toLong())}",
                        name = name,
                        streamUrl = streamUrl,
                        streamUrls = listOf(streamUrl),
                        officialLivePage = catalogMatch?.officialLivePage,
                        xmltvId = catalogMatch?.xmltvId,
                        streamStatus = VerificationStatus.USER_PROVIDED,
                        notes = "$defaultOrigin Türkiye",
                        sourceOrigin = defaultOrigin,
                        groups = catalogMatch?.groups ?: listOf("Ulusal")
                    )
                )
            }
        }
        return results
    }

    /**
     * Belirtilen listeleri ve kullanıcının özel IP listelerini indirir, hepsini birleştirir,
     * çoklu akış adreslerini gruplar ve önbelleğe yazar.
     */
    fun downloadAndMergeAll(ctx: Context): List<TvChannel>? {
        val downloadedLists = mutableListOf<List<TvChannel>>()

        // 1. Önce kullanıcının kendi verdiği özel IPTV / IP listelerini indir ve en başa al
        try {
            val customChannels = CustomPlaylistManager.fetchCustomChannels(ctx)
            if (customChannels.isNotEmpty()) {
                downloadedLists.add(customChannels)
                Log.i(TAG, "Kullanıcı özel listesi en öncelikli kaynak olarak dahil edildi: ${customChannels.size} kanal")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kullanıcı özel listesi okunamadı: ${e.message}")
        }

        // 2. ANA YAYIN: Göktürk IPTV Türkiye Kanal Listesi (gokturkiptv.com/kanallar/turkiye)
        try {
            val gokturkChannels = fetchFromUrlOrWebPage(ChannelCatalog.GOKTURK_PLAYLIST_URL, "Göktürk IPTV")
            if (!gokturkChannels.isNullOrEmpty()) {
                downloadedLists.add(gokturkChannels)
                Log.i(TAG, "Göktürk IPTV ana yayını başarıyla yüklendi: ${gokturkChannels.size} kanal")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Göktürk IPTV ana yayını yüklenirken hata: ${e.message}")
        }

        // 3. DOĞRUDAN TÜRKİYE M3U: iptv-org Türkiye (https://iptv-org.github.io/iptv/countries/tr.m3u)
        try {
            val iptvOrgChannels = fetchFromUrlOrWebPage(ChannelCatalog.IPTV_ORG_TR_URL, "iptv-org (TR)")
            if (!iptvOrgChannels.isNullOrEmpty()) {
                downloadedLists.add(iptvOrgChannels)
                Log.i(TAG, "iptv-org Türkiye M3U yayını başarıyla yüklendi: ${iptvOrgChannels.size} kanal")
            }
        } catch (e: Exception) {
            Log.w(TAG, "iptv-org Türkiye yayını yüklenirken hata: ${e.message}")
        }

        for (urlStr in ChannelCatalog.PLAYLIST_URLS) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 10000
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
                            Log.i(TAG, "Liste başarıyla indirildi: $urlStr (${parsed.size} kanal)")
                            downloadedLists.add(parsed)
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

        if (downloadedLists.isEmpty()) return null

        val merged = mergeChannelLists(downloadedLists, ctx)
        if (merged.isNotEmpty()) {
            saveMergedCache(ctx, merged)
            return merged
        }

        return null
    }

    /**
     * Geriye dönük uyumluluk için download() metodu downloadAndMergeAll()'a yönlendirilir.
     */
    fun download(ctx: Context): List<TvChannel>? = downloadAndMergeAll(ctx)
}
