package com.uydutv

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
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
import com.uydutv.data.PlaylistLoader
import com.uydutv.data.TvChannel
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var flash: View
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

    // EPG: yalnızca kataloğdaki xmltvId'lerle eşleşen kanallar tutulur; listeler start'a göre sıralıdır.
    @Volatile private var epg: Map<String, List<Program>> = emptyMap()
    private var epgWanted: Set<String> = emptySet()
    private var epgReloadPending = false
    private val epgLoading = AtomicBoolean(false)

    // Oynatma hatası yönetimi
    private var retryCount = 0
    private var skipChain = 0

    private val idleHide = Runnable { hideOverlays() }
    private val numberCommit = Runnable { commitNumber() }
    private val retryRunnable = Runnable { playCurrentChannel(isRetry = true) }
    private val skipRunnable = Runnable {
        channelIndex = (channelIndex + 1) % ChannelCatalog.channels.size
        playCurrentChannel()
    }
    private val changeRunnable = Runnable { flash.visibility = View.GONE; playCurrentChannel() }

    private val bg = Color.rgb(8, 10, 14)
    private val panel = Color.argb(225, 12, 16, 23)
    private val accent = Color.rgb(239, 68, 68)

    data class Program(val channelId: String, val title: String, val start: Long, val end: Long)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun runOnWorker(block: () -> Unit) {
        try { worker.execute(block) } catch (_: RejectedExecutionException) { /* activity kapanıyor */ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        buildUi()
        showIntro()
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) = handlePlayerError()
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        statusText.visibility = View.GONE
                        retryCount = 0
                        skipChain = 0
                    }
                    if (playbackState == Player.STATE_BUFFERING && ChannelCatalog.playableStream(currentChannel) != null) {
                        statusText.text = "Canlı yayın yükleniyor…"
                        statusText.visibility = View.VISIBLE
                    }
                }
            })
        }
        PlaylistLoader.loadCached(this)?.let { ChannelCatalog.channels = it }
        val saved = getSharedPreferences("uydutv", MODE_PRIVATE).getInt("last_channel", 1)
        channelIndex = ChannelCatalog.channels.indexOfFirst { it.number == saved }.coerceAtLeast(0)
        playCurrentChannel()
        refreshPlaylist()
        loadEpg()
        resetIdleTimer()
    }

    /** Hata sonrası: 3 kez aynı kanalı dene, olmazsa sonraki kanala geç; hepsi başarısızsa dur. */
    private fun handlePlayerError() {
        handler.removeCallbacks(retryRunnable)
        handler.removeCallbacks(skipRunnable)
        retryCount++
        val total = ChannelCatalog.channels.size
        when {
            retryCount <= MAX_RETRY -> {
                statusText.text = "Yayın bağlantısı kurulamadı · yeniden deneniyor ($retryCount/$MAX_RETRY)"
                handler.postDelayed(retryRunnable, 3000)
            }
            skipChain < total - 1 -> {
                skipChain++
                statusText.text = "Bu kanal yanıt vermiyor · sonraki kanala geçiliyor"
                handler.postDelayed(skipRunnable, 1500)
            }
            else -> statusText.text = "Hiçbir kanal oynatılamadı · bağlantınızı kontrol edin"
        }
        statusText.visibility = View.VISIBLE
    }

    private var introView: PlayerView? = null
    private var introPlayer: ExoPlayer? = null
    private val introTimeout = Runnable { finishIntro() }

    /**
     * Açılış videosu: app/src/main/res/raw/intro.mp4 varsa tam ekran oynatılır (kumandadan herhangi bir tuşla geçilir).
     * Dosya yoksa eski logo animasyonu gösterilir.
     */
    private fun showIntro() {
        val resId = resources.getIdentifier("intro", "raw", packageName)
        if (resId == 0) { showSplash(); return }
        player?.volume = 0f // video sırasında kanal sesi kapalı
        val pv = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        val p = ExoPlayer.Builder(this).build()
        pv.player = p
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) finishIntro()
            }
            override fun onPlayerError(error: PlaybackException) = finishIntro()
        })
        p.setMediaItem(MediaItem.fromUri("android.resource://$packageName/$resId"))
        p.prepare()
        p.playWhenReady = true
        root.addView(pv)
        introView = pv
        introPlayer = p
        handler.postDelayed(introTimeout, 20000) // güvenlik: en fazla 20 sn
    }

    private fun finishIntro() {
        val pv = introView ?: return
        handler.removeCallbacks(introTimeout)
        introView = null
        introPlayer?.release(); introPlayer = null
        root.removeView(pv)
        player?.volume = 1f
    }

    /** Açılış animasyonu: logo büyüyerek belirir, kısa süre kalır, sonra solarak kaybolur. */
    private fun showSplash() {
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            isClickable = true
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_utv)
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(120))
            alpha = 0f; scaleX = 0.6f; scaleY = 0.6f
        }
        val title = TextView(this).apply {
            text = "CNDN TV"
            setTextColor(Color.WHITE)
            textSize = 44f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = 30f
        }
        val sub = TextView(this).apply {
            text = "Canlı TV"
            setTextColor(accent)
            textSize = 20f
            gravity = Gravity.CENTER
            alpha = 0f
        }
        splash.addView(logo); splash.addView(title); splash.addView(sub)
        root.addView(splash, FrameLayout.LayoutParams(-1, -1))
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start()
        title.animate().alpha(1f).translationY(0f).setStartDelay(350).setDuration(600).start()
        sub.animate().alpha(1f).setStartDelay(800).setDuration(500).start()
        handler.postDelayed({
            splash.animate().alpha(0f).setDuration(500).withEndAction { root.removeView(splash) }.start()
        }, 2200)
    }

    private fun refreshPlaylist() {
        runOnWorker {
            val fresh = PlaylistLoader.download(this) ?: return@runOnWorker
            handler.post {
                if (isFinishing || isDestroyed) return@post
                val currentName = currentChannel.name
                val hadStream = ChannelCatalog.playableStream(currentChannel) != null
                ChannelCatalog.channels = fresh
                val idx = fresh.indexOfFirst { it.name == currentName }
                channelIndex = if (idx >= 0) idx else 0
                if (!hadStream || idx < 0) playCurrentChannel() else showChannelInfo()
                loadEpg() // yeni listedeki xmltvId'ler için EPG'yi (gerekirse) yeniden yükle
            }
        }
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
        // Kanal değişiminde kısa "parazit" geçişi: oynatıcının üstünde görünen gri katman.
        flash = View(this).apply { setBackgroundColor(Color.rgb(150, 150, 150)); visibility = View.GONE }
        root.addView(flash, FrameLayout.LayoutParams(-1, -1))
        statusText = text("CNDN TV · yayın kaynağı bekleniyor", 22f, Color.WHITE, true).apply {
            setBackgroundColor(Color.argb(190, 0, 0, 0)); setPadding(dp(14))
            visibility = View.VISIBLE
        }
        root.addView(statusText, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16)); setBackgroundColor(panel)
        }
        infoTitle = text("", 28f, Color.WHITE, true)
        infoSubtitle = text("", 18f, Color.LTGRAY, false)
        infoPanel.addView(infoTitle); infoPanel.addView(infoSubtitle)
        root.addView(infoPanel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        guidePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16)); setBackgroundColor(Color.argb(245, 8, 11, 16)); visibility = View.GONE
        }
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

    private fun playCurrentChannel(isRetry: Boolean = false) {
        handler.removeCallbacks(retryRunnable)
        handler.removeCallbacks(skipRunnable)
        if (!isRetry) retryCount = 0
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
            handlePlayerError()
        }
    }

    private fun showChannelInfo() {
        val c = currentChannel
        infoTitle.text = String.format(Locale.getDefault(), "%03d   %s", c.number, c.name)
        val now = System.currentTimeMillis()
        val cur = findProgram(c, now)
        val next = findNextProgram(c, now)
        infoSubtitle.text = if (cur != null) "${cur.title}   ·   Sonraki: ${next?.title ?: "—"}" else "Canlı   ·   Program bilgisi bulunamadı"
        infoPanel.visibility = View.VISIBLE
        overlayVisible = true
        resetIdleTimer()
    }

    private fun programsFor(c: TvChannel): List<Program>? = c.xmltvId?.lowercase(Locale.ROOT)?.let { epg[it] }
    private fun findProgram(c: TvChannel, now: Long): Program? = programsFor(c)?.firstOrNull { it.start <= now && it.end > now }
    private fun findNextProgram(c: TvChannel, now: Long): Program? = programsFor(c)?.firstOrNull { it.start > now }

    private fun cell(value: String, widthPx: Int, bold: Boolean): TextView =
        text(value, 20f, Color.WHITE, bold).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = if (widthPx > 0) LinearLayout.LayoutParams(widthPx, -2) else LinearLayout.LayoutParams(0, -2, 1f)
        }

    private fun openGuide() {
        guideVisible = true; overlayVisible = true
        guideRows.removeAllViews()
        val now = System.currentTimeMillis()
        ChannelCatalog.channels.forEachIndexed { index, c ->
            val selected = index == channelIndex
            val current = findProgram(c, now)?.title ?: "Canlı"
            val next = findNextProgram(c, now)?.title ?: "Program bilgisi yok"
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8))
                isFocusable = true; isFocusableInTouchMode = true
                setBackgroundColor(if (selected) Color.rgb(48, 56, 70) else Color.TRANSPARENT)
                addView(cell(String.format(Locale.getDefault(), "%03d", c.number), dp(70), selected))
                addView(cell(c.name, dp(220), selected))
                addView(cell(current, 0, false))
                addView(cell("→  $next", 0, false))
                setOnClickListener { channelIndex = index; skipChain = 0; closeGuide(); playCurrentChannel() }
                setOnFocusChangeListener { v, hasFocus -> v.setBackgroundColor(if (hasFocus) Color.rgb(48, 56, 70) else Color.TRANSPARENT) }
            }
            guideRows.addView(row, LinearLayout.LayoutParams(-1, dp(52)))
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
        val size = ChannelCatalog.channels.size
        channelIndex = (channelIndex + delta + size) % size
        skipChain = 0
        showChannelInfo()
        // Kısa parazit geçişi: gri katman, ardından seçilen kanal. Hızlı basışlarda yalnızca sonuncusu oynatılır.
        flash.visibility = View.VISIBLE
        handler.removeCallbacks(changeRunnable)
        handler.postDelayed(changeRunnable, 110)
    }

    private fun commitNumber() {
        if (digitBuffer.isEmpty()) return
        val number = digitBuffer.toString().toIntOrNull()
        digitBuffer.clear()
        val found = ChannelCatalog.channels.indexOfFirst { it.number == number }
        if (found >= 0) { channelIndex = found; skipChain = 0; playCurrentChannel() }
        else { Toast.makeText(this, "Bu kanal numarası listede yok", Toast.LENGTH_SHORT).show(); showChannelInfo() }
    }

    /**
     * XMLTV'yi indirir; yalnızca katalogdaki xmltvId'lerle eşleşen kanalların (bitmemiş) programlarını tutar.
     * Böylece bellek/CPU kullanımı düşük kalır ve rehber her satırda tüm programları taramaz.
     */
    private fun loadEpg() {
        val wanted = ChannelCatalog.channels.mapNotNull { it.xmltvId?.lowercase(Locale.ROOT) }.toSet()
        if (wanted.isEmpty() || wanted == epgWanted) return
        if (!epgLoading.compareAndSet(false, true)) { epgReloadPending = true; return }
        runOnWorker {
            try {
                val collected = HashMap<String, MutableList<Program>>()
                val cutoff = System.currentTimeMillis() - 3 * 60 * 60 * 1000L // 3 saatten eski biten programlar atlanır
                val conn = (URL(ChannelCatalog.XMLTV_SOURCE_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000; readTimeout = 30000; requestMethod = "GET"
                    setRequestProperty("User-Agent", "CNDNTV/1.0 AndroidTV")
                }
                try {
                    conn.inputStream.use { input ->
                        val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(input, null) }
                        var channel: String? = null
                        var title = ""; var start = 0L; var end = 0L
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            if (event == XmlPullParser.START_TAG) {
                                when (parser.name) {
                                    "programme" -> {
                                        val ch = parser.getAttributeValue(null, "channel")?.lowercase(Locale.ROOT)
                                        if (ch != null && ch in wanted) {
                                            channel = ch
                                            start = parseXmltvTime(parser.getAttributeValue(null, "start"))
                                            end = parseXmltvTime(parser.getAttributeValue(null, "stop"))
                                            title = ""
                                        } else channel = null
                                    }
                                    "title" -> if (channel != null && title.isEmpty()) title = parser.nextText().trim()
                                }
                            } else if (event == XmlPullParser.END_TAG && parser.name == "programme") {
                                val ch = channel
                                if (ch != null && title.isNotBlank() && end > start && end > cutoff) {
                                    collected.getOrPut(ch) { mutableListOf() }.add(Program(ch, title, start, end))
                                }
                                channel = null
                            }
                            event = parser.next()
                        }
                    }
                } finally { conn.disconnect() }
                val sorted: Map<String, List<Program>> = collected.mapValues { (_, v) -> v.sortedBy { it.start } }
                handler.post {
                    epg = sorted
                    epgWanted = wanted
                    if (overlayVisible && !isFinishing) { if (guideVisible) openGuide() else showChannelInfo() }
                }
            } catch (_: Exception) {
                // Uygulama çevrimdışı kullanılabilir kalır; EPG yoksa “Canlı” gösterilir. Sonraki liste yenilemesinde tekrar denenir.
            } finally {
                epgLoading.set(false)
                handler.post { if (epgReloadPending) { epgReloadPending = false; loadEpg() } }
            }
        }
    }

    private fun parseXmltvTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            val parts = raw.trim().split(Regex("\\s+"))
            val zone = if (parts.size > 1) parts[1] else "+0000"
            val format = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            format.parse("${parts[0]} $zone")?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        if (introView != null) { finishIntro(); return true }
        if (guideVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { closeGuide(); return true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { resetIdleTimer(); return super.dispatchKeyEvent(event) }
            }
        }
        when (event.keyCode) {
            // CH+ / ↓ = sonraki kanal, CH- / ↑ = önceki kanal (README ile uyumlu)
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { changeChannel(1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_UP -> { changeChannel(-1); return true }
            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_G -> { openGuide(); return true }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_I -> { showChannelInfo(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { if (infoPanel.visibility != View.VISIBLE) showChannelInfo() else openGuide(); return true }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                // Bilgi bandı açıksa önce onu kapat; kapalıysa normal geri davranışı (uygulamadan çıkış).
                if (infoPanel.visibility == View.VISIBLE) { hideOverlays(); return true }
                return super.dispatchKeyEvent(event)
            }
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9 -> {
                if (digitBuffer.length >= 4) digitBuffer.clear()
                digitBuffer.append(event.keyCode - KeyEvent.KEYCODE_0)
                infoTitle.text = "Kanal numarası: $digitBuffer"
                infoSubtitle.text = "Numara girişi"
                infoPanel.visibility = View.VISIBLE; overlayVisible = true
                handler.removeCallbacks(numberCommit); handler.postDelayed(numberCommit, 1200)
                resetIdleTimer(); return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                // Zaman kaydırma bilerek kapalı; canlı konum korunur.
                player?.seekToDefaultPosition(); player?.playWhenReady = true; resetIdleTimer(); return true
            }
        }
        resetIdleTimer()
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onStart() { super.onStart(); if (player != null && ChannelCatalog.playableStream(currentChannel) != null) player?.play() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        introPlayer?.release(); introPlayer = null
        player?.release(); player = null
        super.onDestroy()
    }

    private companion object { const val MAX_RETRY = 3 }
}
