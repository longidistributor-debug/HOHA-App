package com.hoha.analysis

import android.app.*
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.webkit.WebView
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.abs

class OverlayService:Service(){
 private lateinit var wm:WindowManager;private lateinit var bubble:TextView;private var panel:LinearLayout?=null;private var chart:WebView?=null;private var status:TextView?=null;private var counter:TextView?=null;private var symbol="EURUSD";private var period="15m";private val prefs by lazy{getSharedPreferences("hoha",MODE_PRIVATE)};private var busy=false
 override fun onBind(i:Intent?)=null
 override fun onCreate(){super.onCreate();wm=getSystemService(WINDOW_SERVICE) as WindowManager;foreground();bubble()}
 private fun foreground(){val id="hoha";if(Build.VERSION.SDK_INT>=26)(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(id,"HOHA",NotificationManager.IMPORTANCE_LOW));val n=(if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,id) else Notification.Builder(this)).setContentTitle("HOHA running").setContentText("Floating forex analysis active").setSmallIcon(android.R.drawable.ic_menu_compass).build();startForeground(109,n)}
 private fun type()=if(Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
 private fun bubble(){bubble=TextView(this).apply{text="H";textSize=24f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD);background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(Color.BLACK);setStroke(2,Color.WHITE)}};val p=WindowManager.LayoutParams(dp(58),dp(58),type(),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;x=20;y=250};bubble.setOnClickListener{if(panel==null)show()else hide()};wm.addView(bubble,p)}
 private fun show(){val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(12));background=GradientDrawable().apply{setColor(Color.rgb(8,8,8));cornerRadius=22f;setStroke(2,Color.WHITE)}};panel=b;b.addView(text("HOHA • HAMAD ANALYSIS",18f,true));val row=LinearLayout(this);val syms=arrayOf("EURUSD","GBPUSD","USDJPY","AUDUSD","USDCAD","USDCHF","NZDUSD","EURJPY","GBPJPY","XAUUSD");val tfs=arrayOf("5m","15m","30m","1h","4h","1D");val sp=Spinner(this).apply{adapter=ArrayAdapter(this@OverlayService,android.R.layout.simple_spinner_dropdown_item,syms)};val tf=Spinner(this).apply{adapter=ArrayAdapter(this@OverlayService,android.R.layout.simple_spinner_dropdown_item,tfs);setSelection(1)};row.addView(sp,LinearLayout.LayoutParams(0,dp(50),1f));row.addView(tf,LinearLayout.LayoutParams(0,dp(50),1f));b.addView(row);chart=WebView(this).apply{settings.javaScriptEnabled=true;setBackgroundColor(Color.BLACK);loadUrl("file:///android_asset/chart.html")};b.addView(chart,LinearLayout.LayoutParams(-1,dp(270)));counter=text("Calls: ${usage()}/500",12f,true);b.addView(counter);val run=Button(this).apply{text="RUN ANALYSIS";setTextColor(Color.BLACK);setBackgroundColor(Color.WHITE);setOnClickListener{analyze()}};b.addView(run,LinearLayout.LayoutParams(-1,dp(52)));status=text("Waiting for analysis…",12f);b.addView(status);sp.onItemSelectedListener=Sel{symbol=syms[it]};tf.onItemSelectedListener=Sel{period=tfs[it]};val p=WindowManager.LayoutParams(dp(350),dp(570),type(),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.END;x=10;y=80};wm.addView(b,p)}
 private fun hide(){panel?.let{wm.removeView(it)};panel=null;chart=null;status=null;counter=null}
 private fun analyze(){if(busy)return;val key=prefs.getString("api_key","")!!.trim();if(key.isBlank()){status?.text="API key missing";return};if(usage()>=500){status?.text="500/500 monthly calls reached";return};busy=true;status?.text="Analyzing $symbol $period…";thread{try{val(data,credits)=FcsClient.history(key,symbol,period,180);add(credits);val s=AnalysisEngine.analyze(data);Handler(Looper.getMainLooper()).post{counter?.text="Calls: ${usage()}/500";render(data,s);status?.text=format(s)}}catch(e:Exception){Handler(Looper.getMainLooper()).post{status?.text="Request failed: ${e.message}"}}finally{busy=false}}}
 private fun render(data:List<Candle>,s:Signal?){val a=JSONArray();data.takeLast(120).forEach{a.put(JSONObject().put("o",it.o).put("h",it.h).put("l",it.l).put("c",it.c))};chart?.evaluateJavascript("setData(${JSONObject.quote(a.toString())})",null);if(s==null)chart?.evaluateJavascript("setSignal(null)",null)else chart?.evaluateJavascript("setSignal(${JSONObject.quote(JSONObject().put("entry",s.entry).put("sl",s.sl).put("tp1",s.tp1).put("tp2",s.tp2).toString())})",null)}
 private fun format(s:Signal?):String{if(s==null)return "NO VALID SETUP\nWaiting for next analysis.";val d=if(abs(s.entry)>=100)2 else 5;fun f(v:Double)=String.format(Locale.US,"%.${d}f",v);return "${s.direction} • SCORE ${s.score}/100\nEntry ${f(s.entry)}\nSL ${f(s.sl)}\nTP1 ${f(s.tp1)}\nTP2 ${f(s.tp2)}\n"+s.reasons.joinToString("\n"){"✓ $it"}}
 private fun month()=SimpleDateFormat("yyyy-MM",Locale.US).format(Date());private fun usage():Int{val m=month();if(prefs.getString("usage_month","")!=m)prefs.edit().putString("usage_month",m).putInt("usage",0).apply();return prefs.getInt("usage",0)};private fun add(n:Int)=prefs.edit().putInt("usage",usage()+n).apply();private fun text(s:String,z:Float,b:Boolean=false)=TextView(this).apply{text=s;textSize=z;setTextColor(Color.WHITE);if(b)setTypeface(typeface,Typeface.BOLD)};private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 override fun onDestroy(){panel?.let{runCatching{wm.removeView(it)}};if(::bubble.isInitialized)runCatching{wm.removeView(bubble)};super.onDestroy()}
 private class Sel(val f:(Int)->Unit):AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:View?,i:Int,id:Long)=f(i);override fun onNothingSelected(p:AdapterView<*>?){}}
}
