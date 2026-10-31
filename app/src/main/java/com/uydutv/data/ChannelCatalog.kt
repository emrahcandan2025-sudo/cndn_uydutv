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
    val streamUrls: List<String> = if (streamUrl != null) listOf(streamUrl) else emptyList(),
    val officialLivePage: String? = null,
    val xmltvId: String? = null,
    val logoStatus: VerificationStatus = VerificationStatus.NOT_VERIFIED,
    val streamStatus: VerificationStatus = VerificationStatus.PERMISSION_NOT_CONFIRMED,
    val epgStatus: VerificationStatus = VerificationStatus.NOT_VERIFIED,
    val notes: String? = null,
    /** M3U group-title değerleri (ör. News, Sports). Kategori menüsü bunlarla çalışır. */
    val groups: List<String> = emptyList(),
    /** Son ölçülen başarılı açılış süresi (milisaniye) */
    val lastStartupLatencyMs: Long? = null,
    /** Ücretsiz ve şifresiz (FTA) erişim durumu */
    val isFreeAccess: Boolean = true,
    /** Kullanım ve yayın izni durumu açıklaması */
    val permissionStatus: String = "Resmî Yayın / FTA",
    /** Doğrudan yayın bağlantısının kaynak kökeni */
    val sourceOrigin: String? = null,
    /** Son kontrol ve doğrulama tarihi */
    val lastCheckDate: String = "2026-10-30"
)

object ChannelCatalog {
    /** Primary M3U playlist source specified for CNDN TV (iptv-org Türkiye). */
    const val PRIMARY_PLAYLIST_URL: String = "https://iptv-org.github.io/iptv/countries/tr.m3u"

    /** Candidate XMLTV feed. Feed availability does not establish redistribution rights. */
    const val XMLTV_SOURCE_URL: String = "https://iptv-epg.org/files/epg-tr.xml"

