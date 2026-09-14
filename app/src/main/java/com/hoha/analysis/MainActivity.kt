package com.hoha.analysis

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
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
    private lateinit var socketKey: EditText
    private var selectedTicker="FX:EURUSD"
    private var selectedSymbol="EURUSD"
    private var selectedType="forex"
    private var selectedPeriod="15m"
    private var busy=false
    private var chartReady=false

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        selectedTicker=prefs.getString("selected_ticker","FX:EURUSD").orEmpty().ifBlank{"FX:EURUSD"}
        selectedSymbol=prefs.getString("selected_symbol","EURUSD").orEmpty().ifBlank{"EURUSD"}
        selectedType=prefs.getString("selected_type","forex").orEmpty().ifBlank{"forex"}
        selectedPeriod=prefs.getString("selected_period","15m").orEmpty().ifBlank{"15m"}
        if(Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),10)
        window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK
        setContentView(buildUi())
    }

    private fun buildUi():View{
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(28));setBackgroundColor(Color.rgb(4,4,4))}
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val logo=TextView(this).apply{text="H";gravity=Gravity.CENTER;textSize=28f;setTextColor(Color.BLACK);setTypeface(typeface,Typeface.BOLD);background=circle(Color.WHITE);elevation=dp(8).toFloat()}
        header.addView(logo,LinearLayout.LayoutParams(dp(60),dp(60)))
        val titles=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),0,0,0)}
        titles.addView(label("HOHA",28f,true));titles.addView(label("HAMAD ANALYSIS",12f,true,Color.LTGRAY));titles.addView(label("Multi-Market Weighted Analysis",11f,false,Color.GRAY))
        header.addView(titles,LinearLayout.LayoutParams(0,-2,1f));root.addView(header)

        root.addView(sectionTitle("FCS CONNECTION"));val keyCard=card()
        apiKey=input("FCS REST Access Key",prefs.getString("api_key","").orEmpty());socketKey=input("FCS Socket Key (optional)",prefs.getString("socket_key","").orEmpty())
        keyCard.addView(apiKey,LinearLayout.LayoutParams(-1,dp(52)));keyCard.addView(socketKey,LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(8)})
        keyCard.addView(Button(this).apply{text="SAVE KEYS + LOAD CHART";setTextColor(Color.BLACK);background=rounded(Color.WHITE,dp(12).toFloat());setOnClickListener{saveKeys();initChart()}},LinearLayout.LayoutParams(-1,dp(50)).apply{topMargin=dp(10)});root.addView(keyCard)

        root.addView(sectionTitle("INTERACTIVE MARKET CHART"));val chartCard=card()
        val marketRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        marketText=label("$selectedTicker • $selectedPeriod",12f,true,Color.WHITE)
        marketRow.addView(marketText,LinearLayout.LayoutParams(0,dp(46),1f))
        marketRow.addView(Button(this).apply{text="INSTRUMENTS";setTextColor(Color.BLACK);background=rounded(Color.WHITE,dp(10).toFloat());setOnClickListener{openBrowser()}},LinearLayout.LayoutParams(dp(130),dp(44)))
        chartCard.addView(marketRow)
        chart=WebView(this).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;setBackgroundColor(Color.BLACK);addJavascriptInterface(ChartBridge(),"HOHAAndroid");webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView?,url:String?){chartReady=true;initChart()}};loadUrl("file:///android_asset/chart.html")}
        chartCard.addView(chart,LinearLayout.LayoutParams(-1,dp(470)).apply{topMargin=dp(6)})
        chartCard.addView(label("Tap INSTRUMENTS for the real FCS list/search. Blank search shows symbols; categories include Forex, Gold/Commodities, Crypto, Futures, DEX, Stocks, Funds and Indices.",10f,false,Color.GRAY),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)});root.addView(chartCard)

        root.addView(sectionTitle("ANALYSIS"));val action=card();val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        usageText=label("Calls: ${usage()}/500",12f,true,Color.LTGRAY);top.addView(usageText,LinearLayout.LayoutParams(0,-2,1f));top.addView(label("REST quota",11f,false,Color.GRAY));action.addView(top)
        action.addView(Button(this).apply{text="RUN WEIGHTED ANALYSIS";setTypeface(typeface,Typeface.BOLD);setTextColor(Color.BLACK);background=rounded(Color.WHITE,dp(14).toFloat());setOnClickListener{analyze()}},LinearLayout.LayoutParams(-1,dp(56)).apply{topMargin=dp(12)})
        resultText=label("READY\nSelect an instrument inside the chart card, then run analysis.",13f,false,Color.WHITE).apply{setPadding(dp(14),dp(14),dp(14),dp(14));background=rounded(Color.rgb(16,16,16),dp(12).toFloat(),Color.rgb(55,55,55))}
        action.addView(resultText,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)});root.addView(action)

        root.addView(sectionTitle("FLOATING MODE"));val fcard=card();fcard.addView(label("Use the draggable H bubble over MT5. Last selected instrument is shared with floating mode.",12f,false,Color.LTGRAY));fcard.addView(Button(this).apply{text="ENABLE HOHA FLOAT";setTextColor(Color.WHITE);background=rounded(Color.rgb(30,30,30),dp(14).toFloat(),Color.rgb(90,90,90));setOnClickListener{enableFloat()}},LinearLayout.LayoutParams(-1,dp(54)).apply{topMargin=dp(12)});root.addView(fcard)
        return ScrollView(this).apply{isFillViewport=true;setBackgroundColor(Color.BLACK);addView(root)}
    }

    private fun openBrowser(){
        val key=apiKey.text.toString().trim();if(key.isBlank()){Toast.makeText(this,"Enter FCS key first",Toast.LENGTH_LONG).show();return}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(10),dp(14),dp(8))}
        val cats=arrayOf("All","Forex","Commodity","Crypto","Futures","DEX","Stock","Fund","Index")
        val spinner=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,cats)}
        val search=input("Search e.g. XAUUSD, EUR, BTC, AAPL","")
        val go=Button(this).apply{text="SEARCH / SHOW LIST"}
        val status=label("Loading supported instruments...",11f,false,Color.DKGRAY)
        val list=ListView(this)
        box.addView(spinner,LinearLayout.LayoutParams(-1,dp(50)));box.addView(search,LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(6)});box.addView(go,LinearLayout.LayoutParams(-1,dp(48)).apply{topMargin=dp(6)});box.addView(status);box.addView(list,LinearLayout.LayoutParams(-1,dp(430)))
        val dialog=AlertDialog.Builder(this).setTitle("FCS Instruments").setView(box).setNegativeButton("Close",null).create()
        var items=listOf<Instrument>()
        fun load(){
            val cat=cats[spinner.selectedItemPosition].lowercase();val q=search.text.toString().trim();status.text="Loading ${cats[spinner.selectedItemPosition]}..."
            thread{try{val(res,calls)=MarketClient.browse(key,cat,q);runOnUiThread{addUsage(calls);usageText.text="Calls: ${usage()}/500";items=res;list.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_2,android.R.id.text1,res.map{"${it.ticker}  •  ${it.name}"});status.text=if(res.isEmpty())"No instruments returned for this category/search." else "${res.size} instruments shown. Search to find more."}}catch(e:Exception){runOnUiThread{status.text="Load failed: ${e.message}"}}}
        }
        go.setOnClickListener{load()};spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){load()};override fun onNothingSelected(p:AdapterView<*>?){}}
        list.setOnItemClickListener{_,_,pos,_->items.getOrNull(pos)?.let{selectInstrument(it);dialog.dismiss()}}
        dialog.setOnShowListener{load()};dialog.show()
    }

    private fun selectInstrument(i:Instrument){
        selectedTicker=i.ticker;selectedSymbol=i.symbol;selectedType=i.type
        prefs.edit().putString("selected_ticker",selectedTicker).putString("selected_symbol",selectedSymbol).putString("selected_type",selectedType).apply()
        marketText.text="$selectedTicker • $selectedPeriod"
        resultText.text="SELECTED: $selectedTicker\n${i.name}\nReady for weighted analysis."
        if(chartReady)chart.evaluateJavascript("loadTicker(${JSONObject.quote(selectedTicker)},${JSONObject.quote(selectedPeriod)})",null)
    }

    inner class ChartBridge{@JavascriptInterface fun onPeriodChanged(period:String){val p=period.trim().ifBlank{"15m"};selectedPeriod=p;prefs.edit().putString("selected_period",p).apply();runOnUiThread{marketText.text="$selectedTicker • $selectedPeriod"}}}
    private fun saveKeys(){prefs.edit().putString("api_key",apiKey.text.toString().trim()).putString("socket_key",socketKey.text.toString().trim()).apply();Toast.makeText(this,"Keys saved locally",Toast.LENGTH_SHORT).show()}
    private fun initChart(){if(!chartReady)return;val key=apiKey.text.toString().trim();if(key.isBlank())return;val socket=socketKey.text.toString().trim();chart.evaluateJavascript("initLiveChart(${JSONObject.quote(key)},${JSONObject.quote(selectedTicker)},${JSONObject.quote(selectedPeriod)},${JSONObject.quote(socket)})",null)}

    private fun analyze(){if(busy)return;val key=apiKey.text.toString().trim();if(key.isBlank()){resultText.text="API KEY REQUIRED";return};if(usage()>=500){resultText.text="MONTHLY LIMIT REACHED\n500/500 FCS calls used.";return};saveKeys();busy=true;resultText.text="ANALYZING $selectedTicker • $selectedPeriod\nScoring weighted confirmations...";thread{try{val(data,credits)=FcsClient.history(key,selectedTicker,selectedSymbol,selectedType,selectedPeriod,180);addUsage(credits);val signal=AnalysisEngine.analyze(data);Handler(Looper.getMainLooper()).post{usageText.text="Calls: ${usage()}/500";showSignalOnChart(signal);resultText.text=formatSignal(signal);busy=false}}catch(e:Exception){Handler(Looper.getMainLooper()).post{resultText.text="REQUEST FAILED\n${e.message?:"Unknown error"}";busy=false}}}}
    private fun showSignalOnChart(signal:Signal?){if(signal==null){chart.evaluateJavascript("setSignal(null)",null);return};val s=JSONObject().put("entry",signal.entry).put("sl",signal.sl).put("tp1",signal.tp1).put("tp2",signal.tp2);chart.evaluateJavascript("setSignal(${JSONObject.quote(s.toString())})",null)}
    private fun formatSignal(s:Signal?):String{if(s==null)return"WAIT / NO EDGE\nThe current BUY and SELL evidence is too balanced.";val d=if(abs(s.entry)>=100)2 else 5;fun f(v:Double)=String.format(Locale.US,"%.${d}f",v);val confidence=when{s.score>=80->"HIGH";s.score>=65->"MEDIUM";else->"EARLY"};return"${s.direction} SETUP • $confidence • ${s.score}/100\n\nENTRY  ${f(s.entry)}\nSL  ${f(s.sl)}\nTP1  ${f(s.tp1)}\nTP2  ${f(s.tp2)}\nVALID  ~${s.validBars} candles\n\nWHY\n${s.reasons.joinToString("\n"){"✓ $it"}}"}
    private fun enableFloat(){val key=apiKey.text.toString().trim();if(key.isBlank()){Toast.makeText(this,"Enter FCS key first",Toast.LENGTH_LONG).show();return};saveKeys();if(!Settings.canDrawOverlays(this)){prefs.edit().putBoolean("pending_start",true).apply();startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}else startOverlay()}
    override fun onResume(){super.onResume();if(Settings.canDrawOverlays(this)&&prefs.getBoolean("pending_start",false)){prefs.edit().putBoolean("pending_start",false).apply();startOverlay()}}
    private fun startOverlay(){val i=Intent(this,OverlayService::class.java);if(Build.VERSION.SDK_INT>=26)startForegroundService(i)else startService(i);Toast.makeText(this,"HOHA floating mode active",Toast.LENGTH_SHORT).show();moveTaskToBack(true)}
    private fun input(h:String,v:String)=EditText(this).apply{hint=h;setHintTextColor(Color.DKGRAY);setTextColor(Color.WHITE);textSize=13f;setSingleLine(true);setText(v);background=rounded(Color.rgb(24,24,24),dp(12).toFloat(),Color.rgb(60,60,60));setPadding(dp(14),0,dp(14),0)}
    private fun sectionTitle(t:String)=label(t,11f,true,Color.GRAY).apply{setPadding(dp(2),dp(18),0,dp(8))}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(14));background=rounded(Color.rgb(10,10,10),dp(16).toFloat(),Color.rgb(45,45,45))}
    private fun label(t:String,s:Float,b:Boolean=false,c:Int=Color.WHITE)=TextView(this).apply{text=t;textSize=s;setTextColor(c);if(b)setTypeface(typeface,Typeface.BOLD)}
    private fun rounded(fill:Int,r:Float,stroke:Int?=null)=GradientDrawable().apply{setColor(fill);cornerRadius=r;if(stroke!=null)setStroke(dp(1),stroke)}
    private fun circle(fill:Int)=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(fill);setStroke(dp(2),Color.rgb(130,130,130))}
    private fun monthKey()=SimpleDateFormat("yyyy-MM",Locale.US).format(Date())
    private fun usage():Int{val m=monthKey();if(prefs.getString("usage_month","")!=m)prefs.edit().putString("usage_month",m).putInt("usage",0).apply();return prefs.getInt("usage",0)}
    private fun addUsage(n:Int)=prefs.edit().putInt("usage",usage()+n.coerceAtLeast(1)).apply()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
