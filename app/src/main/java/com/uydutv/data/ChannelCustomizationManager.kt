package com.uydutv.data

import android.content.Context
import android.util.Log

/**
 * Kullanıcının özel kanal isimlerini ve kanal sırası/numarası tercihlerini yönetir.
 * M3U listesi güncellendiğinde bile kullanıcının belirlediği isimler ve sıra korunur.
 */
object ChannelCustomizationManager {
    private const val TAG = "ChannelCustomization"
    private const val PREFS_NAME = "cndn_channel_customizations"
    private const val KEY_NAME_PREFIX = "name_"
    private const val KEY_NUMBER_PREFIX = "num_"
    private const val KEY_CUSTOM_ORDER = "custom_order_ids"

    /**
     * Kanala kullanıcı tarafından verilen özel ismi kaydeder.
     */
    fun setCustomName(context: Context, channelId: String, newName: String) {
        val trimmed = newName.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_NAME_PREFIX + channelId).apply()
        } else {
            prefs.edit().putString(KEY_NAME_PREFIX + channelId, trimmed).apply()
        }
    }

    /**
     * Kanala ait özel bir isim varsa döndürür, yoksa null döner.
     */
    fun getCustomName(context: Context, channelId: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NAME_PREFIX + channelId, null)?.takeIf { it.isNotBlank() }
    }

    /**
     * Kanala yeni bir kanal numarası atar ve tam sırayı kaydeder.
     */
    fun setCustomNumber(context: Context, channelId: String, newNumber: Int, channels: List<TvChannel>): List<TvChannel> {
        val targetIdx = channels.indexOfFirst { it.id == channelId }
        if (targetIdx < 0 || newNumber < 1) return channels

        val list = channels.toMutableList()
        val item = list.removeAt(targetIdx)

        // Yeni sıraya yerleştir (1 tabanlı indeksi liste boyutuna uyarla)
        val insertIdx = (newNumber - 1).coerceIn(0, list.size)
        list.add(insertIdx, item)

        // Numaraları 1..N olarak yeniden dağıt
        val renumbered = list.mapIndexed { idx, ch ->
            ch.copy(number = idx + 1)
        }

        saveChannelOrder(context, renumbered)
        return renumbered
    }

    /**
     * Kanalı listede yukarı (-1) veya aşağı (+1) kaydırır.
     */
    fun moveChannel(context: Context, channelId: String, delta: Int, channels: List<TvChannel>): List<TvChannel> {
        val currentIdx = channels.indexOfFirst { it.id == channelId }
        if (currentIdx < 0) return channels

        val newIdx = currentIdx + delta
        if (newIdx !in channels.indices) return channels

        val list = channels.toMutableList()
        val item = list.removeAt(currentIdx)
        list.add(newIdx, item)

        val renumbered = list.mapIndexed { idx, ch ->
            ch.copy(number = idx + 1)
        }

        saveChannelOrder(context, renumbered)
        return renumbered
    }

    /**
     * Kanalın özel ismi ve sıra kaydını sıfırlar.
     */
    fun resetCustomization(context: Context, channelId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_NAME_PREFIX + channelId)
            .remove(KEY_NUMBER_PREFIX + channelId)
            .apply()
    }

    /**
     * Kanal sıralamasını diske kaydeder.
     */
    fun saveChannelOrder(context: Context, channels: List<TvChannel>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val orderString = channels.joinToString(",") { it.id }
            val editor = prefs.edit().putString(KEY_CUSTOM_ORDER, orderString)
            channels.forEach { ch ->
                editor.putInt(KEY_NUMBER_PREFIX + ch.id, ch.number)
            }
            editor.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Sıralama kaydedilemedi: ${e.message}")
        }
    }

    /**
     * Kanal listesine kullanıcının belirlediği isimleri ve sıra tercihlerini uygular.
     */
    fun applyCustomizations(context: Context, channels: List<TvChannel>): List<TvChannel> {
        if (channels.isEmpty()) return channels

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Önce özel isimleri uygula
        val renamed = channels.map { ch ->
            val custom = prefs.getString(KEY_NAME_PREFIX + ch.id, null)
            if (!custom.isNullOrBlank()) ch.copy(name = custom.trim()) else ch
        }

        // 2. Varsa kayıtlı özel kanal sırasını uygula
        val savedOrderStr = prefs.getString(KEY_CUSTOM_ORDER, null)
        if (!savedOrderStr.isNullOrBlank()) {
            val orderedIds = savedOrderStr.split(",").filter { it.isNotBlank() }
            if (orderedIds.isNotEmpty()) {
                val channelMap = renamed.associateBy { it.id }.toMutableMap()
                val result = mutableListOf<TvChannel>()

                // Önce kayıtlı sıradaki kanalları ekle
                for (id in orderedIds) {
                    val ch = channelMap.remove(id)
                    if (ch != null) {
                        result.add(ch)
                    }
                }

                // Listeye yeni eklenmiş kanalları sona ekle
                result.addAll(channelMap.values)

                // Numaraları güncelle
                return result.mapIndexed { idx, ch ->
                    val customNum = prefs.getInt(KEY_NUMBER_PREFIX + ch.id, idx + 1)
                    ch.copy(number = if (customNum > 0) customNum else idx + 1)
                }.sortedBy { it.number }.mapIndexed { idx, ch -> ch.copy(number = idx + 1) }
            }
        }

        return renamed
    }
}
