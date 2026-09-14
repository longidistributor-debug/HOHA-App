package com.hoha.analysis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: TextView
    private var panel: LinearLayout? = null
    private var chart: WebView? = null
    private var statusView: TextView? = null
    private var counterView: TextView? = null
    private var symbol = "EURUSD"
    private var period = "15m"
    private var busy = false

    private val prefs by lazy { getSharedPreferences("hoha", MODE_PRIVATE) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        createBubble()
    }

    private fun startAsForeground() {
        val channelId = "hoha"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(channelId, "HOHA", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("HOHA running")
            .setContentText("Floating forex analysis active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(109, notification)
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun createBubble() {
        bubbleView = TextView(this).apply {
            text = "H"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            elevation = dp(8).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.BLACK)
                setStroke(dp(2), Color.WHITE)
            }
        }

        val params = WindowManager.LayoutParams(
            dp(58),
            dp(58),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(20)
            y = dp(250)
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, params)
    }

    private fun togglePanel() {
        if (panel == null) showPanel() else hidePanel()
    }

    private fun showPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(8, 8, 8))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(2), Color.WHITE)
            }
        }
        panel = root

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            makeText("HOHA • HAMAD ANALYSIS", 18f, true),
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )
        val close = Button(this).apply {
            text = "—"
            setOnClickListener { hidePanel() }
        }
        titleRow.addView(close, LinearLayout.LayoutParams(dp(48), dp(42)))
        root.addView(titleRow)

        val symbols = arrayOf(
            "EURUSD", "GBPUSD", "USDJPY", "AUDUSD", "USDCAD",
            "USDCHF", "NZDUSD", "EURJPY", "GBPJPY", "XAUUSD"
        )
        val timeframes = arrayOf("5m", "15m", "30m", "1h", "4h", "1D")

        val selectorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val symbolSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@OverlayService,
                android.R.layout.simple_spinner_dropdown_item,
                symbols
            )
        }
        val timeframeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@OverlayService,
                android.R.layout.simple_spinner_dropdown_item,
                timeframes
            )
            setSelection(1)
        }
        selectorRow.addView(symbolSpinner, LinearLayout.LayoutParams(0, dp(50), 1f))
        selectorRow.addView(timeframeSpinner, LinearLayout.LayoutParams(0, dp(50), 1f))
        root.addView(selectorRow)

        chart = WebView(this).apply {
            settings.javaScriptEnabled = true
            setBackgroundColor(Color.BLACK)
            loadUrl("file:///android_asset/chart.html")
        }
        root.addView(chart, LinearLayout.LayoutParams(-1, dp(270)))

        counterView = makeText("Calls: ${usage()}/500", 12f, true)
        root.addView(counterView)

        val runButton = Button(this).apply {
            text = "RUN ANALYSIS"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            setOnClickListener { analyze() }
        }
        root.addView(runButton, LinearLayout.LayoutParams(-1, dp(52)))

        statusView = makeText("Waiting for analysis…", 12f)
        root.addView(statusView)

        symbolSpinner.onItemSelectedListener = SelectionListener { symbol = symbols[it] }
        timeframeSpinner.onItemSelectedListener = SelectionListener { period = timeframes[it] }

        val params = WindowManager.LayoutParams(
            dp(350),
            dp(570),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(80)
        }
        windowManager.addView(root, params)
    }

    private fun hidePanel() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        chart = null
        statusView = null
        counterView = null
    }

    private fun analyze() {
        if (busy) return
        val key = prefs.getString("api_key", "").orEmpty().trim()
        if (key.isBlank()) {
            statusView?.text = "API key missing"
            return
        }
        if (usage() >= 500) {
            statusView?.text = "500/500 monthly calls reached"
            return
        }

        busy = true
        statusView?.text = "Analyzing $symbol $period…"

        thread {
            try {
                val (data, credits) = FcsClient.history(key, symbol, period, 180)
                addUsage(credits.coerceAtLeast(1))
                val signal = AnalysisEngine.analyze(data)
                Handler(Looper.getMainLooper()).post {
                    counterView?.text = "Calls: ${usage()}/500"
                    render(data, signal)
                    statusView?.text = formatSignal(signal)
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    statusView?.text = "Request failed: ${e.message ?: "Unknown error"}"
                }
            } finally {
                busy = false
            }
        }
    }

    private fun render(data: List<Candle>, signal: Signal?) {
        val array = JSONArray()
        data.takeLast(120).forEach {
            array.put(
                JSONObject()
                    .put("o", it.o)
                    .put("h", it.h)
                    .put("l", it.l)
                    .put("c", it.c)
            )
        }
        val dataJson = JSONObject.quote(array.toString())
        chart?.evaluateJavascript("setData($dataJson)", null)

        if (signal == null) {
            chart?.evaluateJavascript("setSignal(null)", null)
        } else {
            val signalJson = JSONObject()
                .put("entry", signal.entry)
                .put("sl", signal.sl)
                .put("tp1", signal.tp1)
                .put("tp2", signal.tp2)
                .toString()
            chart?.evaluateJavascript("setSignal(${JSONObject.quote(signalJson)})", null)
        }
    }

    private fun formatSignal(signal: Signal?): String {
        if (signal == null) return "NO VALID SETUP\nWaiting for next analysis."
        val decimals = if (abs(signal.entry) >= 100) 2 else 5
        fun price(value: Double) = String.format(Locale.US, "%.${decimals}f", value)
        val reasons = signal.reasons.joinToString("\n") { "✓ $it" }
        return "${signal.direction} • SCORE ${signal.score}/100\n" +
            "Entry ${price(signal.entry)}\n" +
            "SL ${price(signal.sl)}\n" +
            "TP1 ${price(signal.tp1)}\n" +
            "TP2 ${price(signal.tp2)}\n" + reasons
    }

    private fun currentMonth(): String =
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    private fun usage(): Int {
        val month = currentMonth()
        if (prefs.getString("usage_month", "") != month) {
            prefs.edit()
                .putString("usage_month", month)
                .putInt("usage", 0)
                .apply()
        }
        return prefs.getInt("usage", 0)
    }

    private fun addUsage(count: Int) {
        prefs.edit().putInt("usage", usage() + count).apply()
    }

    private fun makeText(value: String, size: Float, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        hidePanel()
        if (::bubbleView.isInitialized) {
            runCatching { windowManager.removeView(bubbleView) }
        }
        super.onDestroy()
    }

    private class SelectionListener(
        private val onSelected: (Int) -> Unit
    ) : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            onSelected(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
}
