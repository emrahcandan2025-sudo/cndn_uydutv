package com.uydutv

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.setPadding
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.uydutv.data.ChannelCatalog
import com.uydutv.data.TvChannel
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var infoPanel: LinearLayout
    private lateinit var guidePanel: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var infoTitle: TextView
    private lateinit var infoSubtitle: TextView
    private lateinit var guideRows: LinearLayout
    private var player: ExoPlayer? = null
    private var channelIndex = 0
    private var overlayVisible = false
    private var guideVisible = false
    private val currentChannel: TvChannel get() = ChannelCatalog.channels[channelIndex]
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val digitBuffer = StringBuilder()
    private var lastInputAt = 0L
    private val programs = mutableMapOf<String, MutableList<Program>>()
    private val idleHide = Runnable { hideOverlays() }
    private val numberCommit = Runnable { commitNumber() }
    private val retryRunnable = Runnable { playCurrentChannel() }
    private val bg = Color.rgb(8, 10, 14)
    private val panel = Color.argb(225, 12, 16, 23)
    private val accent = Color.rgb(239, 68, 68)

    data class Program(val channelId: String, val title: String, val start: Long, val end: Long)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        buildUi()
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    statusText.text = "Yayın bağlantısı kurulamadı · yeniden deneniyor"
                    statusText.visibility = View.VISIBLE
                    handler.removeCallbacks(retryRunnable)
                    handler.postDelayed(retryRunnable, 3000)
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) statusText.visibility = View.GONE
                    if (playbackState == Player.STATE_BUFFERING && currentChannel.streamUrl != null) {
                        statusText.text = "Canlı yayın yükleniyor…"
                        statusText.visibility = View.VISIBLE
                    }
                }
            })
        }
        val saved = getSharedPreferences("uydutv", MODE_PRIVATE).getInt("last_channel", 1)
        channelIndex = ChannelCatalog.channels.indexOfFirst { it.number == saved }.coerceAtLeast(0)
        showChannelInfo()
        playCurrentChannel()
        loadEpg()
        resetIdleTimer()
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); isFocusableInTouchMode = true }
        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        root.addView(playerView)
        statusText = text("UyduTV · yayın kaynağı bekleniyor", 22f, Color.WHITE, true).apply {
            setBackgroundColor(Color.argb(190, 0, 0, 0)); setPadding(24)
            visibility = View.VISIBLE
        }
        root.addView(statusText, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(26); setBackgroundColor(panel)
        }
        infoTitle = text("", 28f, Color.WHITE, true)
        infoSubtitle = text("", 18f, Color.LTGRAY, false)
        infoPanel.addView(infoTitle); infoPanel.addView(infoSubtitle)
        root.addView(infoPanel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        guidePanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28); setBackgroundColor(Color.argb(245, 8, 11, 16)); visibility = View.GONE }
        guidePanel.addView(text("YAYIN REHBERİ   ·   OK: KANALA GEÇ   ·   BACK: KAPAT", 22f, Color.WHITE, true))
        val scroller = ScrollView(this)
        guideRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroller.addView(guideRows)
        guidePanel.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(guidePanel, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        setContentView(root)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setPadding(8); gravity = Gravity.CENTER_VERTICAL
    }

    private fun playCurrentChannel() {
        handler.removeCallbacks(retryRunnable)
        val channel = currentChannel
        getSharedPreferences("uydutv", MODE_PRIVATE).edit().putInt("last_channel", channel.number).apply()
        showChannelInfo()
        val stream = ChannelCatalog.playableStream(channel)
        if (stream.isNullOrBlank()) {
            player?.stop()
            statusText.text = "${channel.name}\nDoğrudan yayın adresi ve kullanım izni doğrulanmadı.\nKanal kaynağı yapılandırıldığında canlı yayın başlayacak."
            statusText.visibility = View.VISIBLE
            return
        }
        statusText.text = "Canlı yayın yükleniyor…"
        statusText.visibility = View.VISIBLE
        try {
            player?.setMediaItem(MediaItem.fromUri(stream))
            player?.prepare()
            player?.playWhenReady = true
        } catch (_: Exception) {
            handler.postDelayed(retryRunnable, 3000)
        }
    }

    private fun showChannelInfo() {
        val c = currentChannel
        infoTitle.text = String.format(Locale.getDefault(), "%03d   %s", c.number, c.name)
        val now = System.currentTimeMillis()
        val epg = findProgram(c, now)
        val next = findNextProgram(c, now)
        infoSubtitle.text = if (epg != null) "${epg.title}   ·   Sonraki: ${next?.title ?: "—"}" else "Canlı   ·   Program bilgisi bulunamadı"
        infoPanel.visibility = View.VISIBLE
        overlayVisible = true
        resetIdleTimer()
    }

    private fun findProgram(c: TvChannel, now: Long): Program? = programs.values.flatten().firstOrNull { it.start <= now && it.end > now && channelMatches(c, it.channelId) }
    private fun findNextProgram(c: TvChannel, now: Long): Program? = programs.values.flatten().filter { it.start > now && channelMatches(c, it.channelId) }.minByOrNull { it.start }
    private fun channelMatches(c: TvChannel, epgId: String): Boolean = c.xmltvId?.equals(epgId, true) == true

    private fun openGuide() {
        guideVisible = true; overlayVisible = true
        guideRows.removeAllViews()
        val now = System.currentTimeMillis()
        ChannelCatalog.channels.forEachIndexed { index, c ->
            val current = findProgram(c, now)?.title ?: "Canlı"
            val next = findNextProgram(c, now)?.title ?: "Program bilgisi yok"
            val row = text(String.format(Locale.getDefault(), "%03d   %-20s   %s   →   %s", c.number, c.name, current, next), 20f, Color.WHITE, index == channelIndex).apply {
                setPadding(18); isFocusable = true; isFocusableInTouchMode = true
                setBackgroundColor(if (index == channelIndex) Color.rgb(48, 56, 70) else Color.TRANSPARENT)
                setOnClickListener { channelIndex = index; closeGuide(); playCurrentChannel() }
                setOnFocusChangeListener { v, hasFocus -> v.setBackgroundColor(if (hasFocus) Color.rgb(48, 56, 70) else Color.TRANSPARENT) }
            }
            guideRows.addView(row, LinearLayout.LayoutParams(-1, 64))
        }
        guidePanel.visibility = View.VISIBLE
        infoPanel.visibility = View.GONE
        guideRows.getChildAt(channelIndex)?.requestFocus()
        resetIdleTimer()
    }

    private fun closeGuide() { guideVisible = false; guidePanel.visibility = View.GONE; showChannelInfo() }
    private fun hideOverlays() { if (!guideVisible) { infoPanel.visibility = View.GONE; overlayVisible = false } }
    private fun resetIdleTimer() { handler.removeCallbacks(idleHide); handler.postDelayed(idleHide, 5000) }

    private fun changeChannel(delta: Int) {
        channelIndex = (channelIndex + delta + ChannelCatalog.channels.size) % ChannelCatalog.channels.size
        // Brief static-style transition: dark flash, then the selected channel.
        root.setBackgroundColor(Color.rgb(190, 190, 190))
        handler.postDelayed({ root.setBackgroundColor(Color.BLACK); playCurrentChannel() }, 110)
    }

    private fun commitNumber() {
        if (digitBuffer.isEmpty()) return
        val number = digitBuffer.toString().toIntOrNull()
        digitBuffer.clear()
        val found = ChannelCatalog.channels.indexOfFirst { it.number == number }
        if (found >= 0) { channelIndex = found; playCurrentChannel() }
        else Toast.makeText(this, "Bu kanal numarası listede yok", Toast.LENGTH_SHORT).show()
    }

    private fun loadEpg() {
        worker.execute {
            try {
                val conn = (URL(ChannelCatalog.XMLTV_SOURCE_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000; readTimeout = 15000; requestMethod = "GET"; setRequestProperty("User-Agent", "UyduTV/1.0 AndroidTV")
                }
                conn.inputStream.use { input ->
                    val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(input, "UTF-8") }
                    var event = parser.eventType
                    var channel = ""; var title = ""; var start = 0L; var end = 0L
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG) when (parser.name) {
                            "programme" -> { channel = parser.getAttributeValue(null, "channel") ?: ""; start = parseXmltvTime(parser.getAttributeValue(null, "start")); end = parseXmltvTime(parser.getAttributeValue(null, "stop")); title = "" }
                            "title" -> if (channel.isNotEmpty()) title = parser.nextText()
                        }
                        if (event == XmlPullParser.END_TAG && parser.name == "programme" && channel.isNotEmpty() && title.isNotBlank() && end > start) {
                            synchronized(programs) { programs.getOrPut(channel) { mutableListOf() }.add(Program(channel, title, start, end)) }
                        }
                        event = parser.next()
                    }
                }
                handler.post { if (overlayVisible) { showChannelInfo(); if (guideVisible) openGuide() } }
            } catch (_: Exception) {
                // Keep the app usable offline; channels without verified EPG IDs show “Canlı”.
            }
        }
    }

    private fun parseXmltvTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            val normalized = raw.trim()
            val format = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val parts = normalized.split(Regex("\\s+"))
            val zone = if (parts.size > 1) parts[1] else "+0000"
            format.parse("${parts[0]} $zone")?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        lastInputAt = System.currentTimeMillis()
        if (guideVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { closeGuide(); return true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { resetIdleTimer(); return super.dispatchKeyEvent(event) }
            }
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> { changeChannel(-1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> { changeChannel(1); return true }
            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_G -> { openGuide(); return true }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_I -> { showChannelInfo(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { if (infoPanel.visibility != View.VISIBLE) showChannelInfo(); else openGuide(); return true }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { hideOverlays(); return true }
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9 -> {
                val digit = event.keyCode - KeyEvent.KEYCODE_0
                digitBuffer.append(digit)
                infoTitle.text = "Kanal numarası: $digitBuffer"
                infoSubtitle.text = "Numara girişi"
                infoPanel.visibility = View.VISIBLE; overlayVisible = true
                handler.removeCallbacks(numberCommit); handler.postDelayed(numberCommit, 1200)
                resetIdleTimer(); return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                // Deliberately do not expose time-shift controls; keep live position.
                player?.seekToDefaultPosition(); player?.playWhenReady = true; resetIdleTimer(); return true
            }
        }
        resetIdleTimer()
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onStart() { super.onStart(); if (player != null && currentChannel.streamUrl != null) player?.play() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        player?.release(); player = null
        super.onDestroy()
    }
}
