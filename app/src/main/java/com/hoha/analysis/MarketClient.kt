package com.hoha.analysis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MarketClient {
    fun browse(accessKey:String, category:String, query:String=""):List<Instrument>{
        val q=query.trim()
        return when(category.lowercase()){
            "forex" -> fetchForex(accessKey,"forex",q)
            "commodity" -> fetchForex(accessKey,"commodity",q)
            "crypto" -> fetchCrypto(accessKey,"crypto",q)
            "futures" -> fetchCrypto(accessKey,"futures",q)
            "dex" -> fetchCrypto(accessKey,"dex",q)
            "stock" -> fetchStock(accessKey,"stock",q)
            "fund" -> fetchStock(accessKey,"fund",q)
            "index" -> fetchStock(accessKey,"index",q)
            else -> {
                val out=mutableListOf<Instrument>()
                out += fetchForex(accessKey,"forex",q,25)
                out += fetchForex(accessKey,"commodity",q,25)
                out += fetchCrypto(accessKey,"crypto",q,25)
                out += fetchStock(accessKey,"stock",q,25)
                out.distinctBy{it.ticker}.take(100)
            }
        }
    }

    private fun fetchForex(key:String,type:String,q:String,limit:Int=80):List<Instrument>{
        val path=if(q.isBlank()) "forex/list?type=${enc(type)}&page=1&per_page=$limit" else "forex/search?search=${enc(q)}&type=${enc(type)}&page=1&per_page=$limit"
        return parse(request(path,key),type,limit)
    }
    private fun fetchCrypto(key:String,type:String,q:String,limit:Int=80):List<Instrument>{
        val path=if(q.isBlank()) "crypto/list?type=${enc(type)}&page=1&per_page=$limit" else "crypto/search?search=${enc(q)}&type=${enc(type)}&page=1&per_page=$limit"
        return parse(request(path,key),type,limit)
    }
    private fun fetchStock(key:String,type:String,q:String,limit:Int=80):List<Instrument>{
        val path=if(q.isBlank()) "stock/list?type=${enc(type)}&page=1&per_page=$limit" else "stock/search?search=${enc(q)}&type=${enc(type)}&page=1&per_page=$limit"
        return parse(request(path,key),type,limit)
    }

    private fun request(path:String,key:String):JSONObject{
        val sep=if(path.contains("?")) "&" else "?"
        val u="https://api-v4.fcsapi.com/$path${sep}access_key=${enc(key)}"
        val c=URL(u).openConnection() as HttpURLConnection
        c.connectTimeout=15000;c.readTimeout=20000;c.requestMethod="GET"
        val code=c.responseCode
        val body=(if(code in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()}
        if(code !in 200..299) throw IllegalStateException("HTTP $code")
        val j=JSONObject(body)
        if(j.has("status")&&!j.optBoolean("status",true)) throw IllegalStateException(j.optString("msg","Market list failed"))
        return j
    }

    private fun parse(root:JSONObject,fallbackType:String,limit:Int):List<Instrument>{
        val response=root.opt("response") ?: root.opt("data") ?: JSONArray()
        val arr=when(response){
            is JSONArray -> response
            is JSONObject -> {
                val a=JSONArray();val it=response.keys();while(it.hasNext()){val k=it.next();response.optJSONObject(k)?.let{a.put(it)}};a
            }
            else -> JSONArray()
        }
        val out=mutableListOf<Instrument>()
        for(i in 0 until arr.length()){
            val raw=arr.optJSONObject(i)?:continue
            val profile=raw.optJSONObject("profile")?:raw
            val symbol=(profile.optString("symbol").ifBlank{raw.optString("symbol")}).uppercase()
            if(symbol.isBlank()) continue
            val exchange=(profile.optString("exchange").ifBlank{raw.optString("exchange")}).uppercase()
            val ticker=raw.optString("ticker").ifBlank{ if(exchange.isNotBlank()) "$exchange:$symbol" else symbol }
            val name=profile.optString("name").ifBlank{raw.optString("name").ifBlank{symbol}}
            val type=profile.optString("type").ifBlank{raw.optString("type").ifBlank{fallbackType}}
            out += Instrument(ticker.uppercase(),symbol,name,type.lowercase(),exchange)
            if(out.size>=limit) break
        }
        return out
    }
    private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
}
