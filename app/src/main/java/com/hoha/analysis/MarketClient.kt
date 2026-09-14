package com.hoha.analysis

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MarketClient {
    fun browse(accessKey:String, category:String, query:String=""):Pair<List<Instrument>,Int>{
        val q=query.trim()
        return when(category.lowercase()){
            "forex" -> fetchGroup("forex","forex",accessKey,q,120) to 1
            "commodity" -> fetchGroup("forex","commodity",accessKey,q,120) to 1
            "crypto" -> fetchGroup("crypto","crypto",accessKey,q,120) to 1
            "futures" -> fetchGroup("crypto","futures",accessKey,q,120) to 1
            "dex" -> fetchGroup("crypto","dex",accessKey,q,120) to 1
            "stock" -> fetchGroup("stock","stock",accessKey,q,120) to 1
            "fund" -> fetchGroup("stock","fund",accessKey,q,120) to 1
            "index" -> fetchGroup("stock","index",accessKey,q,120) to 1
            else -> {
                val out=mutableListOf<Instrument>()
                out += fetchGroup("forex","forex",accessKey,q,40)
                out += fetchGroup("forex","commodity",accessKey,q,40)
                out += fetchGroup("crypto","crypto",accessKey,q,40)
                out += fetchGroup("stock","stock",accessKey,q,40)
                out.distinctBy{it.ticker}.take(160) to 4
            }
        }
    }

    private fun fetchGroup(group:String,type:String,key:String,q:String,limit:Int):List<Instrument>{
        val path=if(q.isBlank()) {
            "$group/list?type=${enc(type)}&page=1&per_page=$limit"
        } else {
            "$group/search?search=${enc(q)}&type=${enc(type)}&page=1&per_page=$limit"
        }
        return parse(request(path,key),type,limit)
    }

    private fun request(path:String,key:String):Any{
        val sep=if(path.contains("?")) "&" else "?"
        val u="https://api-v4.fcsapi.com/$path${sep}access_key=${enc(key)}"
        val c=URL(u).openConnection() as HttpURLConnection
        c.connectTimeout=15000
        c.readTimeout=20000
        c.requestMethod="GET"
        val code=c.responseCode
        val body=(if(code in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()}
        if(code !in 200..299) throw IllegalStateException("FCS HTTP $code: ${body.take(160)}")
        val parsed=JSONTokener(body).nextValue()
        if(parsed is JSONObject && parsed.has("status") && !parsed.optBoolean("status",true)) {
            throw IllegalStateException(parsed.optString("msg","Market list failed"))
        }
        return parsed
    }

    private fun parse(root:Any,fallbackType:String,limit:Int):List<Instrument>{
        val arr:JSONArray = when(root){
            is JSONArray -> root
            is JSONObject -> when(val r=root.opt("response") ?: root.opt("data")){
                is JSONArray -> r
                is JSONObject -> {
                    val a=JSONArray(); val it=r.keys()
                    while(it.hasNext()){ val k=it.next(); r.optJSONObject(k)?.let{a.put(it)} }
                    a
                }
                else -> JSONArray()
            }
            else -> JSONArray()
        }
        val out=mutableListOf<Instrument>()
        for(i in 0 until arr.length()){
            val raw=arr.optJSONObject(i)?:continue
            val profile=raw.optJSONObject("profile") ?: raw
            val symbol=(profile.optString("symbol").ifBlank{raw.optString("symbol")}).uppercase()
            if(symbol.isBlank()) continue
            val exchange=(profile.optString("exchange").ifBlank{raw.optString("exchange")}).uppercase()
            val rawTicker=raw.optString("ticker").uppercase()
            val ticker=when {
                rawTicker.contains(":") -> rawTicker
                exchange.isNotBlank() -> "$exchange:$symbol"
                else -> symbol
            }
            val name=profile.optString("name").ifBlank{raw.optString("name").ifBlank{symbol}}
            val type=profile.optString("type").ifBlank{raw.optString("type").ifBlank{fallbackType}}.lowercase()
            out += Instrument(ticker,symbol,name,type,exchange)
            if(out.size>=limit) break
        }
        return out.distinctBy{it.ticker}
    }

    private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
}
