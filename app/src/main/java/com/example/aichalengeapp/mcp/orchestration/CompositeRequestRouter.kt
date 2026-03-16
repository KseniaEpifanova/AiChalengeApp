package com.example.aichalengeapp.mcp.orchestration

import com.example.aichalengeapp.mcp.McpTrace
import com.example.aichalengeapp.mcp.currency.CurrencyToolRouter
import com.example.aichalengeapp.mcp.pipeline.PipelineToolRouter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompositeRequestRouter @Inject constructor(
    private val pipelineToolRouter: PipelineToolRouter,
    private val currencyToolRouter: CurrencyToolRouter
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

    fun route(message: String): OrchestrationRoute {
        val normalized = message.trim()
        if (normalized.isEmpty()) return OrchestrationRoute.None

        val pipelineIntent = pipelineToolRouter.route(normalized)
        val currencyIntent = extractCurrencyIntent(normalized)

        val route = when {
            pipelineIntent != null && currencyIntent != null -> OrchestrationRoute.Combined(
                pipelineIntent = pipelineIntent,
                currencyIntent = currencyIntent
            )
            pipelineIntent != null -> OrchestrationRoute.PipelineOnly(pipelineIntent)
            currencyIntent != null -> OrchestrationRoute.CurrencyOnly(currencyIntent)
            else -> OrchestrationRoute.None
        }

        McpTrace.d(
            "event" to "composite_router_result",
            "route" to route::class.java.simpleName,
            "hasPipeline" to (pipelineIntent != null),
            "hasCurrency" to (currencyIntent != null)
        )
        return route
    }

    private fun extractCurrencyIntent(message: String) = candidateCurrencySegments(message)
        .firstNotNullOfOrNull { segment ->
            if (!looksLikeCurrencySegment(segment)) return@firstNotNullOfOrNull null
            currencyToolRouter.route(segment)
        }

    private fun candidateCurrencySegments(message: String): List<String> {
        val separators = listOf("а потом", "потом", "then", "and then")
        val splitSegment = separators.firstNotNullOfOrNull { separator ->
            message.split(separator, limit = 2, ignoreCase = true).getOrNull(1)?.trim()
        }
        return listOfNotNull(splitSegment, message.trim())
    }

    private fun looksLikeCurrencySegment(segment: String): Boolean {
        val lower = segment.lowercase()
        val hasSignal = currencySignals.any { lower.contains(it) }
        val hasAmountAndCodes = Regex("""(?iu)\d+(?:[.,]\d+)?\s+[A-Za-z]{3}\b.*\b(?:to|в|к)\s+[A-Za-z]{3}\b""").containsMatchIn(segment)
        return hasSignal || hasAmountAndCodes
    }
}
