package com.uydutv

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.TextureView
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
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
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

    // Bölünmüş ekran (sol: kanal listesi, sağ: önizleme)
    private var loadedId: String? = null       // şu an oynatıcıya yüklenmiş kanalın kimliği
    private var guideOriginId: String? = null  // liste açılmadan önce izlenen kanal (GERİ ile dönülür)
    private lateinit var guideTitle: TextView
    private var lastOkAt = 0L
    private val previewRunnable = Runnable {
        if (guideVisible && channelIndex in ChannelCatalog.channels.indices && currentChannel.id != loadedId) { skipChain = 0; playCurrentChannel() }
    }

    private val bg = Color.rgb(5, 10, 19)
    private val panel = Color.argb(242, 7, 16, 29)
    private val accent = Color.rgb(0, 174, 255)
    private val selectedRow = Color.rgb(8, 61, 96)
    private val mutedText = Color.rgb(166, 184, 207)

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
        player = buildPlayer().also { exo ->
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
        showIntro() // oynatıcı hazır olduktan sonra: kanal sesi video sırasında kapatılabilsin
        PlaylistLoader.loadCached(this)?.let { ChannelCatalog.setAll(it) }
        val saved = getSharedPreferences("uydutv", MODE_PRIVATE).getInt("last_channel", 1)
        channelIndex = ChannelCatalog.channels.indexOfFirst { it.number == saved }.coerceAtLeast(0)
        playCurrentChannel()
        refreshPlaylist()
        loadEpg()
        resetIdleTimer()
    }

    /** Yüksek görüntü kalitesi: her zaman mevcut en yüksek çözünürlük/bit hızı, yüksek başlangıç bant genişliği tahmini, geniş tampon. */
    private fun buildPlayer(): ExoPlayer {
        val selector = DefaultTrackSelector(this).also {
            it.setParameters(it.buildUponParameters().setForceHighestSupportedBitrate(true))
        }
        val meter = DefaultBandwidthMeter.Builder(this).setInitialBitrateEstimate(30_000_000L).build()
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 40_000, 2_000, 4_000).build()
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(this, renderers)
            .setTrackSelector(selector)
            .setBandwidthMeter(meter)
            .setLoadControl(load)
            .build()
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

    private var introView: View? = null
    private var introPlayer: ExoPlayer? = null
    private val introTimeout = Runnable { finishIntro() }

    /**
     * Açılış videosu: app/src/main/res/raw/intro.mp4 varsa tam ekran oynatılır (kumandadan herhangi bir tuşla geçilir).
     * Dosya yoksa ya da video oynatılamazsa logo animasyonu gösterilir.
     * Video, kanal görüntüsünün (SurfaceView) her zaman ÜSTÜNDE görünsün diye TextureView üzerinde oynatılır.
     */
    private fun showIntro() {
        val resId = resources.getIdentifier("intro", "raw", packageName)
        if (resId == 0) { showSplash(); return }
        player?.volume = 0f // video sırasında kanal sesi kapalı
        val box = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); isClickable = true }
        val tv = TextureView(this)
        box.addView(tv, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        val p = ExoPlayer.Builder(this).build()
        p.setVideoTextureView(tv)
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) finishIntro()
            }
            override fun onPlayerError(error: PlaybackException) {
                finishIntro(); showSplash() // video açılamazsa logo animasyonuna dön
            }
            override fun onVideoSizeChanged(size: VideoSize) {
                val rw = root.width; val rh = root.height
                if (size.width <= 0 || size.height <= 0 || rw <= 0 || rh <= 0) return
                val aspect = size.width * size.pixelWidthHeightRatio / size.height
                var w = rw; var h = (rw / aspect).toInt()
                if (h > rh) { h = rh; w = (rh * aspect).toInt() }
                tv.layoutParams = FrameLayout.LayoutParams(w, h, Gravity.CENTER)
            }
        })
        p.setMediaItem(MediaItem.fromUri("android.resource://$packageName/$resId"))
        p.prepare()
        p.playWhenReady = true
        root.addView(box, FrameLayout.LayoutParams(-1, -1))
        introView = box
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
                ChannelCatalog.setAll(fresh)
                val list = ChannelCatalog.channels
                val idx = list.indexOfFirst { it.name == currentName }
                channelIndex = if (idx >= 0) idx else 0
                if (idx >= 0) loadedId = list[idx].id
                if (!hadStream || idx < 0) playCurrentChannel() else showChannelInfo()
                loadEpg() // yeni listedeki xmltvId'ler için EPG'yi (gerekirse) yeniden yükle
            }
        }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusableInTouchMode = true
        }
        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        root.addView(playerView)
        flash = View(this).apply {
            setBackgroundColor(Color.argb(95, 0, 0, 0))
            visibility = View.GONE
        }
        root.addView(flash, FrameLayout.LayoutParams(-1, -1))
        statusText = text("CNDN TV  ·  Yayın kaynağı bekleniyor", 22f, Color.WHITE, true).apply {
            setBackgroundColor(Color.argb(220, 7, 16, 29))
            setPadding(dp(22), dp(16), dp(22), dp(16))
            visibility = View.VISIBLE
        }
        root.addView(statusText, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(34), dp(20), dp(34), dp(22))
            setBackgroundColor(Color.argb(225, 5, 12, 23))
        }
        infoTitle = text("", 30f, Color.WHITE, true)
        infoSubtitle = text("", 18f, mutedText, false)
        infoPanel.addView(infoTitle)
        infoPanel.addView(infoSubtitle)
        root.addView(infoPanel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        guidePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(18), dp(20))
            setBackgroundColor(Color.argb(248, 5, 13, 25))
            visibility = View.GONE
            elevation = dp(18).toFloat()
        }
        guidePanel.addView(text("CNDN  |  IPTV", 17f, accent, true).apply {
            letterSpacing = 0.12f
            setPadding(dp(10), dp(4), dp(10), dp(16))
        })
        guideTitle = text("KANALLAR", 27f, Color.WHITE, true).apply {
            setPadding(dp(10), dp(6), dp(10), dp(10))
        }
        guidePanel.addView(guideTitle)
        guidePanel.addView(text("◂  Kategori değiştir   ·   OK: İzle   ·   GERİ: Kapat", 14f, mutedText, false).apply {
            setPadding(dp(10), 0, dp(10), dp(18))
        })
        val scroller = ScrollView(this).apply { clipToPadding = false }
        guideRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroller.addView(guideRows)
        guidePanel.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(guidePanel, FrameLayout.LayoutParams(-1, -1, Gravity.START))
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
        loadedId = channel.id
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

    /** Bölünmüş ekran: sol %38 kanal listesi, sağda 16:9 önizleme + altında kanal bilgisi. */
    private fun applyLayout(split: Boolean) {
        val w = resources.displayMetrics.widthPixels
        if (split) {
            val pw = (w * 0.56f).toInt()
            val ph = pw * 9 / 16
            playerView.layoutParams = FrameLayout.LayoutParams(pw, ph, Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(24) }
            infoPanel.layoutParams = FrameLayout.LayoutParams(pw, -2, Gravity.END or Gravity.BOTTOM).apply { marginEnd = dp(24); bottomMargin = dp(36) }
            guidePanel.layoutParams = FrameLayout.LayoutParams((w * 0.38f).toInt(), -1, Gravity.START)
            statusText.layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(24) }
            statusText.maxWidth = pw - dp(16)
        } else {
            playerView.layoutParams = FrameLayout.LayoutParams(-1, -1)
            infoPanel.layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
            statusText.layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)
            statusText.maxWidth = Int.MAX_VALUE
        }
    }

    private fun openGuide() {
        if (!guideVisible) guideOriginId = currentChannel.id
        guideVisible = true; overlayVisible = true
        applyLayout(true)
        guideRows.removeAllViews()
        guideTitle.text = "◂  ${ChannelCatalog.categoryLabel(ChannelCatalog.category)}  ▸   (${ChannelCatalog.channels.size})"
        val now = System.currentTimeMillis()
        ChannelCatalog.channels.forEachIndexed { index, c ->
            val selected = index == channelIndex
            val current = findProgram(c, now)?.title
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(6), dp(10), dp(6))
                isFocusable = true; isFocusableInTouchMode = true
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (selected) selectedRow else Color.TRANSPARENT)
                    if (selected) setStroke(dp(2), accent)
                }
                addView(cell(String.format(Locale.getDefault(), "%03d", c.number), dp(64), selected))
                val col = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                col.addView(text(c.name, 20f, Color.WHITE, selected).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                if (current != null) col.addView(text(current, 14f, Color.LTGRAY, false).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                addView(col)
                setOnClickListener { channelIndex = index; skipChain = 0; closeGuide(commit = true) }
                setOnFocusChangeListener { v, hasFocus ->
                    v.background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(if (hasFocus) selectedRow else Color.TRANSPARENT)
                        if (hasFocus) setStroke(dp(2), accent)
                    }
                    if (hasFocus) {
                        channelIndex = index
                        showChannelInfo()
                        handler.removeCallbacks(previewRunnable)
                        handler.postDelayed(previewRunnable, 500) // seçili kanal sağda önizlenir
                    }
                }
            }
            val rowParams = LinearLayout.LayoutParams(-1, dp(68))
            rowParams.bottomMargin = dp(5)
            guideRows.addView(row, rowParams)
        }
        guidePanel.visibility = View.VISIBLE
        guideRows.getChildAt(channelIndex)?.requestFocus()
        showChannelInfo()
    }

    private fun moveGuideFocus(delta: Int) {
        val n = guideRows.childCount
        if (n == 0) return
        guideRows.getChildAt(((channelIndex + delta) % n + n) % n)?.requestFocus()
    }

    /** Kategoriyi değiştirir (◂ ▸). Oynayan kanal yeni kategoride varsa seçili kalır, yoksa ilk kanal önizlenir. */
    private fun cycleCategory(delta: Int) {
        val cats = ChannelCatalog.categories()
        val cur = cats.indexOf(ChannelCatalog.category).coerceAtLeast(0)
        val next = cats[(cur + delta + cats.size) % cats.size]
        val playingId = loadedId
        ChannelCatalog.setCategory(next)
        val idx = ChannelCatalog.channels.indexOfFirst { it.id == playingId }
        channelIndex = if (idx >= 0) idx else 0
        openGuide()
    }

    /** commit=true: seçili kanalda tam ekrana geç. commit=false (GERİ): liste açılmadan önceki kanala dön. */
    private fun closeGuide(commit: Boolean) {
        handler.removeCallbacks(previewRunnable)
        guideVisible = false
        guidePanel.visibility = View.GONE
        applyLayout(false)
        if (!commit) {
            val id = guideOriginId
            var idx = ChannelCatalog.channels.indexOfFirst { it.id == id }
            if (idx < 0) { ChannelCatalog.setCategory(null); idx = ChannelCatalog.channels.indexOfFirst { it.id == id } }
            channelIndex = idx.coerceAtLeast(0)
        }
        if (currentChannel.id != loadedId) playCurrentChannel() else showChannelInfo()
    }

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
        var found = ChannelCatalog.channels.indexOfFirst { it.number == number }
        if (found < 0 && ChannelCatalog.all.any { it.number == number }) {
            ChannelCatalog.setCategory(null) // numara başka kategoride: tüm kanallara dön
            found = ChannelCatalog.channels.indexOfFirst { it.number == number }
        }
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
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { closeGuide(commit = false); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { cycleCategory(-1); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { cycleCategory(1); return true }
                KeyEvent.KEYCODE_CHANNEL_UP -> { moveGuideFocus(-1); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> { moveGuideFocus(1); return true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { resetIdleTimer(); return super.dispatchKeyEvent(event) }
            }
        }
        when (event.keyCode) {
            // CH+ / ↓ = sonraki kanal, CH- / ↑ = önceki kanal (README ile uyumlu)
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { changeChannel(1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_UP -> { changeChannel(-1); return true }
            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_G -> { openGuide(); return true }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_I -> { showChannelInfo(); return true }
            // OK: 1 kez = kanal bilgisi, 2 kez (2 sn içinde) = sol kanal listesi + sağ önizleme
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                val now = SystemClock.uptimeMillis()
                if (now - lastOkAt < 2000) openGuide() else showChannelInfo()
                lastOkAt = now
                return true
            }
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
