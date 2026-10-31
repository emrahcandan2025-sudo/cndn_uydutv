package com.uydutv.data

import android.content.Context
import android.util.Log

/**
 * Kanal ve yayın akışlarının açılış sürelerini (gecikmelerini) ve sağlık durumlarını
 * SharedPreferences üzerinde saklar. En hızlı ve çalışan yayınların önceliklendirilmesini sağlar.
 */
object StreamHealthManager {
    private const val TAG = "StreamHealthManager"
    private const val PREFS_NAME = "cndn_stream_health"
    private const val KEY_PREFIX_LATENCY = "lat_"
    private const val KEY_PREFIX_FAILURES = "fail_"
    private const val KEY_PREFIX_LAST_SUCCESS = "succ_"

    private fun streamKey(streamUrl: String): String =
        Math.abs(streamUrl.hashCode().toLong()).toString()

    /**
     * Yayın akışının başarıyla açıldığını ve ölçülen süreyi kaydeder.
     */
    fun recordSuccess(context: Context, channelId: String, streamUrl: String, latencyMs: Long) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = streamKey(streamUrl)
            prefs.edit()
                .putLong(KEY_PREFIX_LATENCY + key, latencyMs)
                .putLong(KEY_PREFIX_LAST_SUCCESS + key, System.currentTimeMillis())
                .putInt(KEY_PREFIX_FAILURES + key, 0)
                .putLong("chan_lat_$channelId", latencyMs)
                .putString("chan_fastest_$channelId", streamUrl)
                .apply()
            Log.d(TAG, "Yayın başarıyla açıldı: $channelId (${latencyMs}ms)")
        } catch (e: Exception) {
            Log.w(TAG, "Hata (recordSuccess): ${e.message}")
        }
    }

    /**
     * Yayın akışı açılamadığında hata sayısını artırır.
     */
    fun recordFailure(context: Context, channelId: String, streamUrl: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = streamKey(streamUrl)
            val currentFails = prefs.getInt(KEY_PREFIX_FAILURES + key, 0)
            prefs.edit()
                .putInt(KEY_PREFIX_FAILURES + key, currentFails + 1)
                .apply()
            Log.d(TAG, "Yayın başarısız oldu: $channelId (hata sayısı: ${currentFails + 1})")
        } catch (e: Exception) {
            Log.w(TAG, "Hata (recordFailure): ${e.message}")
        }
    }

    /**
     * Kanal için kaydedilen son açılış süresini döndürür.
     */
    fun getChannelLatency(context: Context, channelId: String): Long? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lat = prefs.getLong("chan_lat_$channelId", -1L)
            if (lat >= 0) lat else null
        } catch (e: Exception) {
            null
        }
    }

    private val ipRegex = Regex("""https?://\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?(/.*)?""", RegexOption.IGNORE_CASE)

    /**
     * Verilen yayın URL'sinin doğrudan bir IP adresi (kullanıcı IP'si / yerel yayın sunucusu)
     * içerip içermediğini denetler.
     */
    fun isIpStream(url: String): Boolean = ipRegex.matches(url) || ipRegex.containsMatchIn(url)

    /**
     * Aday yayın adreslerini sağlık ve gecikme metriklerine göre sıralar.
     * Kullanıcının sağladığı doğrudan IP adresleri en yüksek öncelikle en başa alınır.
     * Ardından en az hata alan ve en düşük gecikmeli çalışan adresler gelir.
     */
    fun orderStreams(context: Context, channelId: String, candidateUrls: List<String>): List<String> {
        if (candidateUrls.size <= 1) return candidateUrls
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            candidateUrls.sortedWith { urlA, urlB ->
                val keyA = streamKey(urlA)
                val keyB = streamKey(urlB)

                val failsA = prefs.getInt(KEY_PREFIX_FAILURES + keyA, 0)
                val failsB = prefs.getInt(KEY_PREFIX_FAILURES + keyB, 0)

                // 1. Kullanıcının doğrudan IP adresli akışları en öne (kronik hata almadıkça)
                val isIpA = isIpStream(urlA)
                val isIpB = isIpStream(urlB)
                if (isIpA != isIpB) {
                    if (isIpA && failsA < 2) return@sortedWith -1
                    if (isIpB && failsB < 2) return@sortedWith 1
                }

                // 2. Az hata alanlar öne
                if (failsA != failsB) {
                    failsA.compareTo(failsB)
                } else {
                    // 3. Düşük gecikmeli olanlar öne (kayıt yoksa Long.MAX_VALUE)
                    val latA = prefs.getLong(KEY_PREFIX_LATENCY + keyA, Long.MAX_VALUE)
                    val latB = prefs.getLong(KEY_PREFIX_LATENCY + keyB, Long.MAX_VALUE)
                    latA.compareTo(latB)
                }
            }
        } catch (e: Exception) {
            candidateUrls
        }
    }
}
