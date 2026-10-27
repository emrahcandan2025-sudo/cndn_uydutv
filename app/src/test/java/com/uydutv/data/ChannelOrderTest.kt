package com.uydutv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelOrderTest {

    @Test
    fun testNormalizeTurkishCharacters() {
        assertEquals("trt1", ChannelOrder.normalize("TRT 1 HD"))
        assertEquals("atv", ChannelOrder.normalize("ATV FHD"))
        assertEquals("showtv", ChannelOrder.normalize("Show TV [1080p]"))
        assertEquals("kanald", ChannelOrder.normalize("Kanal D (Canlı)"))
        assertEquals("ekoturk", ChannelOrder.normalize("Ekotürk"))
        assertEquals("trtcocuk", ChannelOrder.normalize("TRT Çocuk"))
    }

    @Test
    fun testSlotAssignmentAndNoDuplicateNumbers() {
        val input = listOf(
            TvChannel(99, "ch1", "Bilinmeyen Yerel Kanal", streamUrl = "http://test.com/1"),
            TvChannel(99, "ch2", "TRT 1 HD", streamUrl = "http://test.com/2"),
            TvChannel(99, "ch3", "ATV", streamUrl = "http://test.com/3"),
            TvChannel(99, "ch4", "Başka Bir Kanal", streamUrl = "http://test.com/4")
        )

        val ordered = ChannelOrder.apply(input)
        val trt = ordered.first { it.name == "TRT 1 HD" }
        val atv = ordered.first { it.name == "ATV" }

        assertEquals(1, trt.number) // Slot 1
        assertEquals(2, atv.number) // Slot 2

        // Tekrarlanan kanal numarası olmadığını doğrula
        val numbers = ordered.map { it.number }
        assertEquals(numbers.size, numbers.toSet().size)
    }

    @Test
    fun testPlayableStreamFilterM3u() {
        val validChannel = TvChannel(1, "c1", "Test", streamUrl = "https://example.com/stream.m3u8", streamStatus = VerificationStatus.USER_PROVIDED)
        val invalidM3u = TvChannel(2, "c2", "M3u List", streamUrl = "https://example.com/playlist.m3u", streamStatus = VerificationStatus.USER_PROVIDED)
        val unverified = TvChannel(3, "c3", "Unverified", streamUrl = "https://example.com/stream.m3u8", streamStatus = VerificationStatus.PERMISSION_NOT_CONFIRMED)

        assertEquals("https://example.com/stream.m3u8", ChannelCatalog.playableStream(validChannel))
        assertEquals(null, ChannelCatalog.playableStream(invalidM3u))
        assertEquals(null, ChannelCatalog.playableStream(unverified))
    }
}
