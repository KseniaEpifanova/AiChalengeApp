package com.example.aichalengeapp.mcp.currency

import com.example.aichalengeapp.mcp.McpRepository
import javax.inject.Inject
import javax.inject.Singleton

interface McpCurrencyService {
    suspend fun getExchangeRate(
        base: String,
        target: String,
        amount: Double?
    ): CurrencyToolResponse
}

@Singleton
class McpCurrencyServiceImpl @Inject constructor(
    private val mcpRepository: McpRepository
) : McpCurrencyService {
    override suspend fun getExchangeRate(
        base: String,
        target: String,
        amount: Double?
    ): CurrencyToolResponse {
        return mcpRepository.callExchangeRateTool(base = base, target = target, amount = amount)
    }
}
