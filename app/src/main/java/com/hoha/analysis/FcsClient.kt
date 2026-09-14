package com.hoha.analysis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object FcsClient {
    fun history(accessKey: String, symbol: String, period: String, length: Int = 180): Pair<List<Candle>, Int> {
        val clean = symbol.substringAfter(':').replace("/", "").replace(" ", "").uppercase()
        val commodityFirst = isCommodityHint(clean)
        val types = if (commodityFirst) listOf("commodity", "forex") else listOf("forex", "commodity")
        var usedCredits = 0
        var lastError = "Instrument unavailable"

        for (type in types) {
            try {
                val result = fetchHistory(accessKey, clean, period, length, type)
                usedCredits += result.second.coerceAtLeast(1)
                if (result.first.size >= 30) return result.first to usedCredits
                lastError = "Not enough candle data for $clean as $type"
            } catch (e: Exception) {
                usedCredits += 1
                lastError = e.message ?: lastError
            }
        }
        throw IllegalStateException("$clean is not available from FCS history. $lastError")
    }

    private fun fetchHistory(accessKey: String, symbol: String, period: String, length: Int, type: String): Pair<List<Candle>, Int> {
        val u = "https://api-v4.fcsapi.com/forex/history?symbol=${enc(symbol)}&period=${enc(period)}&length=$length&is_chart=0&synthetic=1&type=${enc(type)}&access_key=${enc(accessKey)}"
        val conn = URL(u).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.requestMethod = "GET"
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("FCS HTTP $code: ${body.take(180)}")
        val root = JSONObject(body)
        val credits = root.optJSONObject("info")?.optInt("credit_count", 1) ?: 1
        if (root.has("status") && !root.optBoolean("status", true)) {
            throw IllegalStateException(root.optString("msg", "FCS request failed"))
        }
        val response = root.opt("response") ?: root.opt("data") ?: root
        val candles = mutableListOf<Candle>()
        when (response) {
            is JSONObject -> {
                val keys = response.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val o = response.optJSONObject(k) ?: continue
                    if (!o.has("o") || !o.has("c")) continue
                    candles += Candle(
                        o.optLong("t", k.toLongOrNull() ?: 0L),
                        o.optDouble("o"), o.optDouble("h"), o.optDouble("l"), o.optDouble("c"), o.optDouble("v", 0.0)
                    )
                }
            }
            is JSONArray -> for (i in 0 until response.length()) {
                val o = response.optJSONObject(i) ?: continue
                if (!o.has("o") || !o.has("c")) continue
                candles += Candle(o.optLong("t"), o.optDouble("o"), o.optDouble("h"), o.optDouble("l"), o.optDouble("c"), o.optDouble("v", 0.0))
            }
        }
        candles.sortBy { it.t }
        return candles to credits
    }

    private fun isCommodityHint(s: String): Boolean {
        return s.startsWith("XAU") || s.startsWith("XAG") || s in setOf(
            "GOLD", "SILVER", "WTI", "BRENT", "OSX", "NGAS", "COPPER", "PLATINUM", "PALLADIUM"
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
