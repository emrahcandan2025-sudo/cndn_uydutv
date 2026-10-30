package com.uydutv

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
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
import androidx.media3.common.MimeTypes
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
import com.uydutv.data.ChannelOrder
import com.uydutv.data.PlaylistLoader
import com.uydutv.data.StreamHealthManager
import com.uydutv.data.TvChannel
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    private lateinit var guideTitle: TextView

    private var player: ExoPlayer? = null
    private var channelIndex = 0
    private var overlayVisible = false
    private var guideVisible = false
    private val currentChannel: TvChannel
        get() {
            val list = ChannelCatalog.channels
            return if (channelIndex in list.indices) list[channelIndex] else list.firstOrNull() ?: ChannelCatalog.builtIn.first()
        }

    // Çoklu kaynak ve açılış süresi takibi
    private var streamAttemptIndex = 0
    private var streamStartTime = 0L
    private var activePlayingStream: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val digitBuffer = StringBuilder()

    // EPG: xmltvId eşleştirmesi ile program listeleri
    @Volatile private var epg: Map<String, List<Program>> = emptyMap()
    private var epgWanted: Set<String> = emptySet()
    private var epgReloadPending = false
    private val epgLoading = AtomicBoolean(false)

    // Oynatma hatası yönetimi: sonsuz döngüyü önleyen kontrollü sınır
    private var retryCount = 0
    private var skipChain = 0

    private val idleHide = Runnable { hideOverlays() }
    private val numberCommit = Runnable { commitNumber() }
    private val retryRunnable = Runnable { playCurrentChannel(isRetry = true) }
    private val skipRunnable = Runnable {
        val total = ChannelCatalog.channels.size
        if (total > 0) {
            channelIndex = (channelIndex + 1) % total
            playCurrentChannel()
        }
    }
    private val changeRunnable = Runnable { flash.visibility = View.GONE; playCurrentChannel() }

    // Bölünmüş ekran durumu
    private var loadedId: String? = null
    private var guideOriginId: String? = null
    private var lastOkAt = 0L
    private val previewRunnable = Runnable {
        if (guideVisible && channelIndex in ChannelCatalog.channels.indices && currentChannel.id != loadedId) {
            skipChain = 0
            streamAttemptIndex = 0
            playCurrentChannel()
        }
    }

    private val bg = Color.rgb(5, 10, 19)
    private val accent = Color.rgb(0, 174, 255)
    private val selectedRow = Color.rgb(8, 61, 96)
    private val mutedText = Color.rgb(166, 184, 207)

    data class Program(val channelId: String, val title: String, val start: Long, val end: Long)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun runOnWorker(block: () -> Unit) {
        try { worker.execute(block) } catch (_: RejectedExecutionException) { /* activity kapanıyor */ }
    }

    private var splashView: com.uydutv.ui.CinematicSplashView? = null
    private val splashTimeout = Runnable { finishSplash() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        buildUi()
        initPlayer()
        showCinematicSplash()

        // 1. Önce cihazdaki kayıtlı önbelleği yükle (hızlı açılış)
        val cached = PlaylistLoader.loadCached(this)
        if (!cached.isNullOrEmpty()) {
            ChannelCatalog.setAll(cached)
        } else {
            ChannelCatalog.setAll(ChannelCatalog.builtIn)
        }

        // Son izlenen kanalı aç; yoksa ilk kanaldan başla
        val saved = getSharedPreferences("uydutv", MODE_PRIVATE).getInt("last_channel", 1)
        val idx = ChannelCatalog.channels.indexOfFirst { it.number == saved }
        channelIndex = if (idx >= 0) idx else 0

        playCurrentChannel()

        // 2. 6 saat kuralı: Yalnızca önbellek yoksa veya son güncellemeden 6 saat geçmişse indir
        val prefs = getSharedPreferences("uydutv", MODE_PRIVATE)
        val lastUpdate = prefs.getLong("last_playlist_update_time", 0L)
        val now = System.currentTimeMillis()
        val SIX_HOURS_MS = 6 * 60 * 60 * 1000L
        val shouldRefresh = (cached == null || cached.isEmpty() || (now - lastUpdate) > SIX_HOURS_MS)

        if (shouldRefresh) {
            refreshPlaylist()
        } else {
            Log.i("MainActivity", "Önbellek güncel (${(now - lastUpdate) / 60000} dk önce); 6 saatlik kural gereği ağ yenilemesi atlandı.")
        }
        loadEpg()
        resetIdleTimer()
    }

    /**
     * CNDN TV Özgün Sinematik Açılış Animasyonu:
     * Logo ışık izleriyle çizilir, antenler ve parlayan oynat simgesi belirir,
     * CNDN TV tipografisi parlar ve doğrudan canlı TV'ye geçilir.
     */
    private fun showCinematicSplash() {
        player?.volume = 0f
        val sv = com.uydutv.ui.CinematicSplashView(this) {
            finishSplash()
        }
        sv.setOnClickListener { finishSplash() }
        root.addView(sv, FrameLayout.LayoutParams(-1, -1))
        splashView = sv
        handler.postDelayed(splashTimeout, 4500)
    }

    private fun finishSplash() {
        val sv = splashView ?: return
        handler.removeCallbacks(splashTimeout)
        splashView = null
        sv.skip()
        root.removeView(sv)
        player?.volume = 1f
    }

    private fun initPlayer() {
        val exo = buildPlayer()
        playerView.player = exo
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val currentStream = activePlayingStream
                if (currentStream != null) {
                    StreamHealthManager.recordFailure(this@MainActivity, currentChannel.id, currentStream)
                }

                val streams = if (currentChannel.streamUrls.isNotEmpty()) currentChannel.streamUrls else listOfNotNull(currentChannel.streamUrl)
                if (streamAttemptIndex + 1 < streams.size) {
                    statusText.text = "Kaynak ${streamAttemptIndex + 1} yanıt vermedi · Alternatif deneniyor (${streamAttemptIndex + 2}/${streams.size})…"
                    statusText.visibility = View.VISIBLE
                    handler.postDelayed({
                        playCurrentChannel(tryNextStream = true)
                    }, 500)
                } else {
                    handlePlayerError(error)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val latency = if (streamStartTime > 0L) SystemClock.elapsedRealtime() - streamStartTime else null
                    streamStartTime = 0L
                    val currentStream = activePlayingStream
                    if (currentStream != null && latency != null) {
                        StreamHealthManager.recordSuccess(this@MainActivity, currentChannel.id, currentStream, latency)
                        ChannelCatalog.prioritizeStream(currentChannel.id, currentStream)
                        ChannelCatalog.updateChannelLatency(currentChannel.id, latency)
                    }
                    statusText.visibility = View.GONE
                    retryCount = 0
                    skipChain = 0
                    showChannelInfo(loadLatencyMs = latency)
                } else if (playbackState == Player.STATE_BUFFERING && ChannelCatalog.playableStream(currentChannel) != null) {
                    val totalStreams = currentChannel.streamUrls.size
                    statusText.text = if (totalStreams > 1) {
                        "Canlı yayın yükleniyor… (Kaynak ${streamAttemptIndex + 1}/$totalStreams)"
                    } else {
                        "Canlı yayın yükleniyor…"
                    }
                    statusText.visibility = View.VISIBLE
                }
            }
        })
        player = exo
    }

    /**
     * AndroidX Media3 ExoPlayer yapılandırması:
     * HLS ve DASH akışları için hızlı başlangıç ve uyumlu tampon ayarları.
     */
    private fun buildPlayer(): ExoPlayer {
        val selector = DefaultTrackSelector(this).also {
            it.setParameters(it.buildUponParameters().setForceHighestSupportedBitrate(true))
        }
        val meter = DefaultBandwidthMeter.Builder(this).setInitialBitrateEstimate(30_000_000L).build()
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(8_000, 30_000, 1_500, 3_000)
            .build()
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(this, renderers)
            .setTrackSelector(selector)
            .setBandwidthMeter(meter)
            .setLoadControl(load)
            .build()
    }

    /**
     * Oynatma hatası yönetimi:
     * - Aynı kanalı en fazla MAX_RETRY kez dene.
     * - Sonsuz döngüyü önlemek için ardışık kanal atlamasını MAX_SKIP_CHAIN (2) ile sınırla.
     * - Hata sebebini kullanıcıya anlaşılır şekilde göster.
     */
    private fun handlePlayerError(error: PlaybackException? = null) {
        handler.removeCallbacks(retryRunnable)
        handler.removeCallbacks(skipRunnable)
        retryCount++

        val total = ChannelCatalog.channels.size
        val reason = when (error?.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Bağlantı zaman aşımına uğradı"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Yayın sunucusuna ulaşılamadı"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Desteklenmeyen yayın biçimi"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Görüntü çözücü hatası"
            else -> "Yayın akışı kesildi"
        }

        when {
            retryCount <= MAX_RETRY -> {
                statusText.text = "$reason · Yeniden deneniyor ($retryCount/$MAX_RETRY)"
                handler.postDelayed(retryRunnable, 3000)
            }
            skipChain < MAX_SKIP_CHAIN && total > 1 -> {
                skipChain++
                statusText.text = "Bu kanal yanıt vermiyor · Sonraki kanala geçiliyor ($skipChain/$MAX_SKIP_CHAIN)"
                handler.postDelayed(skipRunnable, 1500)
            }
            else -> {
                statusText.text = "$reason\nYayın bağlantısı kurulamadı. Lütfen internetinizi kontrol edin veya kumanda ile başka bir kanal seçin."
            }
        }
        statusText.visibility = View.VISIBLE
    }

    /**
     * 3 M3U listesini arka planda indirir ve birleştirir.
     * İndirme başarılıysa o anda oynatılan kanalı korur, kataloğu günceller.
     * İndirme başarısızsa kullanıcıya bilgi verir, mevcut çalışan yayını bozmaz.
     */
    private fun refreshPlaylist() {
        runOnWorker {
            val fresh = PlaylistLoader.downloadAndMergeAll(this)
            handler.post {
                if (isFinishing || isDestroyed) return@post
                if (!fresh.isNullOrEmpty()) {
                    getSharedPreferences("uydutv", MODE_PRIVATE).edit()
                        .putLong("last_playlist_update_time", System.currentTimeMillis())
                        .apply()

                    val currentName = currentChannel.name
                    val currentId = currentChannel.id
                    val hadStream = ChannelCatalog.playableStream(currentChannel) != null

                    ChannelCatalog.setAll(fresh)
                    val list = ChannelCatalog.channels

                    // O anda izlenen kanalı yeni listede bul ve koru
                    val idx = list.indexOfFirst {
                        it.id == currentId || it.name.equals(currentName, ignoreCase = true)
                    }

                    if (idx >= 0) {
                        channelIndex = idx
                        loadedId = list[idx].id
                        if (!hadStream && ChannelCatalog.playableStream(list[idx]) != null) {
                            playCurrentChannel()
                        } else {
                            showChannelInfo()
                        }
                    } else {
                        channelIndex = 0
                        playCurrentChannel()
                    }

                    if (guideVisible) {
                        openGuide()
                    }
                    loadEpg()
                } else {
                    // İndirme başarısız oldu: mevcut liste korunur
                    Log.i("MainActivity", "M3U listeleri güncellenemedi; mevcut liste korunuyor.")
                    if (ChannelCatalog.all == ChannelCatalog.builtIn && ChannelCatalog.playableStream(currentChannel) == null) {
                        statusText.text = "Çevrimiçi kanal listesine ulaşılamadı.\nLütfen internet bağlantınızı kontrol edin."
                        statusText.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusableInTouchMode = true
            setOnClickListener {
                if (guideVisible) {
                    closeGuide(commit = false)
                } else {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastOkAt < 2000) openGuide() else showChannelInfo()
                    lastOkAt = now
                }
            }
        }
        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setOnClickListener {
                if (guideVisible) {
                    closeGuide(commit = true)
                } else {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastOkAt < 2000) openGuide() else showChannelInfo()
                    lastOkAt = now
                }
            }
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
            setOnClickListener { openGuide() }
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
            isClickable = true
            setOnClickListener { cycleCategory(1) }
        }
        guidePanel.addView(guideTitle)
        guidePanel.addView(text("◂  Kategori değiştir   ·   OK: İzle   ·   GERİ: Kapat", 14f, mutedText, false).apply {
            setPadding(dp(10), 0, dp(10), dp(18))
            isClickable = true
            setOnClickListener { cycleCategory(1) }
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

    /**
     * Kanala ait yayını başlatır:
     * - Aynı kanalın birden fazla akış adresi varsa hızlı olanı önce dener.
     * - Sırayla dener; ilk açılanı kullanır.
     * - Açılış süresini (gecikmesini) kaydeder.
     */
    private fun playCurrentChannel(isRetry: Boolean = false, tryNextStream: Boolean = false) {
        handler.removeCallbacks(retryRunnable)
        handler.removeCallbacks(skipRunnable)
        if (!isRetry && !tryNextStream) {
            retryCount = 0
            streamAttemptIndex = 0
        } else if (tryNextStream) {
            streamAttemptIndex++
        }

        val channel = currentChannel
        loadedId = channel.id
        getSharedPreferences("uydutv", MODE_PRIVATE).edit().putInt("last_channel", channel.number).apply()

        val streams = if (channel.streamUrls.isNotEmpty()) channel.streamUrls else listOfNotNull(channel.streamUrl)

        if (streams.isEmpty() || streamAttemptIndex >= streams.size) {
            player?.stop()
            player?.clearMediaItems()
            if (streams.isEmpty()) {
                statusText.text = "${channel.name}\nDoğrudan yayın adresi ve kullanım izni doğrulanmadı.\nKanal kaynağı yapılandırıldığında canlı yayın başlayacak."
                statusText.visibility = View.VISIBLE
            } else {
                handlePlayerError()
            }
            showChannelInfo()
            return
        }

        val stream = streams[streamAttemptIndex]
        activePlayingStream = stream
        streamStartTime = SystemClock.elapsedRealtime()

        val totalStreams = streams.size
        statusText.text = if (totalStreams > 1) {
            "Canlı yayın yükleniyor… (Kaynak ${streamAttemptIndex + 1}/$totalStreams)"
        } else {
            "Canlı yayın yükleniyor…"
        }
        statusText.visibility = View.VISIBLE
        showChannelInfo()

        try {
            val p = player ?: return
            p.stop()
            p.clearMediaItems()

            // HLS veya DASH akışı tespiti
            val mediaItem = if (stream.contains(".m3u8", ignoreCase = true) || stream.contains("m3u8", ignoreCase = true) || stream.contains("hls", ignoreCase = true)) {
                MediaItem.Builder()
                    .setUri(stream)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
            } else if (stream.contains(".mpd", ignoreCase = true)) {
                MediaItem.Builder()
                    .setUri(stream)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
            } else {
                MediaItem.fromUri(stream)
            }

            p.setMediaItem(mediaItem)
            p.prepare()
            p.playWhenReady = true
        } catch (_: Exception) {
            handlePlayerError()
        }
    }

    private fun showChannelInfo(loadLatencyMs: Long? = null) {
        val c = currentChannel
        val latency = loadLatencyMs ?: c.lastStartupLatencyMs ?: StreamHealthManager.getChannelLatency(this, c.id)
        val latencyStr = if (latency != null && latency > 0) "   ⚡ ${latency}ms" else ""
        val totalStreams = c.streamUrls.size
        val streamStr = if (totalStreams > 1) "   [K.${streamAttemptIndex + 1}/$totalStreams]" else ""

        infoTitle.text = String.format(Locale.getDefault(), "%03d   %s%s", c.number, c.name, streamStr)
        val now = System.currentTimeMillis()
        val cur = findProgram(c, now)
        val next = findNextProgram(c, now)
        infoSubtitle.text = if (cur != null) {
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val curTime = "${timeFmt.format(Date(cur.start))} - ${timeFmt.format(Date(cur.end))}"
            "${cur.title} ($curTime)$latencyStr   ·   Sonraki: ${next?.title ?: "—"}"
        } else {
            "Canlı Yayın$latencyStr   ·   Program bilgisi bulunamadı"
        }
        infoPanel.visibility = View.VISIBLE
        overlayVisible = true
        resetIdleTimer()
    }

    private fun programsFor(c: TvChannel): List<Program>? {
        val xmltv = c.xmltvId?.lowercase(Locale.ROOT)
        if (xmltv != null && epg.containsKey(xmltv)) return epg[xmltv]
        val cleanName = ChannelOrder.normalize(c.name)
        return epg[cleanName] ?: epg[c.id.lowercase(Locale.ROOT)]
    }

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
            val latency = c.lastStartupLatencyMs ?: StreamHealthManager.getChannelLatency(this, c.id)
            val streamCount = c.streamUrls.size
            val badge = buildString {
                if (latency != null && latency > 0) append("⚡ ${latency}ms")
                if (streamCount > 1) {
                    if (isNotEmpty()) append(" · ")
                    append("$streamCount kaynak")
                }
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(6), dp(10), dp(6))
                isFocusable = true; isFocusableInTouchMode = true
                background = GradientDrawable().apply {
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
                val subText = when {
                    current != null && badge.isNotEmpty() -> "$current  ·  $badge"
                    current != null -> current
                    badge.isNotEmpty() -> badge
                    else -> null
                }
                if (subText != null) {
                    col.addView(text(subText, 14f, Color.LTGRAY, false).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                }
                addView(col)
                setOnClickListener { channelIndex = index; skipChain = 0; streamAttemptIndex = 0; closeGuide(commit = true) }
                setOnFocusChangeListener { v, hasFocus ->
                    v.background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(if (hasFocus) selectedRow else Color.TRANSPARENT)
                        if (hasFocus) setStroke(dp(2), accent)
                    }
                    if (hasFocus) {
                        channelIndex = index
                        streamAttemptIndex = 0
                        showChannelInfo()
                        handler.removeCallbacks(previewRunnable)
                        handler.postDelayed(previewRunnable, 500)
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

    /** Kategoriyi değiştirir (◂ ▸). Oynayan kanal yeni kategoride varsa seçili kalır. */
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

    /** commit=true: seçili kanalda tam ekrana geç. commit=false (GERİ): önceki kanala dön. */
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
        if (currentChannel.id != loadedId) {
            streamAttemptIndex = 0
            playCurrentChannel()
        } else {
            showChannelInfo()
        }
    }

    private fun hideOverlays() { if (!guideVisible) { infoPanel.visibility = View.GONE; overlayVisible = false } }
    private fun resetIdleTimer() { handler.removeCallbacks(idleHide); handler.postDelayed(idleHide, 5000) }

    private fun changeChannel(delta: Int) {
        val size = ChannelCatalog.channels.size
        if (size == 0) return
        channelIndex = (channelIndex + delta + size) % size
        skipChain = 0
        streamAttemptIndex = 0
        showChannelInfo()
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
            ChannelCatalog.setCategory(null)
            found = ChannelCatalog.channels.indexOfFirst { it.number == number }
        }
        if (found >= 0) {
            channelIndex = found
            skipChain = 0
            streamAttemptIndex = 0
            playCurrentChannel()
        } else {
            Toast.makeText(this, "Bu kanal numarası listede yok", Toast.LENGTH_SHORT).show()
            showChannelInfo()
        }
    }

    /**
     * XMLTV'yi indirir ve önbelleğe alır.
     */
    private fun loadEpg() {
        val wanted = ChannelCatalog.channels.mapNotNull { it.xmltvId?.lowercase(Locale.ROOT) }.toSet()
        if (wanted.isEmpty() || wanted == epgWanted) return
        if (!epgLoading.compareAndSet(false, true)) { epgReloadPending = true; return }
        runOnWorker {
            try {
                val collected = HashMap<String, MutableList<Program>>()
                val cutoff = System.currentTimeMillis() - 3 * 60 * 60 * 1000L
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
                // Çevrimdışı durumda canlı yayın aksamaz
            } finally {
                epgLoading.set(false)
                handler.post { if (epgReloadPending) { epgReloadPending = false; loadEpg() } }
            }
        }
    }

    private fun parseXmltvTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            val trimmed = raw.trim()
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size > 1) {
                val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
                format.parse("${parts[0]} ${parts[1]}")?.time ?: 0L
            } else if (trimmed.length >= 14) {
                val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                format.parse(trimmed.substring(0, 14))?.time ?: 0L
            } else 0L
        } catch (_: Exception) { 0L }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        if (splashView != null) { finishSplash(); return true }
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
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { changeChannel(1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_UP -> { changeChannel(-1); return true }
            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_G -> { openGuide(); return true }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_I -> { showChannelInfo(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                val now = SystemClock.uptimeMillis()
                if (now - lastOkAt < 2000) openGuide() else showChannelInfo()
                lastOkAt = now
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
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
                player?.seekToDefaultPosition(); player?.playWhenReady = true; resetIdleTimer(); return true
            }
        }
        resetIdleTimer()
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (guideVisible) {
            closeGuide(commit = false)
        } else if (infoPanel.visibility == View.VISIBLE) {
            hideOverlays()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onStart() { super.onStart(); if (player != null && ChannelCatalog.playableStream(currentChannel) != null) player?.play() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        splashView?.skip()
        splashView = null
        player?.release(); player = null
        super.onDestroy()
    }

    private companion object {
        const val MAX_RETRY = 3
        const val MAX_SKIP_CHAIN = 2
    }
}
