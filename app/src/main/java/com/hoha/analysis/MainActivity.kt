package com.hoha.analysis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("hoha", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(Build.VERSION.SDK_INT>=33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),10)
        val root=LinearLayout(this).apply{ orientation=LinearLayout.VERTICAL; setPadding(28,42,28,28); setBackgroundColor(Color.BLACK) }
        fun tv(text:String,size:Float,bold:Boolean=false)=TextView(this).apply{ this.text=text; textSize=size; setTextColor(Color.WHITE); if(bold) setTypeface(typeface,1) }
        root.addView(tv("HOHA",34f,true)); root.addView(tv("Hamad Analysis • Floating Forex Assistant",15f), lp(-1,-2,0,10)); root.addView(tv("No login required. Enter a fresh FCS Access Key once; it is stored only in this app's local preferences.",13f),lp(-1,-2,0,18))
        val key=EditText(this).apply{ hint="FCS Access Key"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); setText(prefs.getString("api_key","")); background=rounded("#151515",18f) }
        root.addView(key,lp(-1,58,0,12))
        val save=Button(this).apply{text="SAVE KEY"; setTextColor(Color.BLACK); background=rounded("#FFFFFF",18f)}; save.setOnClickListener { prefs.edit().putString("api_key",key.text.toString().trim()).apply(); Toast.makeText(this,"Key saved locally",Toast.LENGTH_SHORT).show() }; root.addView(save,lp(-1,56,0,12))
        val start=Button(this).apply{text="START HOHA FLOAT"; setTextColor(Color.WHITE); background=rounded("#202020",18f)}
        start.setOnClickListener { val k=key.text.toString().trim(); if(k.isBlank()){Toast.makeText(this,"Enter your FCS Access Key first",Toast.LENGTH_LONG).show();return@setOnClickListener}; prefs.edit().putString("api_key",k).apply(); if(!Settings.canDrawOverlays(this)){ prefs.edit().putBoolean("pending_start",true).apply(); startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } else startOverlay() }
        root.addView(start,lp(-1,58,0,12)); root.addView(tv("V1 analysis: EMA trend, RSI, ATR, structure/BOS proxy, CHoCH proxy, liquidity sweep, FVG, order-block context and support/resistance. Signals are probabilistic, not guaranteed.",12f),lp(-1,-2,0,12)); setContentView(ScrollView(this).apply{addView(root)})
    }
    override fun onResume(){super.onResume(); if(Settings.canDrawOverlays(this) && prefs.getBoolean("pending_start",false)){prefs.edit().putBoolean("pending_start",false).apply();startOverlay()}}
    private fun startOverlay(){ val i=Intent(this,OverlayService::class.java); if(Build.VERSION.SDK_INT>=26) startForegroundService(i) else startService(i); moveTaskToBack(true) }
    private fun rounded(hex:String,r:Float)=GradientDrawable().apply{setColor(Color.parseColor(hex));cornerRadius=r}
    private fun lp(w:Int,h:Int,top:Int=0,bottom:Int=0)=LinearLayout.LayoutParams(if(w==-1)ViewGroup.LayoutParams.MATCH_PARENT else w, if(h==-2)ViewGroup.LayoutParams.WRAP_CONTENT else h).apply{setMargins(0,top,0,bottom)}
}
