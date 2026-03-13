package com.example.aichalengeapp.mcp.currency

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyMcpToolHandler @Inject constructor(
    private val frankfurterApi: FrankfurterApi
) {
    suspend fun getExchangeRate(
        base: String,
        target: String,
        amount: Double?
    ): CurrencyToolResponse {
        val normalizedBase = base.trim().uppercase()
        val normalizedTarget = target.trim().uppercase()
        if (!isValidCode(normalizedBase) || !isValidCode(normalizedTarget)) {
            return CurrencyToolResponse.InvalidCurrency(normalizedBase, normalizedTarget)
        }
        if (amount != null && amount <= 0.0) {
            return CurrencyToolResponse.Failure("Amount must be greater than zero")
        }

        val quote = frankfurterApi.latest(
            base = normalizedBase,
            target = normalizedTarget,
            amount = amount
        ).getOrElse { throwable ->
            val msg = throwable.message.orEmpty().lowercase()
            if (msg.contains("invalid") || msg.contains("currency")) {
                return CurrencyToolResponse.InvalidCurrency(normalizedBase, normalizedTarget)
            }
            return CurrencyToolResponse.Failure(throwable.message ?: "Currency request failed")
        }

        val amountRequested = amount ?: 1.0
        val rate = if (amountRequested == 0.0) null else quote.convertedAmount / amountRequested

        return CurrencyToolResponse.Success(
            base = quote.base,
            target = quote.target,
            amountRequested = quote.amount,
            convertedAmount = quote.convertedAmount,
            rate = rate,
            date = quote.date
        )
    }

    private fun isValidCode(code: String): Boolean = code.matches(Regex("^[A-Z]{3}$"))
}
