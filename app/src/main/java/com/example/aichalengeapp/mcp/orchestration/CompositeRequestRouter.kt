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
        .firstNotNullOfOrNull(currencyToolRouter::route)

    private fun candidateCurrencySegments(message: String): List<String> {
        val separators = listOf("а потом", "потом", "then", "and then")
        val splitSegment = separators.firstNotNullOfOrNull { separator ->
            message.split(separator, limit = 2, ignoreCase = true).getOrNull(1)?.trim()
        }
        return listOfNotNull(splitSegment, message.trim())
    }
}
