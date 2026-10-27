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
}
