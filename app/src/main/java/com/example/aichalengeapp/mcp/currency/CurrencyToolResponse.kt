package com.example.aichalengeapp.mcp.currency

sealed class CurrencyToolResponse {
    data class Success(
        val base: String,
        val target: String,
        val amountRequested: Double?,
        val convertedAmount: Double?,
        val rate: Double?,
        val date: String?
    ) : CurrencyToolResponse()

    data class InvalidCurrency(
        val base: String,
        val target: String
    ) : CurrencyToolResponse()

    data class Failure(val message: String) : CurrencyToolResponse()
}
