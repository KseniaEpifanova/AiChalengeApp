package com.example.aichalengeapp.mcp.currency

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyRequestParser @Inject constructor() {

    private val codeRegex = Regex("""(?iu)\b([A-Za-z]{3})\b""")
    private val amountRegex = Regex("""(?iu)(\d+(?:[.,]\d+)?)""")
    private val connectorRegex = Regex("""(?iu)\b(to|in|в|к)\b|/|->""")

    private val namedCurrencies = linkedMapOf(
        "евро" to "EUR",
        "доллар" to "USD",
        "usd" to "USD",
        "eur" to "EUR",
        "gbp" to "GBP",
        "фунт" to "GBP",
        "jpy" to "JPY",
        "иен" to "JPY",
        "йен" to "JPY",
        "рубл" to "RUB",
        "rub" to "RUB",
        "aud" to "AUD",
        "cad" to "CAD",
        "chf" to "CHF",
        "cny" to "CNY"
    )

    private val supportedCodes = namedCurrencies.values.toSet()

    private val currencyKeywordRoots = listOf(
        "курс", "валют", "обмен", "convert", "rate", "exchange", "сколько"
    )
    private val summaryKeywordRoots = listOf(
        "свод", "статист", "анализ", "history", "summary", "average", "средн", "min", "max"
    )

    fun parseIntent(raw: String): CurrencyToolIntent? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val codes = extractValidatedCodes(text)
        val names = extractNamedCurrencies(text)
        val currencies = when {
            codes.size >= 2 -> codes
            names.size >= 2 -> names
            else -> return null
        }

        val amount = amountRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNullFlexible()
        val isSummary = containsRoot(text, summaryKeywordRoots)
        val hasCurrencyKeyword = containsRoot(text, currencyKeywordRoots)
        val hasConnector = connectorRegex.containsMatchIn(text)
        val hasExplicitEvidence = isSummary || hasCurrencyKeyword || (amount != null && hasConnector)

        if (!hasExplicitEvidence) return null

        return CurrencyToolIntent(
            base = currencies[0],
            target = currencies[1],
            amount = if (isSummary) null else amount,
            isSummary = isSummary
        )
    }

    private fun extractValidatedCodes(text: String): List<String> {
        return codeRegex.findAll(text.uppercase())
            .map { it.groupValues[1].uppercase() }
            .filter { it in supportedCodes }
            .distinct()
            .toList()
    }

    private fun extractNamedCurrencies(text: String): List<String> {
        val lowered = text.lowercase()
        return namedCurrencies.entries
            .filter { lowered.contains(it.key) }
            .map { it.value }
            .distinct()
            .toList()
    }

    private fun containsRoot(text: String, roots: List<String>): Boolean {
        val lowered = text.lowercase()
        return roots.any { lowered.contains(it) }
    }

    private fun String.toDoubleOrNullFlexible(): Double? {
        return replace(',', '.').toDoubleOrNull()
    }
}
