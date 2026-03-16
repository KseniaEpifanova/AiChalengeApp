package com.example.aichalengeapp.mcp.orchestration

import com.example.aichalengeapp.mcp.currency.CurrencyToolIntent
import com.example.aichalengeapp.mcp.pipeline.PipelineToolIntent

sealed class OrchestrationRoute {
    data object None : OrchestrationRoute()

    data class PipelineOnly(
        val pipelineIntent: PipelineToolIntent
    ) : OrchestrationRoute()

    data class CurrencyOnly(
        val currencyIntent: CurrencyToolIntent
    ) : OrchestrationRoute()

    data class Combined(
        val pipelineIntent: PipelineToolIntent,
        val currencyIntent: CurrencyToolIntent
    ) : OrchestrationRoute()
}
