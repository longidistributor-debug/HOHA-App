package com.hoha.analysis

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var bubble: TextView
    private var panel: LinearLayout? = null
    private var chart: WebView? = null
    private var status: TextView? = null
    private var counter: TextView? = null
    private var symbol = "EURUSD"
    private var period = "15m"
    private var chartReady = false
    private var busy = false
    private val prefs by lazy { getSharedPreferences("hoha", MODE_PRIVATE) }

    override fun onBind(intent: Intent?) = null
    override fun onCreate() { super.onCreate(); wm = getSystemService(WINDOW_SERVICE) as WindowManager; foreground(); createBubble() }

    private fun foreground() {
        val id="hoha"
        if(Build.VERSION.SDK_INT>=26)(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(id,"HOHA",NotificationManager.IMPORTANCE_LOW))
        val n=(if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,id) else Notification.Builder(this)).setContentTitle("HOHA running").setContentText("Floating forex analysis active").setSmallIcon(android.R.drawable.ic_menu_compass).build()
        startForeground(109,n)
    }

    private fun type()=if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun createBubble(){
        bubble=TextView(this).apply{text="H";textSize=24f;gravity=Gravity.CENTER;setTextColor(Color.BLACK);setTypeface(typeface,Typeface.BOLD);elevation=dp(8).toFloat();background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(Color.WHITE);setStroke(dp(2),Color.GRAY)}}
        val p=WindowManager.LayoutParams(dp(58),dp(58),type(),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;x=dp(18);y=dp(220)}
        var sx=0;var sy=0;var tx=0f;var ty=0f;var moved=false
        bubble.setOnTouchListener { _,e -> when(e.action){
            MotionEvent.ACTION_DOWN->{sx=p.x;sy=p.y;tx=e.rawX;ty=e.rawY;moved=false;true}
            MotionEvent.ACTION_MOVE->{val dx=(e.rawX-tx).toInt();val dy=(e.rawY-ty).toInt();if(abs(dx)>dp(4)||abs(dy)>dp(4))moved=true;p.x=sx+dx;p.y=sy+dy;wm.updateViewLayout(bubble,p);true}
            MotionEvent.ACTION_UP->{if(!moved)toggle();true}
            else->false}}
        wm.addView(bubble,p)
    }

    private fun toggle(){if(panel==null)showPanel()else hidePanel()}

    private fun showPanel(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(12));background=GradientDrawable().apply{setColor(Color.rgb(8,8,8));cornerRadius=dp(18).toFloat();setStroke(dp(1),Color.GRAY)}};panel=root
        val titleRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};titleRow.addView(text("HOHA • HAMAD ANALYSIS",16f,true),LinearLayout.LayoutParams(0,dp(42),1f));titleRow.addView(Button(this).apply{text="—";setOnClickListener{hidePanel()}},LinearLayout.LayoutParams(dp(48),dp(40)));root.addView(titleRow)
        val symbols=arrayOf("EURUSD","GBPUSD","USDJPY","AUDUSD","USDCAD","USDCHF","NZDUSD","EURJPY","GBPJPY","XAUUSD");val periods=arrayOf("5m","15m","30m","1h","4h","1D")
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};val sp=Spinner(this).apply{adapter=ArrayAdapter(this@OverlayService,android.R.layout.simple_spinner_dropdown_item,symbols)};val tf=Spinner(this).apply{adapter=ArrayAdapter(this@OverlayService,android.R.layout.simple_spinner_dropdown_item,periods);setSelection(1)};row.addView(sp,LinearLayout.LayoutParams(0,dp(48),1f));row.addView(tf,LinearLayout.LayoutParams(0,dp(48),1f));root.addView(row)
        chart=WebView(this).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;setBackgroundColor(Color.BLACK);webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView?,url:String?){chartReady=true;initChart()}};loadUrl("file:///android_asset/chart.html")};root.addView(chart,LinearLayout.LayoutParams(-1,dp(300)))
        counter=text("Calls: ${usage()}/500",11f,true);root.addView(counter)
        val run=Button(this).apply{text="RUN WEIGHTED ANALYSIS";setTextColor(Color.BLACK);setBackgroundColor(Color.WHITE);setOnClickListener{analyze()}};root.addView(run,LinearLayout.LayoutParams(-1,dp(50)))
        status=text("Interactive chart ready. Run analysis for Entry / SL / TP.",11f);status?.setPadding(0,dp(8),0,0);root.addView(status)
        sp.onItemSelectedListener=Sel{symbol=symbols[it];changeMarket()};tf.onItemSelectedListener=Sel{period=periods[it];changeMarket()}
        val p=WindowManager.LayoutParams(dp(360),dp(610),type(),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.END;x=dp(8);y=dp(55)};wm.addView(root,p)
    }

    private fun initChart(){if(!chartReady)return;val key=prefs.getString("api_key","").orEmpty();if(key.isBlank())return;val socket=prefs.getString("socket_key","").orEmpty();chart?.evaluateJavascript("initLiveChart(${JSONObject.quote(key)},${JSONObject.quote(symbol)},${JSONObject.quote(period)},${JSONObject.quote(socket)})",null)}
    private fun changeMarket(){if(chartReady)chart?.evaluateJavascript("changeMarket(${JSONObject.quote(symbol)},${JSONObject.quote(period)})",null)}
    private fun hidePanel(){panel?.let{runCatching{wm.removeView(it)}};panel=null;chart=null;status=null;counter=null;chartReady=false}

    private fun analyze(){
        if(busy)return;val key=prefs.getString("api_key","").orEmpty().trim();if(key.isBlank()){status?.text="API key missing";return};if(usage()>=500){status?.text="500/500 monthly calls reached";return};busy=true;status?.text="Scoring $symbol $period..."
        thread{try{val(data,credits)=FcsClient.history(key,symbol,period,180);add(credits);val s=AnalysisEngine.analyze(data);Handler(Looper.getMainLooper()).post{counter?.text="Calls: ${usage()}/500";showSignal(s);status?.text=format(s);busy=false}}catch(e:Exception){Handler(Looper.getMainLooper()).post{status?.text="Request failed: ${e.message}";busy=false}}}
    }

    private fun showSignal(s:Signal?){if(s==null){chart?.evaluateJavascript("setSignal(null)",null);return};val j=JSONObject().put("entry",s.entry).put("sl",s.sl).put("tp1",s.tp1).put("tp2",s.tp2);chart?.evaluateJavascript("setSignal(${JSONObject.quote(j.toString())})",null)}
    private fun format(s:Signal?):String{if(s==null)return "WAIT / NO EDGE\nBUY and SELL evidence is too balanced.";val d=if(abs(s.entry)>=100)2 else 5;fun f(v:Double)=String.format(Locale.US,"%.${d}f",v);val conf=when{ s.score>=80->"HIGH";s.score>=65->"MEDIUM";else->"EARLY"};return "${s.direction} • $conf • ${s.score}/100\nEntry ${f(s.entry)}\nSL ${f(s.sl)}\nTP1 ${f(s.tp1)}\nTP2 ${f(s.tp2)}\n"+s.reasons.take(6).joinToString("\n"){"✓ $it"}}
    private fun month()=SimpleDateFormat("yyyy-MM",Locale.US).format(Date());private fun usage():Int{val m=month();if(prefs.getString("usage_month","")!=m)prefs.edit().putString("usage_month",m).putInt("usage",0).apply();return prefs.getInt("usage",0)};private fun add(n:Int)=prefs.edit().putInt("usage",usage()+n.coerceAtLeast(1)).apply();private fun text(s:String,z:Float,b:Boolean=false)=TextView(this).apply{text=s;textSize=z;setTextColor(Color.WHITE);if(b)setTypeface(typeface,Typeface.BOLD)};private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onDestroy(){hidePanel();if(::bubble.isInitialized)runCatching{wm.removeView(bubble)};super.onDestroy()}
    private class Sel(val f:(Int)->Unit):AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:View?,i:Int,id:Long)=f(i);override fun onNothingSelected(p:AdapterView<*>?){}}
}
