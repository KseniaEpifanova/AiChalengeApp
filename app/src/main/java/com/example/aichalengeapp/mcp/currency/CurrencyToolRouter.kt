package com.example.aichalengeapp.mcp.currency

import com.example.aichalengeapp.mcp.McpTrace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyToolRouter @Inject constructor(
    private val parser: CurrencyRequestParser
) {
    private val currencySignals = listOf(
        "курс",
        "валют",
        "convert",
        "rate",
        "сколько будет",
        "обмен",
        "exchange"
    )

    fun route(message: String): CurrencyToolIntent? {
        val normalized = message.trim()
        if (normalized.isEmpty()) {
            McpTrace.d("event" to "currency_router_no_match", "reason" to "empty_message")
            return null
        }

        val hasSignal = currencySignals.any { normalized.contains(it, ignoreCase = true) } ||
            Regex("""(?iu).*\b[A-Za-z]{3}\b.*\b[A-Za-z]{3}\b.*""").matches(normalized)

        if (!hasSignal) {
            McpTrace.d("event" to "currency_router_no_match", "reason" to "no_currency_signal", "message" to normalized)
            return null
        }

        val intent = parser.parseIntent(normalized)
        if (intent == null) {
            McpTrace.d("event" to "currency_router_no_match", "reason" to "intent_not_parsed", "message" to normalized)
            return null
        }

        if (intent.isSummary) {
            McpTrace.d(
                "event" to "currency_summary_router_match",
                "base" to intent.base,
                "target" to intent.target,
                "message" to normalized
            )
        } else {
            McpTrace.d(
                "event" to "currency_router_match",
                "base" to intent.base,
                "target" to intent.target,
                "amount" to intent.amount,
                "message" to normalized
            )
        }
        return intent
    }
}
