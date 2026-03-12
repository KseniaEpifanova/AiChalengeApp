package com.example.aichalengeapp.mcp.currency

data class CurrencyToolIntent(
    val base: String,
    val target: String,
    val amount: Double?,
    val isSummary: Boolean = false
)
