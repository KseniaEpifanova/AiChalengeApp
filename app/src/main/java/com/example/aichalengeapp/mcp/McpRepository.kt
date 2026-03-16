package com.example.aichalengeapp.mcp

import com.example.aichalengeapp.mcp.currency.CurrencyToolResponse
import com.example.aichalengeapp.mcp.pipeline.PipelineToolResponse
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpRepository @Inject constructor(
    private val manager: McpClientManager,
    private val multiServerRepository: McpMultiServerRepository
) {
    suspend fun connect(): Result<Unit> = manager.connect()

    suspend fun listTools(): Result<List<McpToolUiModel>> = manager.listTools()

    suspend fun callTool(toolName: String, arguments: JSONObject): Result<JSONObject> {
        return manager.callTool(toolName, arguments)
    }

    suspend fun callExchangeRateTool(
        base: String,
        target: String,
        amount: Double?
    ): CurrencyToolResponse {
        return multiServerRepository.callExchangeRateTool(base = base, target = target, amount = amount)
    }

    suspend fun callExchangeRateSummaryTool(
        base: String,
        target: String
    ): CurrencyToolResponse {
        return multiServerRepository.callExchangeRateSummaryTool(base = base, target = target)
    }

    suspend fun callSearchSummaryPipelineTool(
        query: String,
        filename: String?,
        limit: Int?
    ): PipelineToolResponse {
        return multiServerRepository.callSearchSummaryPipelineTool(query = query, filename = filename, limit = limit)
    }

    suspend fun disconnect() = manager.disconnect()

    internal fun buildExchangeToolArguments(
        base: String,
        target: String,
        amount: Double?
    ): Map<String, Any> {
        val normalizedBase = normalizeCurrency(base)
        val normalizedTarget = normalizeCurrency(target)
        return buildMap {
            put("base", normalizedBase)
            put("target", normalizedTarget)
            if (amount != null) {
                put("amount", amount)
            }
        }
    }

    internal fun normalizeCurrency(code: String): String = code.trim().uppercase()

    internal fun isInvalidCurrencyMessage(message: String): Boolean {
        val normalized = message.lowercase()
        return normalized.contains("invalid_currency") ||
            normalized.contains("invalid currency") ||
            normalized.contains("unknown currency") ||
            normalized.contains("unsupported currency")
    }
}
