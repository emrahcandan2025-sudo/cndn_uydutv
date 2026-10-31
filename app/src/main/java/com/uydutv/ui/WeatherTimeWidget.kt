package com.uydutv.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * TV Ekran Köşesi Saat, Tarih ve Canlı Hava Durumu Widget'ı.
 * Arka planda hava durumu bilgisini çeker ve saniyelik saati günceller.
 */
class WeatherTimeWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val timeText: TextView
    private val dateText: TextView
    private val weatherText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private var cachedWeather: String = "Türkiye 18°C 🌤️"
    private var lastWeatherFetchTime = 0L

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale("tr", "TR"))
    private val dateFormat = SimpleDateFormat("d MMMM EEEE", Locale("tr", "TR"))

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val dp14 = dp(14)
        val dp8 = dp(8)
        setPadding(dp14, dp8, dp14, dp8)

        // Ultra-Premium buzlu cam (Frosted Glass) arka plan
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#EE0F172A"), Color.parseColor("#F5080E1C"))
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1.2f), Color.argb(110, 56, 189, 248))
        }
        elevation = dp(10).toFloat()

        // Hava Durumu Metni
        weatherText = TextView(context).apply {
            textSize = 13.5f
            setTextColor(Color.parseColor("#38BDF8")) // Elektrik Turkuaz
            typeface = Typeface.DEFAULT_BOLD
            text = cachedWeather
            setPadding(0, 0, dp(12), 0)
        }

        // Ayırıcı çizgi
        val divider = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.argb(120, 100, 116, 139))
            text = "│"
            setPadding(0, 0, dp(12), 0)
        }

        // Tarih ve Saat Kutusu (Dikey)
        val timeBox = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.END
        }

        timeText = TextView(context).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.04f
        }

        dateText = TextView(context).apply {
            textSize = 10.5f
            setTextColor(Color.parseColor("#94A3B8"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.02f
        }

        timeBox.addView(timeText)
        timeBox.addView(dateText)

        addView(weatherText)
        addView(divider)
        addView(timeBox)

        updateClock()
        isClickable = true
        isFocusable = false
        setOnClickListener {
            fetchLiveWeatherIfNeeded(force = true)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(tickRunnable)
        fetchLiveWeatherIfNeeded()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tickRunnable)
        super.onDetachedFromWindow()
    }

    private fun updateClock() {
        val now = Date()
        timeText.text = timeFormat.format(now)
        dateText.text = dateFormat.format(now)
    }

    fun fetchLiveWeatherIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastWeatherFetchTime < 30 * 60 * 1000L) return // 30 dakikada bir güncelle
        lastWeatherFetchTime = now

        worker.execute {
            try {
                // Open-Meteo İstanbul koordinatları (ücretsiz, anahtarsız)
                val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=41.0082&longitude=28.9784&current=temperature_2m,weather_code&timezone=auto"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                    requestMethod = "GET"
                }
                if (conn.responseCode in 200..299) {
                    val raw = conn.inputStream.use { input ->
                        InputStreamReader(input, StandardCharsets.UTF_8).buffered().use { it.readText() }
                    }
                    conn.disconnect()
                    val json = JSONObject(raw)
                    val current = json.optJSONObject("current")
                    if (current != null) {
                        val temp = Math.round(current.optDouble("temperature_2m", 18.0))
                        val code = current.optInt("weather_code", 0)
                        val icon = weatherIcon(code)
                        cachedWeather = "İstanbul ${temp}°C $icon"
                        handler.post { weatherText.text = cachedWeather }
                    }
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.d("WeatherTimeWidget", "Hava durumu çekilemedi: ${e.message}")
            }
        }
    }

    private fun weatherIcon(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 61, 63, 65 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "⛅"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
}
