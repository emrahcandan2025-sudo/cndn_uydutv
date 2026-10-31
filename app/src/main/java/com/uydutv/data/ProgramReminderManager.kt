package com.uydutv.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * TV Program Hatırlatıcısı (Maç / Dizi Başlangıç Alarmı):
 * Kullanıcının EPG rehberinde seçtiği programları takip eder.
 * Başlangıç saati yaklaştığında (örn. 2 dakika kala) TV ekranında bildirim gösterir
 * ve tek tuşla ilgili kanala geçilmesini sağlar.
 */
data class ProgramReminder(
    val id: String,
    val channelId: String,
    val channelNumber: Int,
    val channelName: String,
    val programTitle: String,
    val startTimeMs: Long,
    val notified: Boolean = false
)

object ProgramReminderManager {
    private const val TAG = "ProgramReminderManager"
    private const val PREFS_NAME = "cndn_program_reminders"
    private const val KEY_REMINDERS = "active_reminders"

    private val inMemoryReminders = mutableListOf<ProgramReminder>()
    private var isLoaded = false

    @Synchronized
    fun getReminders(context: Context): List<ProgramReminder> {
        if (!isLoaded) loadFromDisk(context)
        val now = System.currentTimeMillis()
        // 2 saatten daha eski geçmiş programları temizle
        inMemoryReminders.removeAll { it.startTimeMs < now - 2 * 3600 * 1000L }
        return inMemoryReminders.toList()
    }

    @Synchronized
    fun addReminder(context: Context, reminder: ProgramReminder): Boolean {
        if (!isLoaded) loadFromDisk(context)
        // Aynı kanalda aynı program için mükerrer kayıt engelle
        inMemoryReminders.removeAll { it.channelId == reminder.channelId && it.programTitle.equals(reminder.programTitle, ignoreCase = true) }
        inMemoryReminders.add(reminder)
        saveToDisk(context)
        Log.i(TAG, "Hatırlatıcı eklendi: ${reminder.programTitle} (${reminder.channelName})")
        return true
    }

    @Synchronized
    fun removeReminder(context: Context, reminderId: String) {
        if (!isLoaded) loadFromDisk(context)
        inMemoryReminders.removeAll { it.id == reminderId }
        saveToDisk(context)
    }

    @Synchronized
    fun hasReminder(context: Context, channelId: String, programTitle: String): Boolean {
        if (!isLoaded) loadFromDisk(context)
        val now = System.currentTimeMillis()
        return inMemoryReminders.any {
            it.channelId == channelId &&
            it.programTitle.equals(programTitle, ignoreCase = true) &&
            it.startTimeMs >= now - 5 * 60 * 1000L
        }
    }

    /**
     * Başlangıç saati gelen (şu an ile sonraki 2 dakika içinde başlayan) ve henüz bildirilmemiş hatırlatıcıları bulur.
     */
    @Synchronized
    fun checkDueReminders(context: Context): List<ProgramReminder> {
        if (!isLoaded) loadFromDisk(context)
        val now = System.currentTimeMillis()
        val dueWindowEnd = now + 2 * 60 * 1000L // Önümüzdeki 2 dakika
        val dueList = mutableListOf<ProgramReminder>()

        for (i in inMemoryReminders.indices) {
            val r = inMemoryReminders[i]
            if (!r.notified && r.startTimeMs in (now - 60 * 1000L)..dueWindowEnd) {
                inMemoryReminders[i] = r.copy(notified = true)
                dueList.add(r)
            }
        }

        if (dueList.isNotEmpty()) {
            saveToDisk(context)
        }
        return dueList
    }

    private fun loadFromDisk(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_REMINDERS, null) ?: "[]"
            val arr = JSONArray(json)
            inMemoryReminders.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                inMemoryReminders.add(
                    ProgramReminder(
                        id = obj.getString("id"),
                        channelId = obj.getString("channelId"),
                        channelNumber = obj.getInt("channelNumber"),
                        channelName = obj.getString("channelName"),
                        programTitle = obj.getString("programTitle"),
                        startTimeMs = obj.getLong("startTimeMs"),
                        notified = obj.optBoolean("notified", false)
                    )
                )
            }
            isLoaded = true
        } catch (e: Exception) {
            Log.w(TAG, "Hatırlatıcılar yüklenemedi: ${e.message}")
            isLoaded = true
        }
    }

    private fun saveToDisk(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = JSONArray()
            for (r in inMemoryReminders) {
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("channelId", r.channelId)
                    put("channelNumber", r.channelNumber)
                    put("channelName", r.channelName)
                    put("programTitle", r.programTitle)
                    put("startTimeMs", r.startTimeMs)
                    put("notified", r.notified)
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_REMINDERS, arr.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Hatırlatıcılar kaydedilemedi: ${e.message}")
        }
    }
}
