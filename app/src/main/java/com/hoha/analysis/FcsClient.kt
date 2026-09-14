package com.hoha.analysis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object FcsClient {
    fun history(accessKey:String, ticker:String, symbol:String, type:String, period:String, length:Int=180):Pair<List<Candle>,Int>{
        val t=type.lowercase()
        val p=normalizePeriod(period)
        return when(t){
            "stock","fund","index","indices","structured","dr" -> fetch("stock",accessKey,ticker,p,length,t)
            "crypto","coin","futures","dex" -> fetch("crypto",accessKey,ticker,p,length,t)
            "commodity" -> try { fetch("forex",accessKey,ticker,p,length,"commodity") } catch(_:Exception){ fetch("forex",accessKey,symbol,p,length,"commodity") }
            else -> {
                try { fetch("forex",accessKey,ticker,p,length,"forex") }
                catch(_:Exception){ fetch("forex",accessKey,symbol,p,length,"forex",true) }
            }
        }
    }

    private fun fetch(group:String,key:String,symbol:String,period:String,length:Int,type:String,synthetic:Boolean=false):Pair<List<Candle>,Int>{
        val syn=if(synthetic) "&synthetic=1" else ""
        val u="https://api-v4.fcsapi.com/$group/history?symbol=${enc(symbol)}&period=${enc(period)}&length=$length&is_chart=0&type=${enc(type)}$syn&access_key=${enc(key)}"
        val c=URL(u).openConnection() as HttpURLConnection
        c.connectTimeout=15000
        c.readTimeout=20000
        c.requestMethod="GET"
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
            val tt=o.optLong("t",k.toLongOrNull()?:0L)
            candles+=Candle(tt,o.optDouble("o"),o.optDouble("h"),o.optDouble("l"),o.optDouble("c"),o.optDouble("v",0.0))
        }
        when(response){
            is JSONArray -> for(i in 0 until response.length()) response.optJSONObject(i)?.let{add(it)}
            is JSONObject -> {val it=response.keys();while(it.hasNext()){val k=it.next();response.optJSONObject(k)?.let{add(it,k)}}}
        }
        candles.sortBy{it.t}
        if(candles.size<20) throw IllegalStateException("Not enough candle data for $symbol on $period (${candles.size})")
        return candles to credits
    }

    private fun normalizePeriod(p:String):String = when(p.trim().lowercase()){
        "1m" -> "1m"; "5m" -> "5m"; "15m" -> "15m"; "30m" -> "30m"
        "1h" -> "1h"; "4h" -> "4h"; "1d" -> "1D"; "1w" -> "1W"; "1mo","1mth" -> "1M"
        else -> p
    }
    private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
}
