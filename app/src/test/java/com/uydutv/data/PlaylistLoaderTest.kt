package com.uydutv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistLoaderTest {

    @Test
    fun testParseStandardM3uWithTurkishCharacters() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="trt1.tr" tvg-name="TRT 1 HD" tvg-logo="https://example.com/trt1.png" group-title="Ulusal",TRT 1 HD
            https://live.example.com/trt1/stream.m3u8
            #EXTINF:-1 tvg-id="atv.tr" group-title="Ulusal;Genel",atv
            https://live.example.com/atv/stream.m3u8
            #EXTINF:-1 tvg-id="halktv.tr" group-title="Haber",Halk TV
            https://live.example.com/halktv/stream.m3u8
        """.trimIndent()

        val channels = PlaylistLoader.parse(m3uContent)
        assertEquals(3, channels.size)

        val trt1 = channels.firstOrNull { it.name.contains("TRT 1") }
        assertNotNull(trt1)
        assertEquals("trt1.tr", trt1?.xmltvId)
        assertEquals("https://example.com/trt1.png", trt1?.logoUrl)
        assertEquals(1, trt1?.number) // Slot 1

        val atv = channels.firstOrNull { it.name.equals("atv", ignoreCase = true) }
        assertNotNull(atv)
        assertEquals(2, atv?.number) // Slot 2
    }

    @Test
    fun testUtf8BomAndTurkishCharacterHandling() {
        val m3uWithBom = "\uFEFF#EXTM3U\n#EXTINF:-1 tvg-id=\"trt_cocuk\" group-title=\"Çocuk\",TRT Çocuk HD\nhttps://live.example.com/trtcocuk.m3u8\n"
        val channels = PlaylistLoader.parse(m3uWithBom)
        assertEquals(1, channels.size)
        assertEquals("TRT Çocuk HD", channels[0].name)
        assertEquals("trt_cocuk", channels[0].xmltvId)
    }

    @Test
    fun testFilterEmptyOrInvalidEntries() {
        val malformedM3u = """
            #EXTM3U
            #EXTINF:-1,
            https://live.example.com/noname.m3u8
            #EXTINF:-1,Kanal Adı Ama Url Yok
            
            #EXTINF:-1,Kanal İle M3U Dosyası (Geçersiz Akış)
            https://live.example.com/playlist.m3u
            #EXTINF:-1,Geçerli Kanal
            https://live.example.com/valid.m3u8
        """.trimIndent()

        val channels = PlaylistLoader.parse(malformedM3u)
        assertEquals(1, channels.size)
        assertEquals("Geçerli Kanal", channels[0].name)
    }

    @Test
    fun testDeduplicateExactStreamUrls() {
        val duplicateUrls = """
            #EXTM3U
            #EXTINF:-1 tvg-id="trt1" group-title="Genel",TRT 1
            https://live.example.com/same_stream.m3u8
            #EXTINF:-1 tvg-id="trt1_dup" group-title="Genel",TRT 1 Yedek
            https://live.example.com/same_stream.m3u8
            #EXTINF:-1 tvg-id="trt2" group-title="Kültür",TRT 2
            https://live.example.com/different_stream.m3u8
        """.trimIndent()

        val channels = PlaylistLoader.parse(duplicateUrls)
        assertEquals(2, channels.size)
    }

    @Test
    fun testMergeMultipleListsAndConsolidateStreams() {
        val list1 = PlaylistLoader.parse("""
            #EXTM3U
            #EXTINF:-1 tvg-id="trt1" group-title="Ulusal",TRT 1
            https://source1.com/trt1.m3u8
            #EXTINF:-1 tvg-id="atv" group-title="Ulusal",ATV
            https://source1.com/atv.m3u8
        """.trimIndent())

        val list2 = PlaylistLoader.parse("""
            #EXTM3U
            #EXTINF:-1 tvg-id="trt1" group-title="Genel",TRT 1 HD
            https://source2.com/trt1_alt.m3u8
            #EXTINF:-1 tvg-id="halktv" group-title="Haber",Halk TV
            https://source2.com/halktv.m3u8
        """.trimIndent())

        val list3 = PlaylistLoader.parse("""
            #EXTM3U
            #EXTINF:-1 tvg-id="trt1" group-title="Ulusal",TRT 1
            https://source3.com/trt1_backup.m3u8
            #EXTINF:-1 tvg-id="atv" group-title="Ulusal",ATV HD
            https://source3.com/atv_alt.m3u8
        """.trimIndent())

        val merged = PlaylistLoader.mergeChannelLists(listOf(list1, list2, list3))

        val trt1 = merged.firstOrNull { it.number == 1 } // TRT 1 is Slot 1
        assertNotNull(trt1)
        assertEquals(3, trt1?.streamUrls?.size)
        assertEquals("https://source1.com/trt1.m3u8", trt1?.streamUrls?.get(0))
        assertEquals("https://source2.com/trt1_alt.m3u8", trt1?.streamUrls?.get(1))
        assertEquals("https://source3.com/trt1_backup.m3u8", trt1?.streamUrls?.get(2))

        val atv = merged.firstOrNull { it.number == 2 } // ATV is Slot 2
        assertNotNull(atv)
        assertEquals(2, atv?.streamUrls?.size)
    }

    @Test
    fun testFilterPaywalledStreams() {
        val m3uWithPaywalled = """
            #EXTM3U
            #EXTINF:-1,TRT 1
            https://live.example.com/trt1.m3u8
            #EXTINF:-1,Digiturk BeIN Sports 1
            https://live.example.com/bein1.m3u8
            #EXTINF:-1,D-Smart Spor Smart
            https://live.example.com/sporsmart.m3u8
            #EXTINF:-1,Tivibu Spor HD
            https://live.example.com/tivibu.m3u8
            #EXTINF:-1,Halk TV
            https://live.example.com/halktv.m3u8
        """.trimIndent()

        val parsed = PlaylistLoader.parse(m3uWithPaywalled)
        assertEquals(2, parsed.size)
        assertTrue(parsed.any { it.name == "TRT 1" })
        assertTrue(parsed.any { it.name == "Halk TV" })
        assertFalse(parsed.any { it.name.contains("beIN", ignoreCase = true) })
        assertFalse(parsed.any { it.name.contains("Digiturk", ignoreCase = true) })
    }

    @Test
    fun testFourSourcesConfigurationAndMerging() {
        assertEquals(4, ChannelCatalog.PLAYLIST_URLS.size)
        assertEquals("https://iptv-org.github.io/iptv/countries/tr.m3u", ChannelCatalog.PLAYLIST_URLS[0])
        assertEquals("https://iptv-org.github.io/iptv/languages/tur.m3u", ChannelCatalog.PLAYLIST_URLS[1])
        assertEquals("https://streams.uzunmuhalefet.com/lists/tr.m3u", ChannelCatalog.PLAYLIST_URLS[2])
        assertEquals("https://iptv-org.github.io/iptv/countries/tr.m3u", ChannelCatalog.PLAYLIST_URLS[3])
        assertEquals(ChannelCatalog.FOURTH_PLAYLIST_URL, ChannelCatalog.PLAYLIST_URLS[3])
    }

    @Test
    fun testOfficialCatalogMatchAndFallbackPageInheritance() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,TRT 1
            https://live.example.com/trt1.m3u8
            #EXTINF:-1,NTV
            https://live.example.com/ntv.m3u8
        """.trimIndent()

        val parsed = PlaylistLoader.parse(m3u)
        val trt1 = parsed.firstOrNull { it.name.contains("TRT 1") }
        assertNotNull(trt1)
        assertEquals("https://www.trtizle.com/canli/tv/trt-1", trt1?.officialLivePage)
        assertTrue(trt1?.isFreeAccess == true)
        assertEquals("2026-10-30", trt1?.lastCheckDate)

        val ntv = parsed.firstOrNull { it.name.contains("NTV") }
        assertNotNull(ntv)
        assertEquals("https://www.ntv.com.tr/canli-yayin", ntv?.officialLivePage)
        assertTrue(ntv?.isFreeAccess == true)
    }

    @Test
    fun testTurkishCatalogCoversAllRequiredCategories() {
        val builtIn = ChannelCatalog.builtIn
        assertTrue(builtIn.size >= 40)

        // Ulusal, Haber, Spor, Çocuk, Belgesel, Yerel kategorilerinin tümü mevcut olmalı
        val categories = ChannelCatalog.categories()
        assertTrue(categories.contains("Ulusal"))
        assertTrue(categories.contains("Haber"))
        assertTrue(categories.contains("Spor"))
        assertTrue(categories.contains("Çocuk"))
        assertTrue(categories.contains("Belgesel"))
        assertTrue(categories.contains("Yerel"))

        for (channel in builtIn) {
            assertTrue("Kanal adı boş olamaz: ${channel.name}", channel.name.isNotBlank())
            assertTrue("Resmî canlı yayın sayfası boş olamaz: ${channel.name}", !channel.officialLivePage.isNullOrBlank())
            assertTrue("Resmî sayfa http/https ile başlamalı: ${channel.officialLivePage}", channel.officialLivePage!!.startsWith("http"))
            assertTrue("Tüm kanallar ücretsiz erişim (FTA) olmalı: ${channel.name}", channel.isFreeAccess)
            assertTrue("İzin durumu belirtilmeli: ${channel.name}", channel.permissionStatus.isNotBlank())
        }
    }
}
