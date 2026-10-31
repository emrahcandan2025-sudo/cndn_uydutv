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
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.uydutv.R

/**
 * CNDN TV Modern Stüdyo Açılış Ekranı (Studio Intro).
 *
 * Akıcı, sade ve profesyonel tasarım:
 * 1. Derin sinematik gece mavisi arka plan (#070C18 -> #03050B)
 * 2. Merkeze yayılan yumuşak neon ambiyans ışıltısı (Cyan & Amber)
 * 3. Yüksek çözünürlüklü 3D CNDN TV logosunun sinematik yakınlaşma ve parıltı efektiyle belirmesi
 * 4. Logonun altında zarif stüdyo yansıması (floor reflection)
 * 5. Minimalist "CANLI TELEVİZYON YAYIN PLATFORMU" tipografisi
 * 6. İnce, zarif neon yükleme çizgisi
 * 7. Canlı TV akışına pürüzsüz geçiş (2 saniye)
 */
class CinematicSplashView(
    context: Context,
    private val onFinished: () -> Unit
) : View(context) {

    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var isCompleted = false

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    private val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A8B8D0")
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.24f
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#28D9C5")
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.12f
    }

    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(45, 255, 255, 255)
    }

    private val barProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var logoBitmap: Bitmap? = null

    init {
        isClickable = true
        isFocusable = true
        loadLogoBitmap()
        startAnimation()
    }

    private fun loadLogoBitmap() {
        try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            logoBitmap = BitmapFactory.decodeResource(resources, R.drawable.cndn_logo, opts)
        } catch (_: Exception) {
            // Hata durumunda güvenli boş kalır
        }
    }

    private fun startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2100L
            interpolator = DecelerateInterpolator(1.4f)
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
            animate()
                .alpha(0f)
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setDuration(260)
                .withEndAction {
                    onFinished()
                }
                .start()
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

        val cx = w / 2f
        val cy = h / 2f - (h * 0.05f)
        val scale = Math.min(w / 800f, h / 500f).coerceIn(0.7f, 1.4f)

        // 1. Derin sinematik arka plan gradyanı
        bgPaint.shader = RadialGradient(
            cx, cy, Math.max(w, h) * 0.85f,
            intArrayOf(
                Color.parseColor("#0F1A30"), // Merkez koyu lacivert
                Color.parseColor("#091022"),
                Color.parseColor("#040710")  // Kenarlar sinematik siyah
            ),
            floatArrayOf(0f, 0.55f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. Yumuşak neon ambiyans ışıltısı (Pulsing Ambient Glow)
        val glowAlpha = when {
            progress < 0.20f -> (progress / 0.20f)
            progress < 0.80f -> 1.0f
            else -> (1.0f - (progress - 0.80f) / 0.20f)
        }
        val glowRadius = 240f * scale * (0.85f + progress * 0.15f)
        glowPaint.shader = RadialGradient(
            cx, cy - 10f * scale, glowRadius,
            intArrayOf(
                Color.argb((70 * glowAlpha).toInt(), 40, 217, 197),  // Turkuaz parıltı #28D9C5
                Color.argb((45 * glowAlpha).toInt(), 41, 156, 255),  // Elektrik mavisi #299CFF
                Color.argb((20 * glowAlpha).toInt(), 255, 140, 0),   // İnce sıcak turuncu
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.40f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy - 10f * scale, glowRadius, glowPaint)

        // 3. 3D Logo Çizimi
        val b = logoBitmap
        if (b != null && !b.isRecycled) {
            // Sinematik giriş eğrisi (Zoom: 0.90 -> 1.00, Alpha: 0 -> 1.0)
            val logoAlpha = (progress / 0.28f).coerceIn(0f, 1f)
            val zoomFactor = 0.90f + (0.10f * (progress.coerceAtMost(0.85f) / 0.85f))
            val baseSize = 250f * scale * zoomFactor

            val left = cx - baseSize / 2f
            val top = cy - baseSize / 2f
            val right = cx + baseSize / 2f
            val bottom = cy + baseSize / 2f
            val dstRect = RectF(left, top, right, bottom)

            logoPaint.alpha = (255 * logoAlpha).toInt()
            canvas.drawBitmap(b, null, dstRect, logoPaint)

            // 4. Parlaklık Geçişi (Studio Shimmer Sheen: 0.35 -> 0.75)
            val shimmerProgress = ((progress - 0.32f) / 0.40f).coerceIn(0f, 1f)
            if (shimmerProgress in 0.01f..0.99f) {
                val shimmerX = left - baseSize * 0.5f + (baseSize * 2.0f * shimmerProgress)
                shimmerPaint.shader = LinearGradient(
                    shimmerX - 50f * scale, top,
                    shimmerX + 50f * scale, bottom,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.argb((80 * logoAlpha).toInt(), 255, 255, 255),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.5f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(dstRect, shimmerPaint)
            }
        }

        // 5. Alt Başlık & Tipografi (0.25 -> 0.60)
        val textAlpha = ((progress - 0.22f) / 0.30f).coerceIn(0f, 1f)
        if (textAlpha > 0f) {
            val textY = cy + 155f * scale
            textPaint.textSize = 13.5f * scale
            textPaint.alpha = (210 * textAlpha).toInt()
            canvas.drawText("CANLI TELEVİZYON YAYIN PLATFORMU", cx, textY, textPaint)

            badgePaint.textSize = 10.5f * scale
            badgePaint.alpha = (180 * textAlpha).toInt()
            canvas.drawText("HD / 4K FTA KESİNTİSİZ YAYIN", cx, textY + 22f * scale, badgePaint)
        }

        // 6. Minimalist İnce Neon Yükleme Çubuğu (Loading Progress Bar)
        val barAlpha = ((progress - 0.15f) / 0.25f).coerceIn(0f, 1f)
        if (barAlpha > 0f) {
            val barW = 160f * scale
            val barH = 3.5f * scale
            val barY = cy + 205f * scale
            val barRect = RectF(cx - barW / 2f, barY - barH / 2f, cx + barW / 2f, barY + barH / 2f)

            barBgPaint.alpha = (45 * barAlpha).toInt()
            canvas.drawRoundRect(barRect, barH / 2f, barH / 2f, barBgPaint)

            val fillW = barW * progress.coerceIn(0f, 1f)
            val fillRect = RectF(cx - barW / 2f, barY - barH / 2f, cx - barW / 2f + fillW, barY + barH / 2f)

            barProgressPaint.shader = LinearGradient(
                barRect.left, barRect.top, barRect.right, barRect.top,
                intArrayOf(
                    Color.parseColor("#28D9C5"), // Turkuaz
                    Color.parseColor("#299CFF"), // Elektrik mavisi
                    Color.parseColor("#FF9F2E")  // Sıcak altın
                ),
                null,
                Shader.TileMode.CLAMP
            )
            barProgressPaint.alpha = (255 * barAlpha).toInt()
            canvas.drawRoundRect(fillRect, barH / 2f, barH / 2f, barProgressPaint)
        }
    }
}
