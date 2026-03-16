package com.example.aichalengeapp.mcp.currency

import com.example.aichalengeapp.mcp.McpMultiServerRepository
import javax.inject.Inject
import javax.inject.Singleton

interface McpCurrencyService {
    suspend fun getExchangeRate(
        base: String,
        target: String,
        amount: Double?
    ): CurrencyToolResponse

    suspend fun getExchangeRateSummary(
        base: String,
        target: String
    ): CurrencyToolResponse
}

@Singleton
class McpCurrencyServiceImpl @Inject constructor(
    private val mcpRepository: McpMultiServerRepository
) : McpCurrencyService {
    override suspend fun getExchangeRate(
        base: String,
        target: String,
        amount: Double?
    ): CurrencyToolResponse {
        return mcpRepository.callExchangeRateTool(base = base, target = target, amount = amount)
    }

    override suspend fun getExchangeRateSummary(
        base: String,
        target: String
    ): CurrencyToolResponse {
        return mcpRepository.callExchangeRateSummaryTool(base = base, target = target)
    }
}
