package com.example.aichalengeapp.mcp.orchestration

import com.example.aichalengeapp.mcp.McpTrace
import com.example.aichalengeapp.mcp.currency.CurrencyToolResponse
import com.example.aichalengeapp.mcp.currency.McpCurrencyService
import com.example.aichalengeapp.mcp.pipeline.McpPipelineService
import com.example.aichalengeapp.mcp.pipeline.PipelineToolResponse
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpOrchestrator @Inject constructor(
    private val pipelineService: McpPipelineService,
    private val currencyService: McpCurrencyService
) {
    suspend fun execute(route: OrchestrationRoute): McpOrchestratorResult {
        McpTrace.d("event" to "orchestrator_start", "route" to route::class.java.simpleName)
        return try {
            when (route) {
                OrchestrationRoute.None -> McpOrchestratorResult.NotHandled
                is OrchestrationRoute.PipelineOnly -> {
                    McpTrace.d("event" to "orchestrator_route_pipeline", "query" to route.pipelineIntent.query)
                    val pipelineResponse = pipelineService.runSearchSummaryPipeline(
                        query = route.pipelineIntent.query,
                        filename = route.pipelineIntent.filename,
                        limit = route.pipelineIntent.limit
                    )
                    if (pipelineResponse is PipelineToolResponse.Failure) {
                        McpTrace.d("event" to "orchestrator_failure", "stage" to "pipeline", "message" to pipelineResponse.message)
                        McpOrchestratorResult.Handled(formatPipelineReply(pipelineResponse), "mcp-pipeline")
                    } else {
                        McpTrace.d("event" to "orchestrator_pipeline_success", "query" to route.pipelineIntent.query)
                        McpTrace.d("event" to "orchestrator_success", "route" to "pipeline_only")
                        McpOrchestratorResult.Handled(formatPipelineReply(pipelineResponse), "mcp-pipeline")
                    }
                }
                is OrchestrationRoute.CurrencyOnly -> {
                    McpTrace.d("event" to "orchestrator_route_currency", "base" to route.currencyIntent.base, "target" to route.currencyIntent.target)
                    val currencyResponse = if (route.currencyIntent.isSummary) {
                        currencyService.getExchangeRateSummary(
                            base = route.currencyIntent.base,
                            target = route.currencyIntent.target
                        )
                    } else {
                        currencyService.getExchangeRate(
                            base = route.currencyIntent.base,
                            target = route.currencyIntent.target,
                            amount = route.currencyIntent.amount
                        )
                    }
                    if (currencyResponse is CurrencyToolResponse.Failure) {
                        McpTrace.d("event" to "orchestrator_failure", "stage" to "currency", "message" to currencyResponse.message)
                        McpOrchestratorResult.Handled(formatCurrencyReply(currencyResponse), "mcp-currency")
                    } else {
                        McpTrace.d("event" to "orchestrator_currency_success", "base" to route.currencyIntent.base, "target" to route.currencyIntent.target)
                        McpTrace.d("event" to "orchestrator_success", "route" to "currency_only")
                        McpOrchestratorResult.Handled(formatCurrencyReply(currencyResponse), "mcp-currency")
                    }
                }
                is OrchestrationRoute.Combined -> {
                    McpTrace.d("event" to "orchestrator_route_pipeline", "query" to route.pipelineIntent.query)
                    val pipelineResponse = pipelineService.runSearchSummaryPipeline(
                        query = route.pipelineIntent.query,
                        filename = route.pipelineIntent.filename,
                        limit = route.pipelineIntent.limit
                    )
                    if (pipelineResponse is PipelineToolResponse.Failure) {
                        McpTrace.d("event" to "orchestrator_failure", "stage" to "pipeline", "message" to pipelineResponse.message)
                        return McpOrchestratorResult.Handled(pipelineResponse.message, "mcp-orchestrator")
                    }
                    McpTrace.d("event" to "orchestrator_pipeline_success", "query" to route.pipelineIntent.query)

                    McpTrace.d("event" to "orchestrator_route_currency", "base" to route.currencyIntent.base, "target" to route.currencyIntent.target)
                    val currencyResponse = if (route.currencyIntent.isSummary) {
                        currencyService.getExchangeRateSummary(
                            base = route.currencyIntent.base,
                            target = route.currencyIntent.target
                        )
                    } else {
                        currencyService.getExchangeRate(
                            base = route.currencyIntent.base,
                            target = route.currencyIntent.target,
                            amount = route.currencyIntent.amount
                        )
                    }
                    if (currencyResponse is CurrencyToolResponse.Failure) {
                        McpTrace.d("event" to "orchestrator_failure", "stage" to "currency", "message" to currencyResponse.message)
                        return McpOrchestratorResult.Handled(currencyResponse.message, "mcp-orchestrator")
                    }
                    McpTrace.d("event" to "orchestrator_currency_success", "base" to route.currencyIntent.base, "target" to route.currencyIntent.target)
                    McpTrace.d("event" to "orchestrator_success", "route" to "combined")
                    McpOrchestratorResult.Handled(
                        text = combineReplies(pipelineResponse, currencyResponse),
                        debugLabel = "mcp-orchestrator"
                    )
                }
            }
        } catch (t: Throwable) {
            McpTrace.d("event" to "orchestrator_failure", "stage" to "exception", "error" to (t.message ?: t::class.java.simpleName))
            McpOrchestratorResult.Handled("Не удалось выполнить MCP orchestration сейчас. Попробуй позже.", "mcp-orchestrator")
        }
    }

    private fun combineReplies(
        pipelineResponse: PipelineToolResponse,
        currencyResponse: CurrencyToolResponse
    ): String {
        val pipelineText = when (pipelineResponse) {
            is PipelineToolResponse.Success -> {
                val pathText = pipelineResponse.savedPath ?: "указанный файл"
                "Готово. Я нашёл посты по запросу '${pipelineResponse.query}', сделал краткую сводку и сохранил её в файл $pathText."
            }
            is PipelineToolResponse.NoMatches -> pipelineResponse.message
            is PipelineToolResponse.Failure -> pipelineResponse.message
        }
        val currencyText = formatCurrencyReply(currencyResponse)
        return "$pipelineText\n\nТакже $currencyText"
    }

    private fun formatCurrencyReply(response: CurrencyToolResponse): String {
        return when (response) {
            is CurrencyToolResponse.InvalidCurrency -> {
                "не удалось получить курс для пары ${response.base} -> ${response.target}. Проверь код валюты."
            }
            is CurrencyToolResponse.Failure -> response.message.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            is CurrencyToolResponse.Success -> {
                val amountRequested = response.amountRequested ?: 1.0
                val converted = response.convertedAmount
                val rate = response.rate
                if (converted != null) {
                    "${trimTrailingZeros(amountRequested)} ${response.base} ≈ ${trimTrailingZeros(converted)} ${response.target} по текущему курсу."
                } else if (rate != null) {
                    "курс ${response.base} -> ${response.target}: ${trimTrailingZeros(rate)}."
                } else {
                    "получен курс ${response.base} -> ${response.target}."
                }
            }
            is CurrencyToolResponse.Summary -> {
                if (response.sampleCount == 0) {
                    return "пока нет собранных данных для ${response.base}/${response.target}."
                }
                val latest = response.latestRate?.let { trimTrailingZeros(it) } ?: "N/A"
                "сводка по ${response.base}/${response.target}: последний курс $latest."
            }
        }
    }

    private fun formatPipelineReply(response: PipelineToolResponse): String {
        return when (response) {
            is PipelineToolResponse.Failure -> response.message
            is PipelineToolResponse.NoMatches -> response.message
            is PipelineToolResponse.Success -> {
                if (!response.saved || response.savedPath.isNullOrBlank()) {
                    "Готово. Я нашёл ${response.matchedCount} поста(ов) и сделал краткую сводку: ${response.summary}"
                } else {
                    "Готово. Я нашёл ${response.matchedCount} поста(ов), сделал краткую сводку и сохранил результат в файл ${response.savedPath}. Сводка: ${response.summary}"
                }
            }
        }
    }

    private fun trimTrailingZeros(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        }
    }
}

sealed class McpOrchestratorResult {
    data object NotHandled : McpOrchestratorResult()

    data class Handled(
        val text: String,
        val debugLabel: String
    ) : McpOrchestratorResult()
}
