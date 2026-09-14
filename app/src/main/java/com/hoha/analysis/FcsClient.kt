package com.hoha.analysis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object FcsClient {
    fun history(accessKey: String, symbol: String, period: String, length: Int = 180): Pair<List<Candle>, Int> {
        val type = if (symbol.uppercase() == "XAUUSD" || symbol.uppercase() == "SILVER") "&type=commodity" else ""
        val u = "https://api-v4.fcsapi.com/forex/history?symbol=${enc(symbol)}&period=${enc(period)}&length=$length$isChart$type&access_key=${enc(accessKey)}"
        val conn = URL(u).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000; conn.readTimeout = 20000; conn.requestMethod = "GET"
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("FCS HTTP $code: ${body.take(220)}")
        val root = JSONObject(body)
        if (root.has("status") && !root.optBoolean("status", true)) throw IllegalStateException(root.optString("msg", "FCS request failed"))
        val response = root.opt("response") ?: root.opt("data") ?: root
        val candles = mutableListOf<Candle>()
        when (response) {
            is JSONObject -> { val keys = response.keys(); while (keys.hasNext()) { val k = keys.next(); val o = response.optJSONObject(k) ?: continue; if (!o.has("o") || !o.has("c")) continue; candles += Candle(o.optLong("t", k.toLongOrNull() ?: 0L), o.optDouble("o"), o.optDouble("h"), o.optDouble("l"), o.optDouble("c"), o.optDouble("v",0.0)) } }
            is JSONArray -> for(i in 0 until response.length()) { val o=response.optJSONObject(i) ?: continue; candles += Candle(o.optLong("t"),o.optDouble("o"),o.optDouble("h"),o.optDouble("l"),o.optDouble("c"),o.optDouble("v",0.0)) }
        }
        candles.sortBy { it.t }
        if (candles.size < 30) throw IllegalStateException("Not enough candle data returned (${candles.size})")
        val credits = root.optJSONObject("info")?.optInt("credit_count",1) ?: 1
        return candles to credits
    }
    private const val isChart = "&is_chart=0"
    private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
}
