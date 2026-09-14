package com.hoha.analysis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object FcsClient {
    fun history(accessKey:String, ticker:String, symbol:String, type:String, period:String, length:Int=180):Pair<List<Candle>,Int>{
        val t=type.lowercase()
        return when(t){
            "stock","fund","index","structured","dr" -> fetch("stock",accessKey,ticker,period,length,t)
            "crypto","coin","futures","dex" -> fetch("crypto",accessKey,ticker,period,length,t)
            "commodity" -> fetch("forex",accessKey,symbol,period,length,"commodity")
            else -> {
                try { fetch("forex",accessKey,symbol,period,length,"forex") }
                catch(e:Exception){ fetch("forex",accessKey,symbol,period,length,"commodity") }
            }
        }
    }

    private fun fetch(group:String,key:String,symbol:String,period:String,length:Int,type:String):Pair<List<Candle>,Int>{
        val synthetic=if(group=="forex"&&type=="forex") "&synthetic=1" else ""
        val u="https://api-v4.fcsapi.com/$group/history?symbol=${enc(symbol)}&period=${enc(period)}&length=$length&is_chart=0&type=${enc(type)}$synthetic&access_key=${enc(key)}"
        val c=URL(u).openConnection() as HttpURLConnection
        c.connectTimeout=15000;c.readTimeout=20000;c.requestMethod="GET"
        val code=c.responseCode
        val body=(if(code in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()}
        if(code !in 200..299) throw IllegalStateException("FCS HTTP $code: ${body.take(180)}")
        val root=JSONObject(body)
        if(root.has("status")&&!root.optBoolean("status",true)) throw IllegalStateException(root.optString("msg","FCS request failed"))
        val credits=root.optJSONObject("info")?.optInt("credit_count",1)?:1
        val response=root.opt("response")?:root.opt("data")?:root
        val candles=mutableListOf<Candle>()
        fun add(o:JSONObject,k:String=""){
            if(!o.has("o")||!o.has("c"))return
            candles+=Candle(o.optLong("t",k.toLongOrNull()?:0L),o.optDouble("o"),o.optDouble("h"),o.optDouble("l"),o.optDouble("c"),o.optDouble("v",0.0))
        }
        when(response){
            is JSONArray -> for(i in 0 until response.length()) response.optJSONObject(i)?.let{add(it)}
            is JSONObject -> {val it=response.keys();while(it.hasNext()){val k=it.next();response.optJSONObject(k)?.let{add(it,k)}}}
        }
        candles.sortBy{it.t}
        if(candles.size<30) throw IllegalStateException("Not enough candle data returned for $symbol (${candles.size})")
        return candles to credits
    }
    private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
}
