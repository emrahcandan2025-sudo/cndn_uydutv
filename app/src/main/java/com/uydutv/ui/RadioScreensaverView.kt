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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Koyu Sinematik Zemin
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
        val cardW = dp(420).toFloat().coerceAtMost(w * 0.85f)
        val cardH = dp(230).toFloat()
        val cardRect = RectF(centerX - cardW / 2f, centerY - cardH / 2f, centerX + cardW / 2f, centerY + cardH / 2f)
        val corner = dp(24).toFloat()

        canvas.drawRoundRect(cardRect, corner, corner, cardPaint)
        canvas.drawRoundRect(cardRect, corner, corner, cardBorderPaint)

        // 3. Ritmik Ses Dalgaları (Spektrum Çubukları)
        val barCount = 18
        val barWidth = dp(8).toFloat()
        val barGap = dp(6).toFloat()
        val totalBarW = barCount * barWidth + (barCount - 1) * barGap
        val startBarX = centerX - totalBarW / 2f
        val waveBaseY = cardRect.top + dp(62)

        for (i in 0 until barCount) {
            val waveHeight = dp(10) + dp(32) * (0.5f + 0.5f * sin((animPhase + i * 0.45f).toDouble()).toFloat())
            val bx = startBarX + i * (barWidth + barGap)
            val by = waveBaseY - waveHeight / 2f

            // Mavi-Turkuaz gradient tonu
            val fraction = i.toFloat() / barCount.toFloat()
            val color = interpolateColor(Color.parseColor("#1E6ECD"), Color.parseColor("#4DEEEA"), fraction)
            wavePaint.color = color

            val barRect = RectF(bx, by, bx + barWidth, by + waveHeight)
            canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, wavePaint)
        }

        // 4. İstasyon Başlığı ve Frekans
        canvas.drawText(stationName, centerX, cardRect.top + dp(120), textTitlePaint)
        canvas.drawText("📻 $stationFreq", centerX, cardRect.top + dp(154), textSubtitlePaint)

        // 5. Dijital Saat ve Bilgi
        val timeStr = clockFormat.format(Date(now))
        canvas.drawText("$timeStr  ·  Ekran Koruyucu Aktif (Tuşa basın)", centerX, cardRect.top + dp(195), textClockPaint)
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
    private fun sp(v: Int): Float = v * resources.displayMetrics.scaledDensity
}
