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
    private val qualitySuffix = Regex("(fhd|uhd|hd|sd|4k)$")

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
        Slot(12, "TRT Spor"),
        Slot(13, "A Haber"),
        Slot(14, "A Spor"),
        Slot(15, "TVNET", listOf("TV Net")),
        Slot(16, "TRT Çocuk", listOf("TRT Cocuk")),
        Slot(17, "Habertürk TV", listOf("Haberturk")),
        Slot(18, "Teve2"),
        Slot(19, "Sözcü TV", listOf("Sozcu TV", "SZC")),
        Slot(20, "NTV"),
        Slot(21, "CNN Türk", listOf("CNN Turk")),
        Slot(22, "Tele1", listOf("Tele 1")),
        Slot(23, "TGRT Haber"),
        Slot(24, "24", listOf("24 TV", "TV24")),
        Slot(25, "Haber Global"),
        Slot(26, "Bloomberg HT"),
        Slot(27, "Ekotürk", listOf("Ekotürk TV", "Ekoturk")),
        Slot(28, "TRT Spor Yıldız", listOf("TRT Yildiz", "Spor Yildiz")),
        Slot(29, "Bengütürk", listOf("Bengütürk TV", "Benguturk")),
        Slot(30, "TV 100", listOf("TV100")),
        Slot(31, "Halk TV"),
        Slot(32, "Flash Haber", listOf("Flash Haber TV")),
        Slot(33, "Akit TV", listOf("Akit")),
        Slot(34, "KRT TV", listOf("KRT")),
        Slot(35, "TRT 2"),
        Slot(36, "TRT Belgesel"),
        Slot(37, "Diyanet TV"),
        Slot(38, "Minika Çocuk", listOf("Minika Cocuk")),
        Slot(39, "Minika GO"),
        Slot(40, "TGRT Belgesel"),
        Slot(41, "TRT Müzik", listOf("TRT Muzik")),
        Slot(42, "FB TV", listOf("Fenerbahce TV")),
        Slot(43, "Sports TV"),
        Slot(44, "Kadırga TV", listOf("Kadirga TV", "Kadirga")),
        Slot(45, "Çay TV", listOf("Cay TV")),
        Slot(46, "Ege TV"),
        Slot(47, "Kon TV", listOf("Kontv")),
        Slot(48, "Olay TV"),
        Slot(49, "Aksu TV"),
        Slot(50, "Kanal Fırat", listOf("Kanal Firat"))
    )

    /** Türkçe harfleri sadeleştirir; boşluk, noktalama, (parantez) ve HD/FHD ekini atar. */
    fun normalize(name: String): String {
        val sb = StringBuilder(name.length)
        for (ch in name) sb.append(
            when (ch) {
                'ı', 'İ', 'I' -> 'i'; 'ü', 'Ü' -> 'u'; 'ö', 'Ö' -> 'o'
                'ç', 'Ç' -> 'c'; 'ş', 'Ş' -> 's'; 'ğ', 'Ğ' -> 'g'
                else -> ch
            }
        )
        return sb.toString().lowercase(Locale.ROOT)
            .replace(bracketed, "")
            .replace(nonAlnum, "")
            .replace(qualitySuffix, "")
    }

    /** Kanal adının bilinen TV slotlarından birine ait olup olmadığını kontrol eder. */
    fun slotKeyForName(name: String): String? {
        val norm = normalize(name)
        val slot = slots.firstOrNull { norm in it.keys }
        return slot?.let { "slot_${it.number}" }
    }

    /**
     * Kanalları TV slotlarına yerleştirir.
     * Tüm kanallara tekil (tekrarsız) ve kararlı kanal numaraları atanır.
     */
    fun apply(channels: List<TvChannel>): List<TvChannel> {
        if (channels.isEmpty()) return emptyList()
        val remaining = channels.toMutableList()
        val ordered = mutableListOf<TvChannel>()
        val usedNumbers = mutableSetOf<Int>()

        // 1. Önce tanımlı slotlarla eşleşen kanalları yerleştir
        for (slot in slots) {
            val idx = remaining.indexOfFirst { normalize(it.name) in slot.keys }
            if (idx >= 0) {
                val matched = remaining.removeAt(idx)
                ordered += matched.copy(number = slot.number)
                usedNumbers.add(slot.number)
            }
        }

        // 2. Kalan kanalları sıraya ekle (çakışmayan tekil numaralarla)
        if (KEEP_UNMATCHED) {
            val maxSlot = slots.maxOfOrNull { it.number } ?: 0
            var nextNumber = maxSlot + 1
            for (c in remaining) {
                while (usedNumbers.contains(nextNumber)) {
                    nextNumber++
                }
                ordered += c.copy(number = nextNumber)
                usedNumbers.add(nextNumber)
                nextNumber++
            }
        }

        return ordered.ifEmpty {
            channels.mapIndexed { idx, ch -> ch.copy(number = idx + 1) }
        }
    }
}
