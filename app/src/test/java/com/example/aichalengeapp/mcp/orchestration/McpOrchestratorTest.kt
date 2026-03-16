package com.example.aichalengeapp.mcp.orchestration

import com.example.aichalengeapp.mcp.currency.CurrencyToolIntent
import com.example.aichalengeapp.mcp.currency.CurrencyToolResponse
import com.example.aichalengeapp.mcp.currency.McpCurrencyService
import com.example.aichalengeapp.mcp.pipeline.McpPipelineService
import com.example.aichalengeapp.mcp.pipeline.PipelineToolIntent
import com.example.aichalengeapp.mcp.pipeline.PipelineToolResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpOrchestratorTest {

    @Test
    fun `calls pipeline first then currency and combines result`() = runBlocking {
        val calls = mutableListOf<String>()
        val orchestrator = McpOrchestrator(
            pipelineService = object : McpPipelineService {
                override suspend fun runSearchSummaryPipeline(query: String, filename: String?, limit: Int?): PipelineToolResponse {
                    calls += "pipeline"
                    return PipelineToolResponse.Success(
                        query = query,
                        matchedCount = 2,
                        summary = "Short summary",
                        savedPath = "pipeline_output/latest_summary.txt",
                        saved = true
                    )
                }
            },
            currencyService = object : McpCurrencyService {
                override suspend fun getExchangeRate(base: String, target: String, amount: Double?): CurrencyToolResponse {
                    calls += "currency"
                    return CurrencyToolResponse.Success(
                        base = base,
                        target = target,
                        amountRequested = amount,
                        convertedAmount = 108.34,
                        rate = 1.0834,
                        date = "2026-03-15"
                    )
                }

                override suspend fun getExchangeRateSummary(base: String, target: String): CurrencyToolResponse {
                    error("Not expected")
                }
            }
        )

        val result = orchestrator.execute(
            OrchestrationRoute.Combined(
                pipelineIntent = PipelineToolIntent(query = "qui", filename = "latest_summary.txt", limit = null),
                currencyIntent = CurrencyToolIntent(base = "EUR", target = "USD", amount = 100.0, isSummary = false)
            )
        )

        assertEquals(listOf("pipeline", "currency"), calls)
        assertTrue(result is McpOrchestratorResult.Handled)
        val handled = result as McpOrchestratorResult.Handled
        assertTrue(handled.text.contains("pipeline_output/latest_summary.txt"))
        assertTrue(handled.text.contains("100 EUR ≈ 108.34 USD"))
    }

    @Test
    fun `pipeline failure stops before currency`() = runBlocking {
        val calls = mutableListOf<String>()
        val orchestrator = McpOrchestrator(
            pipelineService = object : McpPipelineService {
                override suspend fun runSearchSummaryPipeline(query: String, filename: String?, limit: Int?): PipelineToolResponse {
                    calls += "pipeline"
                    return PipelineToolResponse.Failure("Pipeline failed")
                }
            },
            currencyService = object : McpCurrencyService {
                override suspend fun getExchangeRate(base: String, target: String, amount: Double?): CurrencyToolResponse {
                    calls += "currency"
                    return CurrencyToolResponse.Success(base, target, amount, 108.34, 1.0834, "2026-03-15")
                }

                override suspend fun getExchangeRateSummary(base: String, target: String): CurrencyToolResponse {
                    calls += "currency-summary"
                    return CurrencyToolResponse.Failure("unexpected")
                }
            }
        )

        val result = orchestrator.execute(
            OrchestrationRoute.Combined(
                pipelineIntent = PipelineToolIntent(query = "qui", filename = null, limit = null),
                currencyIntent = CurrencyToolIntent(base = "EUR", target = "USD", amount = 100.0, isSummary = false)
            )
        )

        assertEquals(listOf("pipeline"), calls)
        assertTrue(result is McpOrchestratorResult.Handled)
        assertEquals("Pipeline failed", (result as McpOrchestratorResult.Handled).text)
    }

    @Test
    fun `currency failure is returned after successful pipeline`() = runBlocking {
        val calls = mutableListOf<String>()
        val orchestrator = McpOrchestrator(
            pipelineService = object : McpPipelineService {
                override suspend fun runSearchSummaryPipeline(query: String, filename: String?, limit: Int?): PipelineToolResponse {
                    calls += "pipeline"
                    return PipelineToolResponse.Success(query, 2, "Summary", "pipeline_output/latest_summary.txt", true)
                }
            },
            currencyService = object : McpCurrencyService {
                override suspend fun getExchangeRate(base: String, target: String, amount: Double?): CurrencyToolResponse {
                    calls += "currency"
                    return CurrencyToolResponse.Failure("Currency failed")
                }

                override suspend fun getExchangeRateSummary(base: String, target: String): CurrencyToolResponse {
                    error("Not expected")
                }
            }
        )

        val result = orchestrator.execute(
            OrchestrationRoute.Combined(
                pipelineIntent = PipelineToolIntent(query = "qui", filename = null, limit = null),
                currencyIntent = CurrencyToolIntent(base = "EUR", target = "USD", amount = 100.0, isSummary = false)
            )
        )

        assertEquals(listOf("pipeline", "currency"), calls)
        assertTrue(result is McpOrchestratorResult.Handled)
        assertEquals("Currency failed", (result as McpOrchestratorResult.Handled).text)
    }
}
