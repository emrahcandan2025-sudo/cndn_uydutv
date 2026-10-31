package com.uydutv.data

import android.content.Context
import android.util.Log
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONArray

/**
 * Harici M3U / Kullanıcı Özel IPTV Listesi Yöneticisi:
 * Kullanıcının eklediği M3U bağlantılarını SharedPreferences'ta saklar,
 * arka planda indirip ayrıştırır ve CNDN TV kanal kataloğuna güvenle dahil eder.
 */
object CustomPlaylistManager {
    private const val TAG = "CustomPlaylistManager"
    private const val PREFS_NAME = "cndn_custom_playlists"
    private const val KEY_URLS = "user_m3u_urls"

    fun getCustomUrls(context: Context): List<String> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_URLS, null) ?: "[]"
            val arr = JSONArray(json)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val u = arr.getString(i).trim()
                if (u.isNotEmpty()) list.add(u)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomUrl(context: Context, url: String) {
        val current = getCustomUrls(context).toMutableList()
        val cleanUrl = url.trim()
        if (cleanUrl.isNotEmpty() && !current.contains(cleanUrl)) {
            current.add(cleanUrl)
            saveAllUrls(context, current)
        }
    }

    fun removeCustomUrl(context: Context, url: String) {
        val current = getCustomUrls(context).toMutableList()
        current.removeAll { it.trim().equals(url.trim(), ignoreCase = true) }
        saveAllUrls(context, current)
    }

    fun clearAllUrls(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_URLS)
            .apply()
    }

    private fun saveAllUrls(context: Context, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URLS, arr.toString())
            .apply()
    }

    /**
     * Kullanıcının kayıtlı tüm harici M3U listelerini indirir, ayrıştırır ve döndürür.
     */
    fun fetchCustomChannels(context: Context): List<TvChannel> {
        val urls = getCustomUrls(context)
        if (urls.isEmpty()) return emptyList()

        val results = mutableListOf<TvChannel>()
        var customStartNumber = 800

        for (urlStr in urls) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 8000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "CNDNTV/1.0 CustomPlaylist")
                }
                val code = conn.responseCode
                if (code in 200..299) {
                    val content = conn.inputStream.use { input ->
                        InputStreamReader(input, StandardCharsets.UTF_8).buffered().use { it.readText() }
                    }
                    conn.disconnect()

                    if (content.isNotBlank()) {
                        val parsed = PlaylistLoader.parse(content)
                        for (rawCh in parsed) {
                            val stream = rawCh.streamUrl ?: rawCh.streamUrls.firstOrNull() ?: continue
                            val userChannel = rawCh.copy(
                                number = customStartNumber++,
                                streamStatus = VerificationStatus.USER_PROVIDED,
                                permissionStatus = "Kullanıcı Özel M3U",
                                groups = (rawCh.groups + "Özel Liste").distinct(),
                                notes = "Harici M3U kaynağından eklendi"
                            )
                            results.add(userChannel)
                        }
                    }
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Harici liste indirilemedi ($urlStr): ${e.message}")
            }
        }
        return results
    }
}
