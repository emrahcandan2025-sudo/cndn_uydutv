package com.uydutv.data

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
    val notes: String? = null
)

object ChannelCatalog {
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

    @Volatile var channels: List<TvChannel> = builtIn

    /** Kullanıcının seçtiği M3U oynatma listesi. */
    const val PLAYLIST_URL: String = "https://iptv-org.github.io/iptv/countries/tr.m3u"

    fun channelByNumber(number: Int): TvChannel? = channels.firstOrNull { it.number == number }
    fun channelById(id: String): TvChannel? = channels.firstOrNull { it.id == id }

    /** Yalnızca doğrulanmış (VERIFIED) veya kullanıcının kendi listesinden gelen (USER_PROVIDED) akışlar oynatılır. */
    fun playableStream(channel: TvChannel): String? =
        channel.streamUrl?.takeIf {
            it.isNotBlank() && (channel.streamStatus == VerificationStatus.VERIFIED ||
                channel.streamStatus == VerificationStatus.USER_PROVIDED)
        }
}
