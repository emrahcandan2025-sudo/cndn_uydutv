package com.uydutv.data

import java.util.Locale

/**
 * Kullanıcının TV'sindeki kanal sırası ve numaraları.
 * Oynatma listesindeki kanallar adlarına göre bu sıraya oturtulur; numaralar TV'deki gibi
 * (boşluklarıyla birlikte) verilir. Yeni sıra için yalnızca `slots` listesini düzenleyin.
 */
object ChannelOrder {
    /** true: listede olmayan kanallar sona eklenir. false: gizlenir (hiçbiri eşleşmezse liste yine de boş bırakılmaz). */
    const val KEEP_UNMATCHED = true

    // normalize() tarafından kullanılır; slots'tan ÖNCE tanımlanmalı.
    private val bracketed = Regex("""\(.*?\)|\[.*?\]""")
    private val nonAlnum = Regex("[^a-z0-9]")
    private val qualitySuffix = Regex("(fhd|uhd|hd|sd)$")

    private class Slot(val number: Int, title: String, aliases: List<String> = emptyList()) {
        val keys: Set<String> = (aliases + title).map { normalize(it) }.toSet()
    }

    private val slots: List<Slot> = listOf(
        Slot(1, "TRT 1"),
        Slot(2, "ATV"),
        Slot(3, "Kanal D"),
        Slot(4, "Star TV", listOf("Star")),
        Slot(5, "Show TV", listOf("Show")),
        Slot(6, "Beyaz TV", listOf("Beyaz")),
        Slot(7, "Kanal 7"),
        Slot(8, "TV8"),
        Slot(9, "NOW", listOf("NOW TV", "FOX", "FOX TV")),
        Slot(10, "Ülke TV", listOf("Ulke")),
        Slot(11, "TRT Haber"),
        Slot(13, "A Haber"),
        Slot(15, "TVNET", listOf("TV Net")),
        Slot(17, "Habertürk TV", listOf("Haberturk")),
        Slot(20, "NTV"),
        Slot(21, "CNN Türk"),
        Slot(23, "TGRT Haber"),
        Slot(24, "24", listOf("24 TV", "TV24")),
        Slot(25, "Haber Global"),
        Slot(26, "Bloomberg HT"),
        Slot(27, "Ekotürk", listOf("Ekotürk TV")),
        Slot(29, "Bengütürk", listOf("Bengütürk TV")),
        Slot(30, "TV 100"),
        Slot(31, "Halk TV"),
        Slot(32, "Flash Haber", listOf("Flash Haber TV")),
        Slot(33, "Akit TV", listOf("Akit")),
        Slot(35, "TRT 2")
    )

    /** Türkçe harfleri sadeleştirir; boşluk, noktalama, (parantez) ve HD/FHD ekini atar. */
    private fun normalize(name: String): String {
        val sb = StringBuilder(name.length)
        for (ch in name) sb.append(
            when (ch) {
                'ı', 'İ' -> 'i'; 'ü', 'Ü' -> 'u'; 'ö', 'Ö' -> 'o'
                'ç', 'Ç' -> 'c'; 'ş', 'Ş' -> 's'; 'ğ', 'Ğ' -> 'g'
                else -> ch
            }
        )
        return sb.toString().lowercase(Locale.ROOT)
            .replace(bracketed, "")
            .replace(nonAlnum, "")
            .replace(qualitySuffix, "")
    }

    fun apply(channels: List<TvChannel>): List<TvChannel> {
        val remaining = channels.toMutableList()
        val ordered = mutableListOf<TvChannel>()
        for (slot in slots) {
            val idx = remaining.indexOfFirst { normalize(it.name) in slot.keys }
            if (idx >= 0) ordered += remaining.removeAt(idx).copy(number = slot.number)
        }
        if (KEEP_UNMATCHED) {
            var n = slots.maxOf { it.number }
            for (c in remaining) { n++; ordered += c.copy(number = n) }
        }
        return ordered.ifEmpty { channels }
    }
}
