package com.uydutv.data

import java.util.Locale

/**
 * CNDN TV fixed channel catalog.
 *
 * IMPORTANT:
 * - officialLivePage is a broadcaster webpage, NOT a direct stream URL.
 * - streamUrl must remain null until the broadcaster/authorized distributor permits
 *   use in this app and the HLS/DASH endpoint has been technically verified.
 * - logoUrl and xmltvId remain null until verified from an authorized/actual source.
 * - Channel numbers are local CNDN TV ordering, not official broadcaster numbers.
 */
enum class VerificationStatus {
    VERIFIED,
    /** Kullanıcının kendi seçtiği oynatma listesinden gelir; yayıncı izni/doğrulaması uygulama tarafından garanti edilmez. */
    USER_PROVIDED,
    NOT_VERIFIED,
    PERMISSION_NOT_CONFIRMED,
    NOT_APPLICABLE
}

data class TvChannel(
    val number: Int,
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val streamUrl: String? = null,
    val officialLivePage: String? = null,
    val xmltvId: String? = null,
    val logoStatus: VerificationStatus = VerificationStatus.NOT_VERIFIED,
    val streamStatus: VerificationStatus = VerificationStatus.PERMISSION_NOT_CONFIRMED,
    val epgStatus: VerificationStatus = VerificationStatus.NOT_VERIFIED,
    val notes: String? = null,
    /** M3U group-title değerleri (ör. News, Sports). Kategori menüsü bunlarla çalışır. */
    val groups: List<String> = emptyList()
)

object ChannelCatalog {
    /** Primary M3U playlist source specified for CNDN TV. */
    const val PRIMARY_PLAYLIST_URL: String = "https://streams.uzunmuhalefet.com/lists/tr.m3u"

    /** Candidate XMLTV feed. Feed availability does not establish redistribution rights. */
    const val XMLTV_SOURCE_URL: String = "https://iptv-epg.org/files/epg-tr.xml"

    /**
     * These are candidate channels with official live-page references.
     * Direct stream URLs, logos and XMLTV IDs are intentionally null until verified.
     */
    /** Varsayılan (yerleşik) liste. Oynatma listesi yüklenince `channels` onunla değiştirilir. */
    private val builtInRaw: List<TvChannel> = listOf(
        TvChannel(1, "trt1", "TRT 1", officialLivePage = "https://www.trtizle.com/",
            notes = "Resmî TRT İzle giriş noktası; doğrudan akış ve uygulamada kullanım izni doğrulanmadı."),
        TvChannel(2, "trthaber", "TRT Haber", officialLivePage = "https://www.trtizle.com/",
            notes = "Resmî platform; kanala özgü doğrudan akış ve EPG ID doğrulanmadı."),
        TvChannel(3, "trtspor", "TRT Spor", officialLivePage = "https://www.trtizle.com/",
            notes = "Resmî platform; kanala özgü doğrudan akış ve EPG ID doğrulanmadı."),
        TvChannel(4, "atv", "atv", officialLivePage = "https://www.atv.com.tr/canli-yayin",
            notes = "Resmî canlı yayın sayfası; doğrudan akış ve uygulamada kullanım izni doğrulanmadı."),
        TvChannel(5, "kanald", "Kanal D", officialLivePage = "https://www.kanald.com.tr/canli-yayin",
            notes = "Resmî canlı yayın sayfası; doğrudan akış ve uygulamada kullanım izni doğrulanmadı."),
        TvChannel(6, "showtv", "Show TV", officialLivePage = "https://www.showtv.com.tr/canli-yayin",
            notes = "Resmî canlı yayın sayfası; doğrudan akış ve uygulamada kullanım izni doğrulanmadı."),
        TvChannel(7, "startv", "Star TV", officialLivePage = "https://www.startv.com.tr/canli-yayin",
            notes = "Resmî canlı yayın sayfası; doğrudan akış ve uygulamada kullanım izni doğrulanmadı."),
        TvChannel(8, "now", "NOW", officialLivePage = "https://www.nowtv.com.tr/canli-yayin",
            notes = "Resmî canlı yayın sayfası; doğrudan akış ve uygulamada kullanım izni doğrulanmadı."),
        TvChannel(9, "tv8", "TV8", officialLivePage = "https://www.tv8.com.tr/canli-yayin",
            notes = "Resmî canlı yayın sayfası; doğrudan akış ve uygulamada kullanım izni doğrulanmadı."),
        TvChannel(10, "kanal7", "Kanal 7", officialLivePage = "https://www.kanal7.com/canli-izle",
            notes = "Resmî canlı yayın sayfası; doğrudan akış ve uygulamada kullanım izni doğrulanmadı."),
        TvChannel(11, "haberturk", "Habertürk TV", officialLivePage = "https://www.haberturk.com/canliyayin",
            notes = "Resmî canlı yayın sayfası; doğrudan akış ve uygulamada kullanım izni doğrulanmadı.")
    )

