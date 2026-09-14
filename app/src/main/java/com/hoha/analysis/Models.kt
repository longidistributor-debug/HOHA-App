package com.hoha.analysis

data class Candle(val t: Long, val o: Double, val h: Double, val l: Double, val c: Double, val v: Double = 0.0)

data class Signal(
    val direction: String,
    val entry: Double,
    val sl: Double,
    val tp1: Double,
    val tp2: Double,
    val score: Int,
    val validBars: Int,
    val reasons: List<String>
)
