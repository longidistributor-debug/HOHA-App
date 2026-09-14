package com.hoha.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object AnalysisEngine {
    fun analyze(c: List<Candle>): Signal? {
        if (c.size < 60) return null
        val close=c.map{it.c}; val ema20=ema(close,20).last(); val ema50=ema(close,50).last(); val ema200=ema(close,min(200,c.size-1)).last(); val rsi=rsi(close,14); val atr=atr(c,14); val last=c.last(); val prev=c[c.lastIndex-1]
        val recent=c.takeLast(55); val swingHigh=recent.dropLast(2).maxOf{it.h}; val swingLow=recent.dropLast(2).minOf{it.l}; val priorHigh=c.takeLast(35).dropLast(6).maxOf{it.h}; val priorLow=c.takeLast(35).dropLast(6).minOf{it.l}
        var bull=0; var bear=0; val br=mutableListOf<String>(); val sr=mutableListOf<String>(); fun b(p:Int,r:String){bull+=p;br+=r}; fun s(p:Int,r:String){bear+=p;sr+=r}
        if(ema20>ema50)b(10,"EMA20 > EMA50") else s(10,"EMA20 < EMA50"); if(last.c>ema200)b(8,"Above long-term EMA") else s(8,"Below long-term EMA"); if(rsi in 50.0..68.0)b(7,"RSI bullish confirmation"); if(rsi in 32.0..50.0)s(7,"RSI bearish confirmation")
        if(last.c>priorHigh && last.c-prev.c>atr*.20)b(16,"Bullish BOS"); if(last.c<priorLow && prev.c-last.c>atr*.20)s(16,"Bearish BOS"); if(last.l<priorLow && last.c>priorLow)b(17,"Sell-side liquidity sweep"); if(last.h>priorHigh && last.c<priorHigh)s(17,"Buy-side liquidity sweep")
        val before=c.takeLast(28).dropLast(4); val up=before.takeLast(8).zipWithNext().count{it.second.c>it.first.c}>=5; val down=before.takeLast(8).zipWithNext().count{it.second.c<it.first.c}>=5; if(down&&last.c>before.takeLast(10).maxOf{it.h})b(14,"Bullish CHoCH"); if(up&&last.c<before.takeLast(10).minOf{it.l})s(14,"Bearish CHoCH")
        var bf=false;var sf=false;val fw=c.takeLast(12);for(i in 2 until fw.size){if(fw[i].l>fw[i-2].h)bf=true;if(fw[i].h<fw[i-2].l)sf=true};if(bf)b(10,"Bullish FVG");if(sf)s(10,"Bearish FVG")
        if(last.c-last.o>atr*.65&&c.takeLast(8).dropLast(1).any{it.c<it.o})b(9,"Bullish order-block context");if(last.o-last.c>atr*.65&&c.takeLast(8).dropLast(1).any{it.c>it.o})s(9,"Bearish order-block context");if(abs(last.c-swingLow)<=atr*1.2)b(6,"Near structural support");if(abs(last.c-swingHigh)<=atr*1.2)s(6,"Near structural resistance")
        val direction=if(bull>=bear)"BUY" else "SELL";val score=max(bull,bear).coerceAtMost(100);if(score<58||abs(bull-bear)<10)return null;val risk=max(atr*1.25,abs(last.c-prev.c)*1.2);val entry=last.c;val sl=if(direction=="BUY")entry-risk else entry+risk;val tp1=if(direction=="BUY")entry+risk*1.5 else entry-risk*1.5;val tp2=if(direction=="BUY")entry+risk*2.4 else entry-risk*2.4;return Signal(direction,entry,sl,tp1,tp2,score,4,(if(direction=="BUY")br else sr).take(6))
    }
    fun isInvalid(signal:Signal,latest:Candle)=if(signal.direction=="BUY")latest.c<signal.sl else latest.c>signal.sl
    private fun ema(v:List<Double>,p:Int):List<Double>{val k=2.0/(p+1);val o=MutableList(v.size){0.0};o[0]=v[0];for(i in 1 until v.size)o[i]=v[i]*k+o[i-1]*(1-k);return o}
    private fun rsi(v:List<Double>,p:Int):Double{if(v.size<=p)return 50.0;var g=0.0;var l=0.0;for(i in v.size-p until v.size){val d=v[i]-v[i-1];if(d>0)g+=d else l-=d};if(l==0.0)return 100.0;val rs=g/l;return 100.0-(100.0/(1+rs))}
    private fun atr(c:List<Candle>,p:Int):Double{val start=max(1,c.size-p);var sum=0.0;var n=0;for(i in start until c.size){sum+=max(c[i].h-c[i].l,max(abs(c[i].h-c[i-1].c),abs(c[i].l-c[i-1].c)));n++};return if(n==0)0.0 else sum/n}
}