    /**
     * Resmî ve ücretsiz (FTA) Türkiye kanalları kataloğu.
     * Her kanal için resmî canlı yayın sayfası, kategori, ücretsiz erişim durumu,
     * kullanım izni durumu ve son kontrol tarihi ayrı ayrı tutulur.
     */
    private val builtInRaw: List<TvChannel> = listOf(
        // === ULUSAL KANALLAR ===
        TvChannel(1, "trt1", "TRT 1", officialLivePage = "https://www.trtizle.com/canli/tv/trt-1",
            groups = listOf("Ulusal", "Genel"), xmltvId = "trt1.tr",
            notes = "Resmî TRT İzle canlı yayın portalı; ücretsiz kamu yayını.",
            permissionStatus = "Kamu Yayıncısı (FTA)"),
        TvChannel(2, "atv", "atv", officialLivePage = "https://www.atv.com.tr/canli-yayin",
            groups = listOf("Ulusal", "Genel"), xmltvId = "atv.tr",
            notes = "Resmî Turkuvaz Medya canlı yayın sayfası.",
            permissionStatus = "Ulusal Karasal / Uydu FTA"),
        TvChannel(3, "kanald", "Kanal D", officialLivePage = "https://www.kanald.com.tr/canli-yayin",
            groups = listOf("Ulusal", "Genel"), xmltvId = "kanald.tr",
            notes = "Resmî Demirören Medya canlı yayın sayfası.",
            permissionStatus = "Ulusal Karasal / Uydu FTA"),
        TvChannel(4, "startv", "Star TV", officialLivePage = "https://www.startv.com.tr/canli-yayin",
            streamUrl = "https://dygvideo.dygdigital.com/live/hls/staravrupa?token=de13baf2a095e421aad499fa34d560d9205ec5b4b28a3371",
            streamUrls = listOf(
                "https://dygvideo.dygdigital.com/live/hls/staravrupa?token=de13baf2a095e421aad499fa34d560d9205ec5b4b28a3371",
                "https://dygvideo.dygdigital.com/live/hls/startv?token=de13baf2a095e421aad499fa34d560d9205ec5b4b28a3371",
                "https://dogus.daioncdn.net/startv/startv.m3u8?app=startv_web",
                "https://dogus.daioncdn.net/startv/startv_720p.m3u8?app=a20ac41e-bdc3-4aa1-934d-26b484480ac9&ce=3&sid=8l4w3lst4co5"
            ),
            streamStatus = VerificationStatus.VERIFIED,
            groups = listOf("Ulusal", "Genel"), xmltvId = "startv.tr",
            notes = "Resmî Doğuş Yayın Grubu canlı yayın sayfası ve alternatif akışları.",
            permissionStatus = "Ulusal Karasal / Uydu FTA"),
        TvChannel(5, "showtv", "Show TV", officialLivePage = "https://www.showtv.com.tr/canli-yayin",
            groups = listOf("Ulusal", "Genel"), xmltvId = "showtv.tr",
            notes = "Resmî Ciner Medya Grubu canlı yayın sayfası.",
            permissionStatus = "Ulusal Karasal / Uydu FTA"),
        TvChannel(6, "beyaztv", "Beyaz TV", officialLivePage = "https://beyaztv.com.tr/canli-yayin/",
            groups = listOf("Ulusal", "Genel"), xmltvId = "beyaztv.tr",
            notes = "Resmî Beyaz TV canlı yayın portalı.",
            permissionStatus = "Ulusal Uydu FTA"),
        TvChannel(7, "kanal7", "Kanal 7", officialLivePage = "https://www.kanal7.com/canli-izle",
            groups = listOf("Ulusal", "Genel"), xmltvId = "kanal7.tr",
            notes = "Resmî Kanal 7 Medya Grubu canlı yayın sayfası.",
            permissionStatus = "Ulusal Uydu FTA"),
        TvChannel(8, "tv8", "TV8", officialLivePage = "https://www.tv8.com.tr/canli-yayin",
            groups = listOf("Ulusal", "Genel"), xmltvId = "tv8.tr",
            notes = "Resmî Acun Medya canlı yayın sayfası.",
            permissionStatus = "Ulusal Karasal / Uydu FTA"),
        TvChannel(9, "now", "NOW", officialLivePage = "https://www.nowtv.com.tr/canli-yayin",
            groups = listOf("Ulusal", "Genel"), xmltvId = "now.tr",
            notes = "Resmî The Walt Disney Company Türkiye canlı yayın sayfası.",
            permissionStatus = "Ulusal Karasal / Uydu FTA"),
        TvChannel(10, "ulketv", "Ülke TV", officialLivePage = "https://www.ulketv.com.tr/canli-yayin",
            groups = listOf("Ulusal", "Haber"), xmltvId = "ulketv.tr",
            notes = "Resmî Ülke TV canlı yayın sayfası.",
            permissionStatus = "Ulusal Uydu FTA"),
        TvChannel(18, "teve2", "Teve2", officialLivePage = "https://www.teve2.com.tr/canli-yayin",
            groups = listOf("Ulusal", "Eğlence"), xmltvId = "teve2.tr",
            notes = "Resmî Demirören Medya eğlence kanalı canlı yayın sayfası.",
            permissionStatus = "Ulusal Uydu FTA"),
        TvChannel(30, "tv100", "TV 100", officialLivePage = "https://www.tv100.com/canli-yayin",
            groups = listOf("Ulusal", "Haber"), xmltvId = "tv100.tr",
            notes = "Resmî TV 100 canlı yayın sayfası.",
            permissionStatus = "Ulusal Uydu FTA"),

        // === HABER KANALLARI ===
        TvChannel(11, "trthaber", "TRT Haber", officialLivePage = "https://www.trthaber.com/canli-yayin-izle.html",
            groups = listOf("Haber"), xmltvId = "trthaber.tr",
            notes = "Resmî TRT Haber 24 saat kesintisiz kamu haber yayını.",
            permissionStatus = "Kamu Haber Yayıncısı (FTA)"),
        TvChannel(13, "ahaber", "A Haber", officialLivePage = "https://www.ahaber.com.tr/canli-yayin",
            groups = listOf("Haber"), xmltvId = "ahaber.tr",
            notes = "Resmî Turkuvaz Medya haber kanalı.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(15, "tvnet", "TVNET", officialLivePage = "https://www.tvnet.com.tr/canli-yayin",
            groups = listOf("Haber"), xmltvId = "tvnet.tr",
            notes = "Resmî Albayrak Medya haber kanalı.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(17, "haberturk", "Habertürk TV", officialLivePage = "https://www.haberturk.com/canliyayin",
            groups = listOf("Haber"), xmltvId = "haberturk.tr",
            notes = "Resmî Ciner Medya haber kanalı canlı sayfası.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(19, "sozcutv", "Sözcü TV", officialLivePage = "https://www.sozcu.com.tr/canli-tv",
            groups = listOf("Haber"), xmltvId = "sozcutv.tr",
            notes = "Resmî Sözcü Medya Grubu haber kanalı.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(20, "ntv", "NTV", officialLivePage = "https://www.ntv.com.tr/canli-yayin",
            groups = listOf("Haber"), xmltvId = "ntv.tr",
            notes = "Resmî Doğuş Yayın Grubu 24 saat haber kanalı.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(21, "cnnturk", "CNN Türk", officialLivePage = "https://www.cnnturk.com/canli-yayin",
            groups = listOf("Haber"), xmltvId = "cnnturk.tr",
            notes = "Resmî Demirören / Warner Bros Discovery ortak haber kanalı.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(22, "tele1", "Tele1", officialLivePage = "https://tele1.com.tr/canli-yayin",
            groups = listOf("Haber"), xmltvId = "tele1.tr",
            notes = "Resmî Tele1 Televizyonu canlı yayın sayfası.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(23, "tgrthaber", "TGRT Haber", officialLivePage = "https://www.tgrthaber.com.tr/canli",
            groups = listOf("Haber"), xmltvId = "tgrthaber.tr",
            notes = "Resmî İhlas Medya haber kanalı.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(24, "24tv", "24", officialLivePage = "https://www.yirmidort.tv/canli-yayin",
            groups = listOf("Haber"), xmltvId = "yirmidorttv.tr",
            notes = "Resmî TürkMedya haber kanalı.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(25, "haberglobal", "Haber Global", officialLivePage = "https://haberglobal.com.tr/canli-yayin",
            groups = listOf("Haber"), xmltvId = "haberglobal.tr",
            notes = "Resmî Global Medya haber kanalı.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(26, "bloomberght", "Bloomberg HT", officialLivePage = "https://www.bloomberght.com/canli-yayin",
            groups = listOf("Haber", "Business"), xmltvId = "bloomberght.tr",
            notes = "Resmî Ciner Medya ekonomi ve iş dünyası kanalı.",
            permissionStatus = "Ulusal Ekonomi FTA"),
        TvChannel(27, "ekoturk", "Ekotürk", officialLivePage = "https://www.ekoturk.com/canli-yayin",
            groups = listOf("Haber", "Business"), xmltvId = "ekoturk.tr",
            notes = "Resmî Ekotürk ekonomi ve finans televizyonu.",
            permissionStatus = "Ulusal Ekonomi FTA"),
        TvChannel(29, "benguturk", "Bengütürk", officialLivePage = "https://www.benguturk.com/canli-yayin",
            groups = listOf("Haber"), xmltvId = "benguturk.tr",
            notes = "Resmî Bengütürk TV haber kanalı.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(31, "halktv", "Halk TV", officialLivePage = "https://halktv.com.tr/canli-yayin",
            groups = listOf("Haber"), xmltvId = "halktv.tr",
            notes = "Resmî Halk TV haber kanalı.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(32, "flashhaber", "Flash Haber", officialLivePage = "https://flashhabertv.com.tr/canli-yayin",
            groups = listOf("Haber"), xmltvId = "flashhaber.tr",
            notes = "Resmî Flash Haber TV canlı yayını.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(33, "akittv", "Akit TV", officialLivePage = "https://www.akittv.com.tr/canli-yayin",
            groups = listOf("Haber"), xmltvId = "akittv.tr",
            notes = "Resmî Akit Medya televizyon kanalı.",
            permissionStatus = "Ulusal Haber FTA"),
        TvChannel(34, "krttv", "KRT TV", officialLivePage = "https://www.krttv.com.tr/canli-yayin",
            groups = listOf("Haber"), xmltvId = "krttv.tr",
            notes = "Resmî Kültür Radyo Televizyonu canlı sayfası.",
            permissionStatus = "Ulusal Haber FTA"),

        // === SPOR KANALLARI ===
        TvChannel(12, "trtspor", "TRT Spor", officialLivePage = "https://www.trtspor.com.tr/canli-yayin-izle/trt-spor",
            groups = listOf("Spor"), xmltvId = "trtspor.tr",
            notes = "Resmî TRT Spor kamu spor kanalı.",
            permissionStatus = "Kamu Spor Yayıncısı (FTA)"),
        TvChannel(14, "aspor", "A Spor", officialLivePage = "https://www.aspor.com.tr/canli-yayin",
            groups = listOf("Spor"), xmltvId = "aspor.tr",
            notes = "Resmî Turkuvaz Medya şifresiz spor kanalı.",
            permissionStatus = "Ulusal Spor FTA"),
        TvChannel(28, "trtsporyildiz", "TRT Spor Yıldız", officialLivePage = "https://www.trtspor.com.tr/canli-yayin-izle/trt-spor-yildiz",
            groups = listOf("Spor"), xmltvId = "trtsporyildiz.tr",
            notes = "Resmî TRT amatör ve olimpik sporlar kanalı.",
            permissionStatus = "Kamu Spor Yayıncısı (FTA)"),
        TvChannel(42, "fbtv", "FB TV", officialLivePage = "https://www.fenerbahce.org/fbtv/canli-yayin",
            groups = listOf("Spor"), xmltvId = "fbtv.tr",
            notes = "Resmî Fenerbahçe Spor Kulübü televizyon kanalı.",
            permissionStatus = "Kulüp Televizyonu FTA"),
        TvChannel(43, "sportstv", "Sports TV", officialLivePage = "https://www.sportstv.com.tr/canli-yayin",
            groups = listOf("Spor"), xmltvId = "sportstv.tr",
            notes = "Resmî Sports TV spor kanalı portalı.",
            permissionStatus = "Ulusal Spor FTA"),

        // === ÇOCUK KANALLARI ===
        TvChannel(16, "trtcocuk", "TRT Çocuk", officialLivePage = "https://www.trtcocuk.net.tr/canli-izle",
            groups = listOf("Çocuk"), xmltvId = "trtcocuk.tr",
            notes = "Resmî TRT Çocuk kamu çocuk televizyonu.",
            permissionStatus = "Kamu Çocuk Yayıncısı (FTA)"),
        TvChannel(38, "minikacocuk", "Minika Çocuk", officialLivePage = "https://www.minikacocuk.com.tr/canli-yayin",
            groups = listOf("Çocuk"), xmltvId = "minikacocuk.tr",
            notes = "Resmî Turkuvaz Medya okul öncesi çocuk kanalı.",
            permissionStatus = "Ulusal Çocuk FTA"),
        TvChannel(39, "minikago", "Minika GO", officialLivePage = "https://www.minikago.com.tr/canli-yayin",
            groups = listOf("Çocuk"), xmltvId = "minikago.tr",
            notes = "Resmî Turkuvaz Medya 7-14 yaş çocuk kanalı.",
            permissionStatus = "Ulusal Çocuk FTA"),

        // === BELGESEL & KÜLTÜR KANALLARI ===
        TvChannel(35, "trt2", "TRT 2", officialLivePage = "https://www.trtizle.com/canli/tv/trt-2",
            groups = listOf("Kültür", "Belgesel"), xmltvId = "trt2.tr",
            notes = "Resmî TRT Kültür Sanat kanalı.",
            permissionStatus = "Kamu Kültür Yayıncısı (FTA)"),
        TvChannel(36, "trtbelgesel", "TRT Belgesel", officialLivePage = "https://www.trtizle.com/canli/tv/trt-belgesel",
            groups = listOf("Belgesel"), xmltvId = "trtbelgesel.tr",
            notes = "Resmî TRT Belgesel kanalı portalı.",
            permissionStatus = "Kamu Belgesel Yayıncısı (FTA)"),
        TvChannel(37, "diyanettv", "Diyanet TV", officialLivePage = "https://www.diyanet.tv/canli-yayin",
            groups = listOf("Belgesel", "Religious"), xmltvId = "diyanettv.tr",
            notes = "Resmî Diyanet İşleri Başkanlığı ve TRT ortak televizyonu.",
            permissionStatus = "Kamu / Tematik FTA"),
        TvChannel(40, "tgrtbelgesel", "TGRT Belgesel", officialLivePage = "https://www.tgrtbelgesel.com.tr/canli",
            groups = listOf("Belgesel"), xmltvId = "tgrtbelgesel.tr",
            notes = "Resmî İhlas Medya belgesel kanalı.",
            permissionStatus = "Ulusal Belgesel FTA"),
        TvChannel(41, "trtmuzik", "TRT Müzik", officialLivePage = "https://www.trtizle.com/canli/tv/trt-muzik",
            groups = listOf("Music", "Kültür"), xmltvId = "trtmuzik.tr",
            notes = "Resmî TRT Müzik kanalı portalı.",
            permissionStatus = "Kamu Müzik Yayıncısı (FTA)"),

        // === YEREL KANALLAR ===
        TvChannel(44, "kadirgatv", "Kadırga TV", officialLivePage = "https://www.kadirgatv.com/canli-yayin",
            groups = listOf("Yerel"), xmltvId = "kadirgatv.tr",
            notes = "Trabzon ve Karadeniz bölgesel televizyon yayını.",
            permissionStatus = "Bölgesel / Yerel FTA"),
        TvChannel(45, "caytv", "Çay TV", officialLivePage = "https://www.caytv.com.tr/canli",
            groups = listOf("Yerel"), xmltvId = "caytv.tr",
            notes = "Rize ve Doğu Karadeniz bölgesel televizyon yayını.",
            permissionStatus = "Bölgesel / Yerel FTA"),
        TvChannel(46, "egetv", "Ege TV", officialLivePage = "https://egetv.com.tr/canli-yayin",
            groups = listOf("Yerel"), xmltvId = "egetv.tr",
            notes = "İzmir ve Ege bölgesi yerel televizyon yayını.",
            permissionStatus = "Bölgesel / Yerel FTA"),
        TvChannel(47, "kontv", "Kon TV", officialLivePage = "https://www.kontv.com.tr/canli",
            groups = listOf("Yerel"), xmltvId = "kontv.tr",
            notes = "Konya ve İç Anadolu bölgesel televizyon yayını.",
            permissionStatus = "Bölgesel / Yerel FTA"),
        TvChannel(48, "olaytv", "Olay TV", officialLivePage = "https://www.olay.com.tr/canli-yayin",
            groups = listOf("Yerel"), xmltvId = "olaytv.tr",
            notes = "Bursa ve Marmara bölgesel televizyon yayını.",
            permissionStatus = "Bölgesel / Yerel FTA"),
        TvChannel(49, "aksutv", "Aksu TV", officialLivePage = "https://aksutv.com.tr/canli-yayin",
            groups = listOf("Yerel"), xmltvId = "aksutv.tr",
            notes = "Kahramanmaraş ve Akdeniz bölgesi yerel televizyon yayını.",
            permissionStatus = "Bölgesel / Yerel FTA"),
        TvChannel(50, "kanalfirat", "Kanal Fırat", officialLivePage = "https://www.kanalfirat.com/canli-yayin",
            groups = listOf("Yerel"), xmltvId = "kanalfirat.tr",
            notes = "Elazığ ve Doğu Anadolu yerel televizyon yayını.",
            permissionStatus = "Bölgesel / Yerel FTA"),
        TvChannel(51, "eurostar", "Euro Star", officialLivePage = "https://www.eurostartv.com.tr/canli-izle",
            streamUrl = "https://dygvideo.dygdigital.com/live/hls/staravrupa?token=de13baf2a095e421aad499fa34d560d9205ec5b4b28a3371",
            streamUrls = listOf(
                "https://dygvideo.dygdigital.com/live/hls/staravrupa?token=de13baf2a095e421aad499fa34d560d9205ec5b4b28a3371"
            ),
            streamStatus = VerificationStatus.VERIFIED,
            groups = listOf("Ulusal", "Genel"), xmltvId = "startv.tr",
            notes = "Resmî Doğuş Yayın Grubu Euro Star TV (bölgesel kısıtlamasız yayın).",
            permissionStatus = "Ulusal Uydu / Web FTA"),

        // === CANLI RADYO KANALLARI ===
        TvChannel(201, "trtfm", "TRT FM", officialLivePage = "https://www.trtdinle.com",
            streamUrl = "https://trt.daioncdn.net/trtfm/trtfm.m3u8",
            streamStatus = VerificationStatus.VERIFIED,
            groups = listOf("Radyo"), notes = "TRT FM Canlı Radyo Yayını",
            permissionStatus = "Kamu Radyo Yayıncısı (FTA)"),
        TvChannel(202, "trtradyo1", "TRT Radyo 1", officialLivePage = "https://www.trtdinle.com",
            streamUrl = "https://trt.daioncdn.net/trtradyo1/trtradyo1.m3u8",
            streamStatus = VerificationStatus.VERIFIED,
            groups = listOf("Radyo"), notes = "TRT Radyo 1 Haber ve Kültür",
            permissionStatus = "Kamu Radyo Yayıncısı (FTA)"),
        TvChannel(203, "trtradyo3", "TRT Radyo 3", officialLivePage = "https://www.trtdinle.com",
            streamUrl = "https://trt.daioncdn.net/trtradyo3/trtradyo3.m3u8",
            streamStatus = VerificationStatus.VERIFIED,
            groups = listOf("Radyo"), notes = "TRT Radyo 3 Klasik & Caz",
            permissionStatus = "Kamu Radyo Yayıncısı (FTA)"),
        TvChannel(204, "trtturku", "TRT Türkü", officialLivePage = "https://www.trtdinle.com",
            streamUrl = "https://trt.daioncdn.net/trtturku/trtturku.m3u8",
            streamStatus = VerificationStatus.VERIFIED,
            groups = listOf("Radyo"), notes = "TRT Türkü Halk Müziği",
            permissionStatus = "Kamu Radyo Yayıncısı (FTA)"),
        TvChannel(205, "kralfm", "Kral FM", officialLivePage = "https://www.kralmuzik.com.tr",
            streamUrl = "https://dogus.daioncdn.net/kralfm/kralfm.m3u8",
            streamStatus = VerificationStatus.VERIFIED,
            groups = listOf("Radyo"), notes = "Kral FM İlaç Gibi Radyo",
            permissionStatus = "Ulusal Radyo FTA"),
        TvChannel(206, "kralpop", "Kral Pop", officialLivePage = "https://www.kralmuzik.com.tr",
            streamUrl = "https://dogus.daioncdn.net/kralpop/kralpop.m3u8",
            streamStatus = VerificationStatus.VERIFIED,
            groups = listOf("Radyo"), notes = "Kral Pop Türkçe Pop Müzik",
            permissionStatus = "Ulusal Radyo FTA"),
        TvChannel(207, "bestfm", "Best FM", officialLivePage = "https://www.bestfm.com.tr",
            streamUrl = "https://bestfm.listenpowerapp.com/bestfm/bestfm.stream/playlist.m3u8",
            streamStatus = VerificationStatus.VERIFIED,
            groups = listOf("Radyo"), notes = "Best FM Radyonuz Açık Olsun",
            permissionStatus = "Ulusal Radyo FTA"),
        TvChannel(208, "powerturk", "Power Türk", officialLivePage = "https://www.powerapp.com.tr",
            streamUrl = "https://powerturk.listenpowerapp.com/powerturk/powerturk.stream/playlist.m3u8",
            streamStatus = VerificationStatus.VERIFIED,
            groups = listOf("Radyo"), notes = "Power Türk Önce Müzik",
            permissionStatus = "Ulusal Radyo FTA"),
        TvChannel(209, "superfm", "Süper FM", officialLivePage = "https://karnaval.com",
            streamUrl = "https://superfm.listenpowerapp.com/superfm/superfm.stream/playlist.m3u8",
            streamStatus = VerificationStatus.VERIFIED,
            groups = listOf("Radyo"), notes = "Süper FM En İyi Türkçe Müzik",
            permissionStatus = "Ulusal Radyo FTA")
    )

