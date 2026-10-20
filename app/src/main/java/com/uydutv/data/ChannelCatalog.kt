package com.uydutv.data

/**
 * UyduTV fixed channel catalog.
 *
 * IMPORTANT:
 * - officialLivePage is a broadcaster webpage, NOT a direct stream URL.
 * - streamUrl must remain null until the broadcaster/authorized distributor permits
 *   use in this app and the HLS/DASH endpoint has been technically verified.
 * - logoUrl and xmltvId remain null until verified from an authorized/actual source.
 * - Channel numbers are local UyduTV ordering, not official broadcaster numbers.
 */
enum class VerificationStatus {
    VERIFIED,
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
    val channels: List<TvChannel> = listOf(
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

    fun channelByNumber(number: Int): TvChannel? = channels.firstOrNull { it.number == number }
    fun channelById(id: String): TvChannel? = channels.firstOrNull { it.id == id }

    /** Returns a playable URL only after stream authorization/verification is completed. */
    fun playableStream(channel: TvChannel): String? =
        channel.streamUrl.takeIf { channel.streamStatus == VerificationStatus.VERIFIED }
}