    val builtIn: List<TvChannel> = ChannelOrder.apply(builtInRaw)

    /** Tüm kanallar. */
    @Volatile var all: List<TvChannel> = builtIn
        private set

    /** Seçili kategori (null = tüm kanallar). */
    @Volatile var category: String? = null
        private set

    /** Şu an gezilen (kategoriye göre süzülmüş) kanal listesi. */
    @Volatile var channels: List<TvChannel> = builtIn
        private set

    /** Oynatma listesi kaynakları: Birincil liste ve yedek kaynaklar */
    val PLAYLIST_URLS: List<String> = listOf(
        PRIMARY_PLAYLIST_URL,
        "https://iptv-org.github.io/iptv/countries/tr.m3u",
        "https://iptv-org.github.io/iptv/languages/tur.m3u"
    )

    fun setAll(list: List<TvChannel>) {
        all = if (list.isEmpty()) builtIn else list
        val c = category
        if (c != null && all.none { c in it.groups }) category = null
        recompute()
    }

    fun setCategory(c: String?) { category = c; recompute() }

    private fun recompute() {
        val c = category
        channels = if (c == null) all else all.filter { c in it.groups }.ifEmpty { all }
    }

    private val categoryOrder = listOf(
        "General", "News", "Sports", "Movies", "Series", "Entertainment", "Kids", "Music", "Documentary",
        "Religious", "Culture", "Education", "Lifestyle", "Comedy", "Animation", "Family", "Business",
        "Cooking", "Travel", "Weather", "Science", "Outdoor", "Classic", "Shop", "Relax", "Legislative",
        "Ulusal", "Haber", "Spor", "Sinema", "Dizi", "Belgesel", "Müzik", "Çocuk", "Dini", "Yerel"
    )

    /** Menüdeki kategoriler: önce null (Tümü), sonra listede gerçekten kanalı olanlar. */
    fun categories(): List<String?> {
        val present = all.flatMap { it.groups }.toSet()
        val known = categoryOrder.filter { it in present }
        val rest = present.filter { it !in categoryOrder && it != "Undefined" }.sorted()
        return listOf<String?>(null) + known + rest
    }

    fun categoryLabel(key: String?): String = when (key) {
        null -> "Tüm Kanallar"
        "General", "Ulusal" -> "Genel"; "News", "Haber" -> "Haber"; "Sports", "Spor" -> "Spor"
        "Movies", "Sinema" -> "Film"; "Series", "Dizi" -> "Dizi"; "Entertainment", "Eğlence" -> "Eğlence"
        "Kids", "Çocuk" -> "Çocuk"; "Music", "Müzik" -> "Müzik"; "Documentary", "Belgesel" -> "Belgesel"
        "Religious", "Dini" -> "Dini"; "Culture", "Kültür" -> "Kültür"; "Education", "Eğitim" -> "Eğitim"
        "Lifestyle", "Yaşam" -> "Yaşam"; "Comedy", "Komedi" -> "Komedi"; "Animation", "Animasyon" -> "Animasyon"
        "Family", "Aile" -> "Aile"; "Business", "Ekonomi" -> "İş / Ekonomi"; "Cooking", "Mutfak" -> "Mutfak"
        "Travel", "Seyahat" -> "Seyahat"; "Weather", "Hava" -> "Hava Durumu"; "Science", "Bilim" -> "Bilim"
        "Outdoor", "Doğa" -> "Doğa"; "Classic", "Klasik" -> "Klasik"; "Shop", "Alışveriş" -> "Alışveriş"
        "Relax", "Dinlenme" -> "Dinlenme"; "Legislative", "Meclis" -> "Meclis"; "Yerel" -> "Yerel Kanallar"
        "Undefined" -> "Diğer"
        else -> key ?: "Diğer"
    }

    fun channelByNumber(number: Int): TvChannel? = channels.firstOrNull { it.number == number }
    fun channelById(id: String): TvChannel? = channels.firstOrNull { it.id == id }

    /** Yalnızca doğrulanmış (VERIFIED) veya kullanıcının kendi listesinden gelen (USER_PROVIDED) akışlar oynatılır. */
    fun playableStream(channel: TvChannel): String? {
        val url = channel.streamUrl?.trim() ?: return null
        if (url.isBlank()) return null
        // M3U dosyasının kendisini doğrudan video oynatıcısına verme
        if (url.endsWith(".m3u", ignoreCase = true) && !url.endsWith(".m3u8", ignoreCase = true)) {
            return null
        }
        val lower = url.lowercase(Locale.ROOT)
        if (!lower.startsWith("http://") && !lower.startsWith("https://") && !lower.startsWith("rtmp://") && !lower.startsWith("rtsp://")) {
            return null
        }
        return if (channel.streamStatus == VerificationStatus.VERIFIED ||
            channel.streamStatus == VerificationStatus.USER_PROVIDED) {
            url
        } else null
    }
}
