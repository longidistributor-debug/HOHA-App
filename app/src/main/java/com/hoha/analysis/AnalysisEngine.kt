package com.hoha.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object AnalysisEngine {
    fun analyze(c: List<Candle>): Signal? {
        if (c.size < 60) return null
        val close = c.map { it.c }
        val last = c.last()
        val prev = c[c.lastIndex - 1]
        val ema20 = ema(close, 20).last()
        val ema50 = ema(close, 50).last()
        val emaLong = ema(close, min(150, c.size - 1)).last()
        val rsi = rsi(close, 14)
        val atr = atr(c, 14).coerceAtLeast(1e-9)
        val macdNow = ema(close, 12).last() - ema(close, 26).last()
        val macdPrev = ema(close.dropLast(1), 12).last() - ema(close.dropLast(1), 26).last()
        val recent = c.takeLast(55)
        val swingHigh = recent.dropLast(2).maxOf { it.h }
        val swingLow = recent.dropLast(2).minOf { it.l }
        val prior = c.takeLast(35).dropLast(4)
        val priorHigh = prior.maxOf { it.h }
        val priorLow = prior.minOf { it.l }
        val basis = close.takeLast(20).average()
        val sd = stdev(close.takeLast(20))
        val upper = basis + 2 * sd
        val lower = basis - 2 * sd

        var bull = 0
        var bear = 0
        val bullReasons = mutableListOf<String>()
        val bearReasons = mutableListOf<String>()
        fun b(points: Int, reason: String) { bull += points; bullReasons += reason }
        fun s(points: Int, reason: String) { bear += points; bearReasons += reason }

        // Trend / momentum
        if (ema20 > ema50) b(12, "EMA20 above EMA50") else s(12, "EMA20 below EMA50")
        if (last.c > emaLong) b(7, "Price above long EMA") else s(7, "Price below long EMA")
        if (ema20 > ema(close.dropLast(1), 20).last()) b(5, "EMA20 rising") else s(5, "EMA20 falling")
        if (rsi >= 54) b(8, "RSI bullish ${rsi.toInt()}") else if (rsi <= 46) s(8, "RSI bearish ${rsi.toInt()}")
        if (macdNow > 0) b(7, "MACD above zero") else s(7, "MACD below zero")
        if (macdNow > macdPrev) b(5, "MACD momentum rising") else s(5, "MACD momentum falling")

        // Structure / breakout / sweeps
        if (last.c > priorHigh) b(16, "Bullish BOS / breakout")
        if (last.c < priorLow) s(16, "Bearish BOS / breakout")
        if (last.l < priorLow && last.c > priorLow) b(16, "Sell-side liquidity sweep")
        if (last.h > priorHigh && last.c < priorHigh) s(16, "Buy-side liquidity sweep")

        val before = c.takeLast(30).dropLast(3)
        val upSeq = before.takeLast(8).zipWithNext().count { it.second.c > it.first.c } >= 5
        val downSeq = before.takeLast(8).zipWithNext().count { it.second.c < it.first.c } >= 5
        if (downSeq && last.c > before.takeLast(10).maxOf { it.h }) b(13, "Bullish CHoCH")
        if (upSeq && last.c < before.takeLast(10).minOf { it.l }) s(13, "Bearish CHoCH")

        // FVG approximation
        var bullFvg = false
        var bearFvg = false
        val fw = c.takeLast(14)
        for (i in 2 until fw.size) {
            if (fw[i].l > fw[i - 2].h) bullFvg = true
            if (fw[i].h < fw[i - 2].l) bearFvg = true
        }
        if (bullFvg) b(8, "Bullish FVG present")
        if (bearFvg) s(8, "Bearish FVG present")

        // Order-block context approximation
        if (last.c - last.o > atr * 0.45 && c.takeLast(7).dropLast(1).any { it.c < it.o }) b(8, "Bullish displacement / OB context")
        if (last.o - last.c > atr * 0.45 && c.takeLast(7).dropLast(1).any { it.c > it.o }) s(8, "Bearish displacement / OB context")

        // Support/resistance and volatility bands
        if (abs(last.c - swingLow) <= atr * 1.25) b(7, "Near structural support")
        if (abs(last.c - swingHigh) <= atr * 1.25) s(7, "Near structural resistance")
        if (last.c <= lower) b(6, "Near lower Bollinger band")
        if (last.c >= upper) s(6, "Near upper Bollinger band")

        // Recent candle impulse / micro momentum
        val recent3 = c.takeLast(4)
        val momentum = recent3.last().c - recent3.first().c
        if (momentum > atr * 0.35) b(6, "Short-term momentum bullish")
        if (momentum < -atr * 0.35) s(6, "Short-term momentum bearish")

        val direction = if (bull >= bear) "BUY" else "SELL"
        val winning = max(bull, bear)
        val losing = min(bull, bear)
        val separation = winning - losing

        // Weighted confluence: do NOT require every condition. Strong setups need several independent confirmations.
        if (winning < 38 || separation < 5) return null

        val score = (50 + separation * 2 + (winning - 38) / 2).coerceIn(50, 96)
        val risk = max(atr * 1.20, abs(last.c - prev.c) * 1.5)
        val entry = last.c
        val sl = if (direction == "BUY") entry - risk else entry + risk
        val tp1 = if (direction == "BUY") entry + risk * 1.35 else entry - risk * 1.35
        val tp2 = if (direction == "BUY") entry + risk * 2.20 else entry - risk * 2.20
        val reasons = (if (direction == "BUY") bullReasons else bearReasons).take(8)
        return Signal(direction, entry, sl, tp1, tp2, score, 4, reasons)
    }

    fun isInvalid(signal: Signal, latest: Candle): Boolean =
        if (signal.direction == "BUY") latest.c < signal.sl else latest.c > signal.sl

    private fun ema(v: List<Double>, p: Int): List<Double> {
        val period = p.coerceAtMost(v.size.coerceAtLeast(1))
        val k = 2.0 / (period + 1)
        val out = MutableList(v.size) { 0.0 }
        if (v.isEmpty()) return out
        out[0] = v[0]
        for (i in 1 until v.size) out[i] = v[i] * k + out[i - 1] * (1 - k)
        return out
    }

    private fun rsi(v: List<Double>, p: Int): Double {
        if (v.size <= p) return 50.0
        var g = 0.0
        var l = 0.0
        for (i in v.size - p until v.size) {
            val d = v[i] - v[i - 1]
            if (d > 0) g += d else l -= d
        }
        if (l == 0.0) return 100.0
        val rs = g / l
        return 100.0 - (100.0 / (1 + rs))
    }

    private fun atr(c: List<Candle>, p: Int): Double {
        val start = max(1, c.size - p)
        var sum = 0.0
        var n = 0
        for (i in start until c.size) {
            sum += max(c[i].h - c[i].l, max(abs(c[i].h - c[i - 1].c), abs(c[i].l - c[i - 1].c)))
            n++
        }
        return if (n == 0) 0.0 else sum / n
    }

    private fun stdev(v: List<Double>): Double {
        if (v.isEmpty()) return 0.0
        val mean = v.average()
        return sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size)
    }
}
