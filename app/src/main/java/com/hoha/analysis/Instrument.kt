package com.hoha.analysis

data class Instrument(
    val ticker: String,
    val symbol: String,
    val name: String,
    val type: String,
    val exchange: String = ""
)
