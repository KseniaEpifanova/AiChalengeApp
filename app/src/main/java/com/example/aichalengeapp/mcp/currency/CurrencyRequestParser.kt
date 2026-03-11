package com.example.aichalengeapp.mcp.currency

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyRequestParser @Inject constructor() {

    private val codeRegex = Regex("""(?iu)\b([A-Za-z]{3})\b""")
    private val amountRegex = Regex("""(?iu)(\d+(?:[.,]\d+)?)""")

    private val namedCurrencies = linkedMapOf(
        "евро" to "EUR",
        "доллар" to "USD",
        "доллара" to "USD",
        "доллару" to "USD",
        "usd" to "USD",
        "eur" to "EUR",
        "gbp" to "GBP",
        "фунт" to "GBP",
        "фунта" to "GBP",
        "иен" to "JPY",
        "йен" to "JPY",
        "jpy" to "JPY",
        "рубл" to "RUB",
        "rub" to "RUB"
    )

    fun parseIntent(raw: String): CurrencyToolIntent? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val upper = text.uppercase()
        val codeHits = codeRegex.findAll(upper)
            .map { it.groupValues[1].uppercase() }
            .distinct()
            .toList()

        val amount = amountRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNullFlexible()

        if (codeHits.size >= 2) {
            return CurrencyToolIntent(
                base = codeHits[0],
                target = codeHits[1],
                amount = amount
            )
        }

        val lowered = text.lowercase()
        val namedHits = namedCurrencies.entries
            .filter { lowered.contains(it.key) }
            .map { it.value }
            .distinct()
            .toList()

        if (namedHits.size >= 2) {
            return CurrencyToolIntent(
                base = namedHits[0],
                target = namedHits[1],
                amount = amount
            )
        }

        return null
    }

    private fun String.toDoubleOrNullFlexible(): Double? {
        return replace(',', '.').toDoubleOrNull()
    }
}
