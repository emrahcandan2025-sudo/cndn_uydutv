package com.uydutv.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

/**
 * TV OLED Ekran Koruyucu & Canlı Radyo Ambiyansı:
 * Radyo yayını dinlerken televizyon panellerinde (özellikle OLED/QLED)
 * statik görüntü yanmasını (burn-in) önlemek için görseli yavaşça hareket ettirir,
 * ritmik ses dalgaları çizer ve zarif bir dijital radyo deneyimi sunar.
 */
class RadioScreensaverView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var stationName: String = "CNDN Radyo"
    private var stationFreq: String = "Canlı Yayın · HD Ses"

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#090D16") }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 16, 26, 44)
    }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
        color = Color.parseColor("#1E6ECD")
    }
    private val textTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(28)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val textSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DEEEA")
        textSize = sp(16)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val textClockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A0AEC0")
        textSize = sp(18)
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private var animPhase = 0f
    private var driftX = 0f
    private var driftY = 0f
    private var driftTargetX = 0f
    private var driftTargetY = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animPhase = (animPhase + 0.08f) % (2f * Math.PI.toFloat())
            // Yavaş OLED kayması (drift)
            driftX += (driftTargetX - driftX) * 0.02f
            driftY += (driftTargetY - driftY) * 0.02f
            invalidate()
        }
    }

    private val clockFormat = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
    private var lastDriftChange = 0L

    fun setStation(name: String, subtitle: String = "Canlı Radyo · HD Ses") {
        this.stationName = name
        this.stationFreq = subtitle
        invalidate()
    }

    fun start() {
        if (!animator.isRunning) animator.start()
    }

    fun stop() {
        if (animator.isRunning) animator.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#28D9C5")
        textSize = sp(11)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.16f
    }
    private val dotLivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981")
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Kozmik Sinematik Radyal Zemin
        val bgShader = android.graphics.RadialGradient(
            w / 2f, h * 0.45f, w * 0.75f,
            intArrayOf(Color.parseColor("#131E3A"), Color.parseColor("#080D1A"), Color.parseColor("#020409")),
            floatArrayOf(0f, 0.55f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        bgPaint.shader = bgShader
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Belirli aralıklarla OLED burn-in drift hedefini güncelle (-40dp ile +40dp arası)
        val now = System.currentTimeMillis()
        if (now - lastDriftChange > 12000L) {
            lastDriftChange = now
            driftTargetX = ((Math.random() - 0.5) * dp(80)).toFloat()
            driftTargetY = ((Math.random() - 0.5) * dp(50)).toFloat()
        }

        val centerX = w / 2f + driftX
        val centerY = h / 2f + driftY

        // 2. Canlı Radyo Kartı
        val cardW = dp(440).toFloat().coerceAtMost(w * 0.88f)
        val cardH = dp(250).toFloat()
        val cardRect = RectF(centerX - cardW / 2f, centerY - cardH / 2f, centerX + cardW / 2f, centerY + cardH / 2f)
        val corner = dp(24).toFloat()

        // Kartın arkasındaki yumuşak neon ambiyans aurası
        val auraShader = android.graphics.RadialGradient(
            centerX, centerY, cardW * 0.65f,
            intArrayOf(Color.argb(70, 41, 156, 255), Color.argb(25, 40, 217, 197), Color.TRANSPARENT),
            floatArrayOf(0f, 0.6f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        auraPaint.shader = auraShader
        canvas.drawCircle(centerX, centerY, cardW * 0.65f, auraPaint)

        // Kart Arka Planı (Derin Gece Mavisi Cam)
        val cardShader = android.graphics.LinearGradient(
            cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
            intArrayOf(Color.argb(235, 17, 27, 49), Color.argb(245, 9, 14, 26)),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        cardPaint.shader = cardShader
        canvas.drawRoundRect(cardRect, corner, corner, cardPaint)

        // Kartın kenarlığı (Elektrik Mavisi -> Turkuaz)
        val borderShader = android.graphics.LinearGradient(
            cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
            intArrayOf(Color.parseColor("#38BDF8"), Color.parseColor("#28D9C5")),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        cardBorderPaint.shader = borderShader
        cardBorderPaint.strokeWidth = dp(1.8f).toFloat()
        canvas.drawRoundRect(cardRect, corner, corner, cardBorderPaint)

        // 3. Üst Canlı Yayın & Ses Rozeti
        val badgeW = dp(170).toFloat()
        val badgeH = dp(22).toFloat()
        val badgeTop = cardRect.top + dp(18)
        val badgeRect = RectF(centerX - badgeW / 2f, badgeTop, centerX + badgeW / 2f, badgeTop + badgeH)
        badgeBgPaint.color = Color.argb(140, 16, 28, 50)
        canvas.drawRoundRect(badgeRect, dp(11).toFloat(), dp(11).toFloat(), badgeBgPaint)

        // Yeşil Canlı Noktası
        canvas.drawCircle(badgeRect.left + dp(14), badgeRect.centerY(), dp(3.5f).toFloat(), dotLivePaint)
        canvas.drawText("CANLI RADYO · HD SES", centerX + dp(6), badgeRect.centerY() + dp(4), badgeTextPaint)

        // 4. Ritmik Ses Dalgaları (24 Spektrum Çubuğu)
        val barCount = 24
        val barWidth = dp(7).toFloat()
        val barGap = dp(5).toFloat()
        val totalBarW = barCount * barWidth + (barCount - 1) * barGap
        val startBarX = centerX - totalBarW / 2f
        val waveBaseY = cardRect.top + dp(82)

        for (i in 0 until barCount) {
            val harmonic1 = sin((animPhase + i * 0.38f).toDouble()).toFloat()
            val harmonic2 = sin((animPhase * 1.5f + i * 0.65f).toDouble()).toFloat() * 0.4f
            val normHeight = (0.5f + 0.35f * harmonic1 + 0.15f * harmonic2).coerceIn(0.12f, 1.0f)
            val waveHeight = dp(12) + dp(38) * normHeight
            val bx = startBarX + i * (barWidth + barGap)
            val by = waveBaseY - waveHeight / 2f

            // Mavi -> Turkuaz -> Zümrüt Spektrumu
            val fraction = i.toFloat() / barCount.toFloat()
            val color = interpolateColor(Color.parseColor("#2563EB"), Color.parseColor("#2DD4BF"), fraction)
            wavePaint.color = color

            val barRect = RectF(bx, by, bx + barWidth, by + waveHeight)
            canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, wavePaint)
        }

        // 5. İstasyon Başlığı ve Frekans
        canvas.drawText(stationName, centerX, cardRect.top + dp(142), textTitlePaint)
        canvas.drawText("📻 $stationFreq", centerX, cardRect.top + dp(174), textSubtitlePaint)

        // 6. Dijital Saat ve OLED Bilgi Metni
        val timeStr = clockFormat.format(Date(now))
        canvas.drawText("$timeStr   ·   OLED Panel Koruma Aktif   ·   [OK: Kanallar]", centerX, cardRect.top + dp(216), textClockPaint)
    }

    private fun interpolateColor(a: Int, b: Int, proportion: Float): Int {
        val hs = FloatArray(3)
        val hs2 = FloatArray(3)
        Color.colorToHSV(a, hs)
        Color.colorToHSV(b, hs2)
        for (i in 0..2) {
            hs[i] = hs[i] + (hs2[i] - hs[i]) * proportion
        }
        return Color.HSVToColor(hs)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
    private fun sp(v: Int): Float = v * resources.displayMetrics.scaledDensity
}