    /** Kanal adına göre katalogdaki resmî sayfayı ve meta bilgileri bulur. */
    fun findOfficialChannel(name: String): TvChannel? {
        val norm = ChannelOrder.normalize(name)
        return builtInRaw.firstOrNull { ChannelOrder.normalize(it.name) == norm }
    }

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

    /** Dördüncü M3U oynatma listesi kaynağı (Türkiye M3U). */
    const val FOURTH_PLAYLIST_URL: String = "https://iptv-org.github.io/iptv/countries/tr.m3u"

    /** Oynatma listesi kaynakları: 4 kaynak birlikte indirilir ve birleştirilir */
    val PLAYLIST_URLS: List<String> = listOf(
        "https://iptv-org.github.io/iptv/countries/tr.m3u",
        "https://iptv-org.github.io/iptv/languages/tur.m3u",
        "https://streams.uzunmuhalefet.com/lists/tr.m3u",
        "https://iptv-org.github.io/iptv/countries/tr.m3u"
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

    /** Kanalın açılış süresini (gecikmesini) günceller. */
    fun updateChannelLatency(channelId: String, latencyMs: Long) {
        all = all.map { ch ->
            if (ch.id == channelId) ch.copy(lastStartupLatencyMs = latencyMs) else ch
        }
        recompute()
    }

    /** Başarıyla açılan akış adresini kanalın yayın listesinin en başına alır. */
    fun prioritizeStream(channelId: String, workingStream: String) {
        all = all.map { ch ->
            val streams = if (ch.streamUrls.isNotEmpty()) ch.streamUrls else listOfNotNull(ch.streamUrl)
            if (ch.id == channelId && workingStream in streams) {
                val reordered = listOf(workingStream) + streams.filter { it != workingStream }
                ch.copy(streamUrl = workingStream, streamUrls = reordered)
            } else ch
        }
        recompute()
    }

    /** Kanal adını dinamik olarak günceller. */
    fun renameChannel(channelId: String, newName: String) {
        all = all.map { if (it.id == channelId) it.copy(name = newName) else it }
        recompute()
    }

    /** Tüm kanal listesini ve sırasını günceller. */
    fun updateAll(list: List<TvChannel>) {
        all = list
        recompute()
    }

    private val categoryOrder = listOf(
        "General", "News", "Sports", "Movies", "Series", "Entertainment", "Kids", "Music", "Documentary",
        "Religious", "Culture", "Education", "Lifestyle", "Comedy", "Animation", "Family", "Business",
        "Cooking", "Travel", "Weather", "Science", "Outdoor", "Classic", "Shop", "Relax", "Legislative",
        "Ulusal", "Haber", "Spor", "Sinema", "Dizi", "Belgesel", "Müzik", "Çocuk", "Dini", "Yerel", "Radyo", "Özel Liste"
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
        "Radyo" -> "Radyo İstasyonları"; "Özel Liste" -> "Özel M3U Listesi"
        "Undefined" -> "Diğer"
        else -> key ?: "Diğer"
    }

    fun channelByNumber(number: Int): TvChannel? = channels.firstOrNull { it.number == number }
    fun channelById(id: String): TvChannel? = channels.firstOrNull { it.id == id }

    /** Yalnızca doğrulanmış (VERIFIED) veya kullanıcının kendi listesinden gelen (USER_PROVIDED) akışlar oynatılır. */
    fun playableStream(channel: TvChannel, index: Int = 0): String? {
        val urls = if (channel.streamUrls.isNotEmpty()) channel.streamUrls else listOfNotNull(channel.streamUrl)
        val url = urls.getOrNull(index)?.trim() ?: return null
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
