package com.uydutv.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.uydutv.R

/**
 * CNDN TV Sinematik Açılış Animasyonu
 *
 * 7 aşamalı akış:
 * 1. Koyu lacivert/siyah arka plan (#060813).
 * 2. Merkezde yumuşak ışık parıltısı.
 * 3. Turuncu ve mavi kıvrımlı ışık izlerinin televizyon çerçevesini çizmesi.
 * 4. Antenlerin zarif ışık efektleriyle yükselmesi.
 * 5. Merkezdeki turuncu-sarı oynat simgesinin parlaması.
 * 6. "CNDN TV" tipografisinin ışık geçişiyle aydınlanması.
 * 7. Logonun kısa süre sabit kalması ve canlı TV ekranına yumuşak geçiş.
 */
class CinematicSplashView(
    context: Context,
    private val onFinished: () -> Unit
) : View(context) {

    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var isCompleted = false

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#060813")
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ribbonPaintOrange = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val ribbonPaintBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val antennaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val antennaTipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val playPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }

    private val textPaintOrange = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val logoBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val tvFramePath = Path()
    private val tvMeasure = PathMeasure()
    private var logoBitmap: Bitmap? = null

    init {
        isClickable = true
        isFocusable = true
        loadLogoBitmap()
        startAnimation()
    }

    private fun loadLogoBitmap() {
        try {
            val resId = R.drawable.cndn_logo
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            logoBitmap = BitmapFactory.decodeResource(resources, resId, opts)
        } catch (_: Exception) {
            // Bitmap yüklenemezse tam vektörel çizim çalışır
        }
    }

    private fun startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3600L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                progress = va.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finishOnce()
                }
            })
            start()
        }
    }

    fun skip() {
        if (!isCompleted) {
            animator?.cancel()
            finishOnce()
        }
    }

    private fun finishOnce() {
        if (!isCompleted) {
            isCompleted = true
            animate().alpha(0f).setDuration(350).withEndAction {
                onFinished()
            }.start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        logoBitmap?.recycle()
        logoBitmap = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Koyu arka plan
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val cx = w / 2f
        val cy = h / 2f - (h * 0.04f)
        val scale = Math.min(w, h) / 600f

        // AŞAMA 1: Merkezde yumuşak ışık parıltısı (0.00..0.35)
        if (progress > 0.02f) {
            val glowAlpha = when {
                progress < 0.25f -> ((progress - 0.02f) / 0.23f)
                progress < 0.85f -> 1.0f
                else -> (1.0f - (progress - 0.85f) / 0.15f)
            }
            val glowRadius = (160f * scale) * (0.6f + progress * 0.4f)
            glowPaint.shader = RadialGradient(
                cx, cy, glowRadius,
                intArrayOf(
                    Color.argb((70 * glowAlpha).toInt(), 255, 150, 0),
                    Color.argb((35 * glowAlpha).toInt(), 0, 180, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, glowRadius, glowPaint)
        }

        val tvWidth = 260f * scale
        val tvHeight = 190f * scale
        val strokeW = 18f * scale

        // AŞAMA 2: Çerçevenin ışık izleriyle çizilmesi (0.10..0.65)
        val frameProgress = ((progress - 0.10f) / 0.50f).coerceIn(0f, 1f)
        if (frameProgress > 0f) {
            val rect = RectF(cx - tvWidth / 2f, cy - tvHeight / 2f, cx + tvWidth / 2f, cy + tvHeight / 2f)
            tvFramePath.reset()
            tvFramePath.addRoundRect(rect, 48f * scale, 48f * scale, Path.Direction.CW)
            tvMeasure.setPath(tvFramePath, false)
            val totalLength = tvMeasure.length

            val currentLength = totalLength * frameProgress
            val partialPath = Path()
            tvMeasure.getSegment(0f, currentLength, partialPath, true)

            // Turuncu ve mavi çift gradyan şeritler
            ribbonPaintOrange.strokeWidth = strokeW
            ribbonPaintOrange.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.top,
                Color.parseColor("#FF6D00"), Color.parseColor("#FFD54F"),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(partialPath, ribbonPaintOrange)

            // Alt kıvrımda mavi / camgöbeği ışıltı
            ribbonPaintBlue.strokeWidth = strokeW * 0.9f
            ribbonPaintBlue.shader = LinearGradient(
                rect.left, rect.bottom, rect.right, rect.bottom,
                Color.parseColor("#00E5FF"), Color.parseColor("#2979FF"),
                Shader.TileMode.CLAMP
            )
            val blueSegment = Path()
            val blueStart = (totalLength * 0.45f).coerceAtMost(currentLength)
            if (currentLength > blueStart) {
                tvMeasure.getSegment(blueStart, currentLength, blueSegment, true)
                canvas.drawPath(blueSegment, ribbonPaintBlue)
            }
        }

        // AŞAMA 3: Antenlerin belirmesi (0.35..0.70)
        val antennaProgress = ((progress - 0.35f) / 0.30f).coerceIn(0f, 1f)
        if (antennaProgress > 0f) {
            val antY = cy - tvHeight / 2f
            val antLen = 58f * scale * antennaProgress
            antennaPaint.strokeWidth = 6f * scale

            // Sol anten
            antennaPaint.color = Color.parseColor("#FFA000")
            val leftEndX = cx - 18f * scale - (antLen * 0.65f)
            val leftEndY = antY - (antLen * 0.75f)
            canvas.drawLine(cx - 18f * scale, antY, leftEndX, leftEndY, antennaPaint)

            // Sağ anten
            antennaPaint.color = Color.parseColor("#00E5FF")
            val rightEndX = cx + 18f * scale + (antLen * 0.65f)
            val rightEndY = antY - (antLen * 0.75f)
            canvas.drawLine(cx + 18f * scale, antY, rightEndX, rightEndY, antennaPaint)

            // Parlayan anten uçları
            if (antennaProgress > 0.5f) {
                val tipAlpha = ((antennaProgress - 0.5f) / 0.5f).coerceIn(0f, 1f)
                val tipRadius = 9f * scale * tipAlpha
                antennaTipPaint.color = Color.argb((255 * tipAlpha).toInt(), 255, 170, 0)
                canvas.drawCircle(leftEndX, leftEndY, tipRadius, antennaTipPaint)
                antennaTipPaint.color = Color.argb((255 * tipAlpha).toInt(), 0, 230, 255)
                canvas.drawCircle(rightEndX, rightEndY, tipRadius, antennaTipPaint)
            }
        }

        // AŞAMA 4: Merkezdeki parlayan oynat simgesi (0.45..0.75)
        val playProgress = ((progress - 0.45f) / 0.25f).coerceIn(0f, 1f)
        if (playProgress > 0f) {
            val playScale = (0.3f + 0.7f * playProgress) * (if (playProgress < 0.9f) 1.05f else 1.0f)
            val pSize = 42f * scale * playScale
            val playPath = Path().apply {
                moveTo(cx - pSize * 0.6f, cy - pSize)
                lineTo(cx + pSize, cy)
                lineTo(cx - pSize * 0.6f, cy + pSize)
                close()
            }
            playPaint.shader = LinearGradient(
                cx - pSize, cy - pSize, cx + pSize, cy + pSize,
                Color.parseColor("#FFD54F"), Color.parseColor("#FF6D00"),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(playPath, playPaint)
        }

        // AŞAMA 5 & 6: Sinematik 3D Logo ve "CNDN TV" Tipografisi (0.65..1.00)
        val fullLogoAlpha = ((progress - 0.65f) / 0.20f).coerceIn(0f, 1f)
        if (fullLogoAlpha > 0f) {
            val b = logoBitmap
            if (b != null && !b.isRecycled) {
                val dstSize = 340f * scale
                val dstRect = RectF(cx - dstSize / 2f, cy - dstSize / 2f - 18f * scale, cx + dstSize / 2f, cy + dstSize / 2f - 18f * scale)
                logoBitmapPaint.alpha = (255 * fullLogoAlpha).toInt()
                canvas.drawBitmap(b, null, dstRect, logoBitmapPaint)
            } else {
                // Tipografi çizimi (vektörel yedek)
                val textSize = 54f * scale
                textPaintWhite.textSize = textSize
                textPaintOrange.textSize = textSize
                textPaintOrange.shader = LinearGradient(
                    cx, 0f, cx + 80f * scale, 0f,
                    Color.parseColor("#FFD54F"), Color.parseColor("#FF6D00"),
                    Shader.TileMode.CLAMP
                )
                val textY = cy + tvHeight / 2f + 56f * scale
                val alphaInt = (255 * fullLogoAlpha).toInt()
                textPaintWhite.alpha = alphaInt
                textPaintOrange.alpha = alphaInt
                canvas.drawText("CNDN ", cx, textY, textPaintWhite)
                canvas.drawText("TV", cx, textY, textPaintOrange)
            }
        }
    }
}
