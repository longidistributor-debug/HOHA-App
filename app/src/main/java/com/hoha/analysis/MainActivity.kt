package com.hoha.analysis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("hoha", MODE_PRIVATE) }
    private lateinit var chart: WebView
    private lateinit var resultText: TextView
    private lateinit var usageText: TextView
    private lateinit var apiKey: EditText
    private var selectedSymbol = "EURUSD"
    private var selectedPeriod = "15m"
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(Color.rgb(4, 4, 4))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = TextView(this).apply {
            text = "H"
            gravity = Gravity.CENTER
            textSize = 28f
            setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
            background = circle(Color.WHITE)
            elevation = dp(8).toFloat()
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(60), dp(60)))
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        titleBox.addView(label("HOHA", 28f, true))
        titleBox.addView(label("HAMAD ANALYSIS", 12f, true, Color.LTGRAY))
        titleBox.addView(label("Forex Confluence Engine", 11f, false, Color.GRAY))
        header.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)

        val badgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, dp(14))
        }
        badgeRow.addView(badge("LIVE ANALYSIS"), LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(6) })
        badgeRow.addView(badge("NO LOGIN"), LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginStart = dp(6) })
        root.addView(badgeRow)

        root.addView(sectionTitle("FCS DATA CONNECTION"))
        val keyCard = card()
        apiKey = EditText(this).apply {
            hint = "Paste FCS Access Key"
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            setText(prefs.getString("api_key", ""))
            background = rounded(Color.rgb(24, 24, 24), dp(12).toFloat(), Color.rgb(60, 60, 60))
            setPadding(dp(14), 0, dp(14), 0)
        }
        keyCard.addView(apiKey, LinearLayout.LayoutParams(-1, dp(52)))
        val saveKey = Button(this).apply {
            text = "SAVE KEY LOCALLY"
            textSize = 12f
            setTextColor(Color.BLACK)
            background = rounded(Color.WHITE, dp(12).toFloat())
            setOnClickListener {
                prefs.edit().putString("api_key", apiKey.text.toString().trim()).apply()
                Toast.makeText(this@MainActivity, "FCS key saved", Toast.LENGTH_SHORT).show()
            }
        }
        keyCard.addView(saveKey, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        root.addView(keyCard)

        root.addView(sectionTitle("MARKET SETUP"))
        val selectorCard = card()
        val selectorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val symbols = arrayOf("EURUSD", "GBPUSD", "USDJPY", "AUDUSD", "USDCAD", "USDCHF", "NZDUSD", "EURJPY", "GBPJPY", "XAUUSD")
        val periods = arrayOf("5m", "15m", "30m", "1h", "4h", "1D")
        val symbolSpinner = makeSpinner(symbols)
        val periodSpinner = makeSpinner(periods).apply { setSelection(1) }
        selectorRow.addView(symbolSpinner, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })
        selectorRow.addView(periodSpinner, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(6) })
        selectorCard.addView(selectorRow)
        symbolSpinner.onItemSelectedListener = listener { selectedSymbol = symbols[it] }
        periodSpinner.onItemSelectedListener = listener { selectedPeriod = periods[it] }
        root.addView(selectorCard)

        root.addView(sectionTitle("PRICE CHART"))
        val chartCard = card()
        chart = WebView(this).apply {
            settings.javaScriptEnabled = true
            setBackgroundColor(Color.BLACK)
            loadUrl("file:///android_asset/chart.html")
        }
        chartCard.addView(chart, LinearLayout.LayoutParams(-1, dp(290)))
        root.addView(chartCard)

        val actionCard = card()
        val topLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        usageText = label("Calls: ${usage()}/500", 12f, true, Color.LTGRAY)
        topLine.addView(usageText, LinearLayout.LayoutParams(0, -2, 1f))
        topLine.addView(label("Free API quota", 11f, false, Color.GRAY))
        actionCard.addView(topLine)

        val run = Button(this).apply {
            text = "RUN FULL ANALYSIS"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.BLACK)
            background = rounded(Color.WHITE, dp(14).toFloat())
            setOnClickListener { analyze() }
        }
        actionCard.addView(run, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(12) })

        resultText = label("READY\nSelect pair + timeframe, then run analysis.", 13f, false, Color.WHITE).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.rgb(16, 16, 16), dp(12).toFloat(), Color.rgb(55, 55, 55))
        }
        actionCard.addView(resultText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root.addView(actionCard)

        root.addView(sectionTitle("FLOATING MODE"))
        val floatCard = card()
        floatCard.addView(label("Open HOHA over MT5 or any other app. The floating H bubble opens the compact chart + signal panel.", 12f, false, Color.LTGRAY))
        val floatButton = Button(this).apply {
            text = "ENABLE HOHA FLOAT"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(Color.rgb(30, 30, 30), dp(14).toFloat(), Color.rgb(90, 90, 90))
            setOnClickListener { enableFloat() }
        }
        floatCard.addView(floatButton, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12) })
        root.addView(floatCard)

        root.addView(sectionTitle("ANALYSIS ENGINE"))
        val engine = card()
        engine.addView(label("EMA trend • RSI • ATR • BOS • CHoCH • liquidity sweep • FVG • order-block context • support/resistance • weighted confluence", 12f, false, Color.LTGRAY))
        engine.addView(label("Signals are probabilistic and require confirmation. Use risk management.", 11f, false, Color.GRAY), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        root.addView(engine)

        return ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.BLACK)
            addView(root)
        }
    }

    private fun analyze() {
        if (busy) return
        val key = apiKey.text.toString().trim()
        if (key.isBlank()) {
            resultText.text = "API KEY REQUIRED\nPaste your FCS access key above and save it."
            return
        }
        if (usage() >= 500) {
            resultText.text = "MONTHLY LIMIT REACHED\n500/500 FCS calls used."
            return
        }
        prefs.edit().putString("api_key", key).apply()
        busy = true
        resultText.text = "ANALYZING $selectedSymbol • $selectedPeriod\nFetching candles and checking confluence..."
        thread {
            try {
                val pair = FcsClient.history(key, selectedSymbol, selectedPeriod, 180)
                addUsage(pair.second)
                val signal = AnalysisEngine.analyze(pair.first)
                Handler(Looper.getMainLooper()).post {
                    usageText.text = "Calls: ${usage()}/500"
                    renderChart(pair.first, signal)
                    resultText.text = formatSignal(signal)
                    busy = false
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    resultText.text = "REQUEST FAILED\n${e.message ?: "Unknown error"}"
                    busy = false
                }
            }
        }
    }

    private fun renderChart(data: List<Candle>, signal: Signal?) {
        val arr = JSONArray()
        data.takeLast(120).forEach {
            arr.put(JSONObject().put("o", it.o).put("h", it.h).put("l", it.l).put("c", it.c))
        }
        chart.evaluateJavascript("setData(${JSONObject.quote(arr.toString())})", null)
        if (signal == null) {
            chart.evaluateJavascript("setSignal(null)", null)
        } else {
            val s = JSONObject().put("entry", signal.entry).put("sl", signal.sl).put("tp1", signal.tp1).put("tp2", signal.tp2)
            chart.evaluateJavascript("setSignal(${JSONObject.quote(s.toString())})", null)
        }
    }

    private fun formatSignal(s: Signal?): String {
        if (s == null) return "NO VALID SETUP\nConfluence is not strong enough. Waiting for next analysis."
        val decimals = if (abs(s.entry) >= 100) 2 else 5
        fun f(v: Double) = String.format(Locale.US, "%.${decimals}f", v)
        val reasons = s.reasons.joinToString("\n") { "✓ $it" }
        return "${s.direction} SETUP • SCORE ${s.score}/100\n\nENTRY  ${f(s.entry)}\nSTOP LOSS  ${f(s.sl)}\nTP1  ${f(s.tp1)}\nTP2  ${f(s.tp2)}\n\nCONFLUENCE\n$reasons"
    }

    private fun enableFloat() {
        val key = apiKey.text.toString().trim()
        if (key.isBlank()) {
            Toast.makeText(this, "Enter your FCS Access Key first", Toast.LENGTH_LONG).show()
            return
        }
        prefs.edit().putString("api_key", key).apply()
        if (!Settings.canDrawOverlays(this)) {
            prefs.edit().putBoolean("pending_start", true).apply()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            startOverlay()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this) && prefs.getBoolean("pending_start", false)) {
            prefs.edit().putBoolean("pending_start", false).apply()
            startOverlay()
        }
    }

    private fun startOverlay() {
        val i = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        Toast.makeText(this, "HOHA floating mode active", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }

    private fun sectionTitle(text: String): TextView = label(text, 11f, true, Color.GRAY).apply {
        setPadding(dp(2), dp(18), 0, dp(8))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = rounded(Color.rgb(10, 10, 10), dp(16).toFloat(), Color.rgb(45, 45, 45))
    }

    private fun badge(text: String): TextView = label(text, 10f, true, Color.WHITE).apply {
        gravity = Gravity.CENTER
        background = rounded(Color.rgb(20, 20, 20), dp(10).toFloat(), Color.rgb(70, 70, 70))
    }

    private fun makeSpinner(items: Array<String>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        background = rounded(Color.rgb(24, 24, 24), dp(12).toFloat(), Color.rgb(65, 65, 65))
        setPadding(dp(10), 0, dp(8), 0)
    }

    private fun listener(block: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = block(position)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun label(text: String, size: Float, bold: Boolean = false, color: Int = Color.WHITE): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun circle(fill: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke(dp(2), Color.rgb(130, 130, 130))
    }

    private fun monthKey(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    private fun usage(): Int {
        val month = monthKey()
        if (prefs.getString("usage_month", "") != month) {
            prefs.edit().putString("usage_month", month).putInt("usage", 0).apply()
        }
        return prefs.getInt("usage", 0)
    }

    private fun addUsage(n: Int) {
        prefs.edit().putInt("usage", usage() + n.coerceAtLeast(1)).apply()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
