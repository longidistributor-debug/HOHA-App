package com.hoha.analysis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.abs

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("hoha", MODE_PRIVATE) }
    private lateinit var chart: WebView
    private lateinit var resultText: TextView
    private lateinit var usageText: TextView
    private lateinit var marketText: TextView
    private lateinit var apiKey: EditText
    private lateinit var goldButton: Button
    private lateinit var btcButton: Button

    private var selectedTicker = "XAUUSD"
    private var selectedSymbol = "XAUUSD"
    private var selectedType = "commodity"
    private var selectedPeriod = "15m"
    private var busy = false
    private var chartReady = false
    private var dataLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val oldSymbol = prefs.getString("selected_symbol", "XAUUSD").orEmpty().uppercase()
        if (oldSymbol == "BTCUSDT" || oldSymbol == "BTCUSD" || oldSymbol == "BTC") {
            selectBtcValues()
        } else {
            selectGoldValues()
        }
        selectedPeriod = prefs.getString("selected_period", "15m").orEmpty().ifBlank { "15m" }
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

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = TextView(this).apply {
            text = "H"; gravity = Gravity.CENTER; textSize = 28f; setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD); background = circle(Color.WHITE); elevation = dp(8).toFloat()
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(60), dp(60)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        titles.addView(label("HOHA", 28f, true))
        titles.addView(label("HAMAD ANALYSIS", 12f, true, Color.LTGRAY))
        titles.addView(label("HOHA v8 • GOLD + BTC", 11f, true, Color.WHITE))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        root.addView(sectionTitle("FCS CONNECTION"))
        val keyCard = card()
        apiKey = input("FCS REST Access Key", prefs.getString("api_key", "").orEmpty())
        keyCard.addView(apiKey, LinearLayout.LayoutParams(-1, dp(52)))
        keyCard.addView(Button(this).apply {
            text = "SAVE KEY + LOAD CHART"
            setTextColor(Color.BLACK)
            background = rounded(Color.WHITE, dp(12).toFloat())
            setOnClickListener { saveKey(); loadChartData() }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })
        root.addView(keyCard)

        root.addView(sectionTitle("MARKET"))
        val pairCard = card()
        val pairRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        goldButton = marketButton("GOLD\nXAUUSD") { chooseGold() }
        btcButton = marketButton("BTC\nBTCUSDT") { chooseBtc() }
        pairRow.addView(goldButton, LinearLayout.LayoutParams(0, dp(66), 1f).apply { rightMargin = dp(6) })
        pairRow.addView(btcButton, LinearLayout.LayoutParams(0, dp(66), 1f).apply { leftMargin = dp(6) })
        pairCard.addView(pairRow)
        root.addView(pairCard)

        root.addView(sectionTitle("INTERACTIVE MARKET CHART"))
        val chartCard = card()
        val marketRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        marketText = label(displayMarket(), 12f, true, Color.WHITE)
        marketRow.addView(marketText, LinearLayout.LayoutParams(0, dp(46), 1f))
        val periods = arrayOf("1m", "5m", "15m", "30m", "1h", "4h", "1D")
        val tf = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, periods)
            setSelection(periods.indexOf(selectedPeriod).coerceAtLeast(0))
        }
        marketRow.addView(tf, LinearLayout.LayoutParams(dp(110), dp(44)))
        chartCard.addView(marketRow)

        chart = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(Color.BLACK)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    chartReady = true
                    if (apiKey.text.toString().trim().isNotBlank()) loadChartData()
                }
            }
            loadUrl("file:///android_asset/chart.html")
        }
        chartCard.addView(chart, LinearLayout.LayoutParams(-1, dp(430)).apply { topMargin = dp(6) })
        chartCard.addView(label("Only GOLD and BTC are enabled. Chart and analysis share the same cached candles, so analysis does not make another request immediately.", 10f, false, Color.GRAY), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        root.addView(chartCard)

        tf.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val np = periods[pos]
                if (np != selectedPeriod) {
                    selectedPeriod = np
                    prefs.edit().putString("selected_period", np).apply()
                    marketText.text = displayMarket()
                    loadChartData()
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        root.addView(sectionTitle("ANALYSIS"))
        val action = card()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        usageText = label("Calls: ${usage()}/500", 12f, true, Color.LTGRAY)
        top.addView(usageText, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(label("cached + paced", 11f, false, Color.GRAY))
        action.addView(top)
        action.addView(Button(this).apply {
            text = "RUN WEIGHTED ANALYSIS"
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.BLACK)
            background = rounded(Color.WHITE, dp(14).toFloat())
            setOnClickListener { analyze() }
        }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(12) })
        resultText = label("READY\nChoose GOLD or BTC, then load chart and analyze.", 13f, false, Color.WHITE).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.rgb(16, 16, 16), dp(12).toFloat(), Color.rgb(55, 55, 55))
        }
        action.addView(resultText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root.addView(action)

        root.addView(sectionTitle("FLOATING MODE"))
        val fcard = card()
        fcard.addView(label("Floating H uses the same GOLD/BTC selection, timeframe and cached candle data.", 12f, false, Color.LTGRAY))
        fcard.addView(Button(this).apply {
            text = "ENABLE HOHA FLOAT"
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(30, 30, 30), dp(14).toFloat(), Color.rgb(90, 90, 90))
            setOnClickListener { enableFloat() }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12) })
        root.addView(fcard)

        updatePairButtons()
        return ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.BLACK); addView(root) }
    }

    private fun chooseGold() {
        selectGoldValues()
        persistSelection()
        updatePairButtons()
        marketText.text = displayMarket()
        resultText.text = "GOLD • XAUUSD selected\nLoading candles..."
        loadChartData()
    }

    private fun chooseBtc() {
        selectBtcValues()
        persistSelection()
        updatePairButtons()
        marketText.text = displayMarket()
        resultText.text = "BTC • BTCUSDT selected\nLoading candles..."
        loadChartData()
    }

    private fun selectGoldValues() {
        selectedTicker = "XAUUSD"
        selectedSymbol = "XAUUSD"
        selectedType = "commodity"
    }

    private fun selectBtcValues() {
        selectedTicker = "BINANCE:BTCUSDT"
        selectedSymbol = "BTCUSDT"
        selectedType = "crypto"
    }

    private fun persistSelection() {
        prefs.edit()
            .putString("selected_ticker", selectedTicker)
            .putString("selected_symbol", selectedSymbol)
            .putString("selected_type", selectedType)
            .apply()
    }

    private fun displayMarket(): String {
        val name = if (selectedType == "commodity") "GOLD • XAUUSD" else "BTC • BTCUSDT"
        return "$name • $selectedPeriod"
    }

    private fun updatePairButtons() {
        if (!::goldButton.isInitialized || !::btcButton.isInitialized) return
        val goldSelected = selectedType == "commodity"
        styleMarketButton(goldButton, goldSelected)
        styleMarketButton(btcButton, !goldSelected)
    }

    private fun marketButton(textValue: String, click: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setOnClickListener { click() }
    }

    private fun styleMarketButton(button: Button, selected: Boolean) {
        button.setTextColor(if (selected) Color.BLACK else Color.WHITE)
        button.background = rounded(if (selected) Color.WHITE else Color.rgb(25, 25, 25), dp(12).toFloat(), Color.rgb(80, 80, 80))
    }

    private fun loadChartData() {
        if (!chartReady || dataLoading) return
        val key = apiKey.text.toString().trim()
        if (key.isBlank()) return
        dataLoading = true
        chart.evaluateJavascript("showMessage(${JSONObject.quote("Loading ${displayMarket()}...")})", null)
        thread {
            try {
                val (data, credits) = FcsClient.history(key, selectedTicker, selectedSymbol, selectedType, selectedPeriod, 180)
                val arr = JSONArray()
                data.forEach {
                    arr.put(JSONObject().put("t", it.t).put("o", it.o).put("h", it.h).put("l", it.l).put("c", it.c).put("v", it.v))
                }
                runOnUiThread {
                    if (credits > 0) addUsage(credits)
                    usageText.text = "Calls: ${usage()}/500"
                    marketText.text = displayMarket()
                    chart.evaluateJavascript("renderCandles(${JSONObject.quote(arr.toString())},${JSONObject.quote(selectedSymbol)},${JSONObject.quote(selectedPeriod)})", null)
                    resultText.text = "CHART READY: ${displayMarket()}\n${data.size} candles loaded. Analysis can reuse this data without another request."
                    dataLoading = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    chart.evaluateJavascript("showMessage(${JSONObject.quote("Chart load failed: ${friendlyError(e)}")})", null)
                    resultText.text = "CHART LOAD FAILED\n${friendlyError(e)}"
                    dataLoading = false
                }
            }
        }
    }

    private fun analyze() {
        if (busy) return
        if (dataLoading) {
            resultText.text = "MARKET DATA IS LOADING\nAnalysis will use the same candle data. Tap again when chart is ready."
            return
        }
        val key = apiKey.text.toString().trim()
        if (key.isBlank()) { resultText.text = "API KEY REQUIRED"; return }
        if (usage() >= 500) { resultText.text = "MONTHLY LIMIT REACHED\n500/500 FCS calls used."; return }
        saveKey()
        busy = true
        resultText.text = "ANALYZING ${displayMarket()}\nUsing cached candles when available..."
        thread {
            try {
                val (data, credits) = FcsClient.history(key, selectedTicker, selectedSymbol, selectedType, selectedPeriod, 180)
                if (credits > 0) addUsage(credits)
                val signal = AnalysisEngine.analyze(data)
                Handler(Looper.getMainLooper()).post {
                    usageText.text = "Calls: ${usage()}/500"
                    showSignalOnChart(signal)
                    resultText.text = formatSignal(signal)
                    busy = false
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    resultText.text = "ANALYSIS UNAVAILABLE\n${friendlyError(e)}"
                    busy = false
                }
            }
        }
    }

    private fun friendlyError(e: Exception): String {
        val m = e.message.orEmpty()
        return if (m.contains("rate limit", true) || m.contains("three requests", true)) {
            "FCS is cooling down. HOHA now spaces requests automatically; wait for the current load instead of pressing repeatedly."
        } else m.ifBlank { "Unknown data error" }
    }

    private fun showSignalOnChart(signal: Signal?) {
        if (signal == null) { chart.evaluateJavascript("setSignal(null)", null); return }
        val s = JSONObject().put("entry", signal.entry).put("sl", signal.sl).put("tp1", signal.tp1).put("tp2", signal.tp2)
        chart.evaluateJavascript("setSignal(${JSONObject.quote(s.toString())})", null)
    }

    private fun formatSignal(s: Signal?): String {
        if (s == null) return "WAIT / NO EDGE\nThe current BUY and SELL evidence is too balanced."
        val d = if (abs(s.entry) >= 100) 2 else 5
        fun f(v: Double) = String.format(Locale.US, "%.${d}f", v)
        val confidence = when { s.score >= 80 -> "HIGH"; s.score >= 65 -> "MEDIUM"; else -> "EARLY" }
        val reasons = s.reasons.joinToString("\n") { "✓ $it" }
        return "${s.direction} SETUP • $confidence • ${s.score}/100\n\nENTRY  ${f(s.entry)}\nSL  ${f(s.sl)}\nTP1  ${f(s.tp1)}\nTP2  ${f(s.tp2)}\nVALID  ~${s.validBars} candles\n\nWHY\n$reasons"
    }

    private fun saveKey() {
        prefs.edit().putString("api_key", apiKey.text.toString().trim()).apply()
    }

    private fun enableFloat() {
        val key = apiKey.text.toString().trim()
        if (key.isBlank()) { Toast.makeText(this, "Enter FCS key first", Toast.LENGTH_LONG).show(); return }
        saveKey(); persistSelection()
        if (!Settings.canDrawOverlays(this)) {
            prefs.edit().putBoolean("pending_start", true).apply()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else startOverlay()
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this) && prefs.getBoolean("pending_start", false)) {
            prefs.edit().putBoolean("pending_start", false).apply(); startOverlay()
        }
    }

    private fun startOverlay() {
        val i = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        Toast.makeText(this, "HOHA floating mode active", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }

    private fun input(h: String, v: String) = EditText(this).apply {
        hint = h; setHintTextColor(Color.DKGRAY); setTextColor(Color.WHITE); textSize = 13f; setSingleLine(true); setText(v)
        background = rounded(Color.rgb(24, 24, 24), dp(12).toFloat(), Color.rgb(60, 60, 60)); setPadding(dp(14), 0, dp(14), 0)
    }
    private fun sectionTitle(t: String) = label(t, 11f, true, Color.GRAY).apply { setPadding(dp(2), dp(18), 0, dp(8)) }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); background = rounded(Color.rgb(10, 10, 10), dp(16).toFloat(), Color.rgb(45, 45, 45)) }
    private fun label(t: String, s: Float, b: Boolean = false, c: Int = Color.WHITE) = TextView(this).apply { text = t; textSize = s; setTextColor(c); if (b) setTypeface(typeface, Typeface.BOLD) }
    private fun rounded(fill: Int, r: Float, stroke: Int? = null) = GradientDrawable().apply { setColor(fill); cornerRadius = r; if (stroke != null) setStroke(dp(1), stroke) }
    private fun circle(fill: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fill); setStroke(dp(2), Color.rgb(130, 130, 130)) }
    private fun monthKey() = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    private fun usage(): Int { val m = monthKey(); if (prefs.getString("usage_month", "") != m) prefs.edit().putString("usage_month", m).putInt("usage", 0).apply(); return prefs.getInt("usage", 0) }
    private fun addUsage(n: Int) { if (n > 0) prefs.edit().putInt("usage", usage() + n).apply() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
