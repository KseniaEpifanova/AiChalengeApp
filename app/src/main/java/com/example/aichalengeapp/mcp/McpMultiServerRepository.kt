package com.example.aichalengeapp.mcp

import com.example.aichalengeapp.mcp.currency.CurrencyToolResponse
import com.example.aichalengeapp.mcp.pipeline.PipelineToolResponse
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpMultiServerRepository @Inject constructor(
    private val manager: McpClientManager
) {
    suspend fun connect(target: McpServerTarget): Result<Unit> = manager.connect(target)

    suspend fun listTools(target: McpServerTarget): Result<List<McpToolUiModel>> = manager.listTools(target)

    suspend fun disconnect(target: McpServerTarget) = manager.disconnect(target)

    suspend fun disconnectAll() = manager.disconnectAll()

    suspend fun callExchangeRateTool(
        base: String,
        target: String,
        amount: Double?
    ): CurrencyToolResponse {
        val args = buildExchangeToolArguments(base = base, target = target, amount = amount)
        McpTrace.d(
            "event" to "currency_tool_call_start",
            "server" to McpServerTarget.CURRENCY.serverId,
            "base" to args["base"],
            "target" to args["target"],
            "amount" to args["amount"]
        )

        val connected = connect(McpServerTarget.CURRENCY)
        if (connected.isFailure) {
            McpTrace.d("event" to "currency_tool_call_failure", "server" to McpServerTarget.CURRENCY.serverId, "reason" to "connect_failed")
            return CurrencyToolResponse.Failure("Не удалось получить курс сейчас. Попробуй позже.")
        }

        val result = manager.callTool(
            target = McpServerTarget.CURRENCY,
            toolName = "get_exchange_rate",
            arguments = JSONObject(args)
        ).getOrElse {
            McpTrace.d(
                "event" to "currency_tool_call_failure",
                "server" to McpServerTarget.CURRENCY.serverId,
                "reason" to "tool_call_transport_error",
                "error" to (it.message ?: it::class.java.simpleName)
            )
            return CurrencyToolResponse.Failure("Не удалось получить курс сейчас. Попробуй позже.")
        }

        val textContent = manager.extractTextContent(result).orEmpty()
        if (result.optBoolean("isError", false)) {
            if (isInvalidCurrencyMessage(textContent)) {
                val normalizedBase = args["base"] as? String ?: ""
                val normalizedTarget = args["target"] as? String ?: ""
                McpTrace.d("event" to "currency_tool_call_failure", "server" to McpServerTarget.CURRENCY.serverId, "reason" to "invalid_currency", "message" to textContent)
                return CurrencyToolResponse.InvalidCurrency(normalizedBase, normalizedTarget)
            }
            McpTrace.d("event" to "currency_tool_call_failure", "server" to McpServerTarget.CURRENCY.serverId, "reason" to "tool_error", "message" to textContent)
            return CurrencyToolResponse.Failure("Не удалось получить курс сейчас. Попробуй позже.")
        }

        val payload = result.optJSONObject("structuredContent")
            ?: runCatching { JSONObject(textContent) }.getOrNull()
        if (payload == null) {
            McpTrace.d("event" to "currency_tool_call_failure", "server" to McpServerTarget.CURRENCY.serverId, "reason" to "payload_unparseable", "rawResult" to result.toString())
            return CurrencyToolResponse.Failure("Не удалось получить курс сейчас. Попробуй позже.")
        }

        if (!payload.optBoolean("ok", true)) {
            val errorCode = payload.optString("error", "")
            val message = payload.optString("message", "")
            if (errorCode == "invalid_currency" || isInvalidCurrencyMessage(message)) {
                val invalidBase = payload.optString("base", args["base"] as? String ?: "")
                val invalidTarget = payload.optString("target", args["target"] as? String ?: "")
                McpTrace.d("event" to "currency_tool_call_failure", "server" to McpServerTarget.CURRENCY.serverId, "reason" to "invalid_currency", "errorCode" to errorCode, "message" to message)
                return CurrencyToolResponse.InvalidCurrency(invalidBase, invalidTarget)
            }
            McpTrace.d("event" to "currency_tool_call_failure", "server" to McpServerTarget.CURRENCY.serverId, "reason" to "payload_error", "errorCode" to errorCode, "message" to message)
            return CurrencyToolResponse.Failure("Не удалось получить курс сейчас. Попробуй позже.")
        }

        val success = CurrencyToolResponse.Success(
            base = payload.optString("base", args["base"] as? String ?: ""),
            target = payload.optString("target", args["target"] as? String ?: ""),
            amountRequested = payload.optDouble("amountRequested").takeUnless { it.isNaN() },
            convertedAmount = payload.optDouble("convertedAmount").takeUnless { it.isNaN() },
            rate = payload.optDouble("rate").takeUnless { it.isNaN() },
            date = payload.optString("date", "").ifBlank { null }
        )
        McpTrace.d(
            "event" to "currency_tool_call_success",
            "server" to McpServerTarget.CURRENCY.serverId,
            "base" to success.base,
            "target" to success.target,
            "amountRequested" to success.amountRequested,
            "convertedAmount" to success.convertedAmount,
            "rate" to success.rate
        )
        return success
    }

    suspend fun callExchangeRateSummaryTool(
        base: String,
        target: String
    ): CurrencyToolResponse {
        val args = buildMap {
            put("base", normalizeCurrency(base))
            put("target", normalizeCurrency(target))
        }
        McpTrace.d("event" to "currency_tool_call_start", "server" to McpServerTarget.CURRENCY.serverId, "tool" to "get_exchange_rate_summary", "base" to args["base"], "target" to args["target"])

        val connected = connect(McpServerTarget.CURRENCY)
        if (connected.isFailure) {
            McpTrace.d("event" to "currency_tool_call_failure", "server" to McpServerTarget.CURRENCY.serverId, "reason" to "connect_failed", "tool" to "get_exchange_rate_summary")
            return CurrencyToolResponse.Failure("Не удалось получить сводку сейчас. Попробуй позже.")
        }

        val result = manager.callTool(
            target = McpServerTarget.CURRENCY,
            toolName = "get_exchange_rate_summary",
            arguments = JSONObject(args)
        ).getOrElse {
            McpTrace.d("event" to "currency_tool_call_failure", "server" to McpServerTarget.CURRENCY.serverId, "reason" to "tool_call_transport_error", "error" to (it.message ?: it::class.java.simpleName), "tool" to "get_exchange_rate_summary")
            return CurrencyToolResponse.Failure("Не удалось получить сводку сейчас. Попробуй позже.")
        }

        val textContent = manager.extractTextContent(result).orEmpty()
        if (result.optBoolean("isError", false)) {
            McpTrace.d("event" to "currency_tool_call_failure", "server" to McpServerTarget.CURRENCY.serverId, "reason" to "tool_error", "message" to textContent, "tool" to "get_exchange_rate_summary")
            return CurrencyToolResponse.Failure("Не удалось получить сводку сейчас. Попробуй позже.")
        }

        val payload = result.optJSONObject("structuredContent")
            ?: runCatching { JSONObject(textContent) }.getOrNull()
        if (payload == null) {
            McpTrace.d("event" to "currency_tool_call_failure", "server" to McpServerTarget.CURRENCY.serverId, "reason" to "payload_unparseable", "rawResult" to result.toString(), "tool" to "get_exchange_rate_summary")
            return CurrencyToolResponse.Failure("Не удалось получить сводку сейчас. Попробуй позже.")
        }

        if (!payload.optBoolean("ok", true)) {
            val errorCode = payload.optString("error", "")
            val message = payload.optString("message", "")
            McpTrace.d("event" to "currency_tool_call_failure", "server" to McpServerTarget.CURRENCY.serverId, "reason" to "payload_error", "errorCode" to errorCode, "message" to message, "tool" to "get_exchange_rate_summary")
            return CurrencyToolResponse.Failure("Не удалось получить сводку сейчас. Попробуй позже.")
        }

        val summary = CurrencyToolResponse.Summary(
            base = payload.optString("base", args["base"] as? String ?: ""),
            target = payload.optString("target", args["target"] as? String ?: ""),
            sampleCount = payload.optInt("sampleCount", 0),
            latestRate = payload.optDouble("latestRate").takeUnless { it.isNaN() },
            minRate = payload.optDouble("minRate").takeUnless { it.isNaN() },
            maxRate = payload.optDouble("maxRate").takeUnless { it.isNaN() },
            averageRate = payload.optDouble("averageRate").takeUnless { it.isNaN() },
            latestTimestamp = payload.optString("latestTimestamp").ifBlank { null }
        )
        McpTrace.d(
            "event" to "currency_tool_call_success",
            "server" to McpServerTarget.CURRENCY.serverId,
            "tool" to "get_exchange_rate_summary",
            "base" to summary.base,
            "target" to summary.target,
            "sampleCount" to summary.sampleCount
        )
        return summary
    }

    suspend fun callSearchSummaryPipelineTool(
        query: String,
        filename: String?,
        limit: Int?
    ): PipelineToolResponse {
        val args = buildMap<String, Any> {
            put("query", query.trim())
            if (!filename.isNullOrBlank()) put("filename", filename.trim())
            if (limit != null) put("limit", limit)
        }

        McpTrace.d("event" to "pipeline_tool_call_start", "server" to McpServerTarget.PIPELINE.serverId, "tool" to "run_search_summary_pipeline", "query" to args["query"])
        val connected = connect(McpServerTarget.PIPELINE)
        if (connected.isFailure) {
            McpTrace.d("event" to "pipeline_tool_call_failure", "server" to McpServerTarget.PIPELINE.serverId, "reason" to "connect_failed")
            return PipelineToolResponse.Failure("Не удалось запустить pipeline сейчас. Попробуй позже.")
        }

        val result = manager.callTool(
            target = McpServerTarget.PIPELINE,
            toolName = "run_search_summary_pipeline",
            arguments = JSONObject(args)
        ).getOrElse {
            McpTrace.d("event" to "pipeline_tool_call_failure", "server" to McpServerTarget.PIPELINE.serverId, "reason" to "tool_call_transport_error", "error" to (it.message ?: it::class.java.simpleName))
            return PipelineToolResponse.Failure("Не удалось запустить pipeline сейчас. Попробуй позже.")
        }

        val textContent = manager.extractTextContent(result).orEmpty()
        if (result.optBoolean("isError", false)) {
            McpTrace.d("event" to "pipeline_tool_call_failure", "server" to McpServerTarget.PIPELINE.serverId, "reason" to "tool_error", "message" to textContent)
            return PipelineToolResponse.Failure("Не удалось запустить pipeline сейчас. Попробуй позже.")
        }

        val payload = result.optJSONObject("structuredContent")
            ?: runCatching { JSONObject(textContent) }.getOrNull()
        if (payload == null) {
            McpTrace.d("event" to "pipeline_tool_call_failure", "server" to McpServerTarget.PIPELINE.serverId, "reason" to "payload_unparseable")
            return PipelineToolResponse.Failure("Не удалось запустить pipeline сейчас. Попробуй позже.")
        }

        if (!payload.optBoolean("ok", true)) {
            val message = payload.optString("message", "Pipeline error")
            McpTrace.d("event" to "pipeline_tool_call_failure", "server" to McpServerTarget.PIPELINE.serverId, "reason" to "payload_error", "message" to message)
            return PipelineToolResponse.Failure(message)
        }

        val normalizedQuery = payload.optString("query", query.trim())
        val matchedCount = payload.optInt("matchedCount", 0)
        val summary = payload.optString("summary", "")
        val savedPath = payload.optString("savedPath", "").ifBlank { null }
        val saved = payload.optBoolean("saved", false)
        val noMatchesMessage = payload.optString("message", "").ifBlank { null }

        if (matchedCount == 0) {
            McpTrace.d("event" to "pipeline_tool_call_success", "server" to McpServerTarget.PIPELINE.serverId, "matchedCount" to 0)
            return PipelineToolResponse.NoMatches(
                query = normalizedQuery,
                message = noMatchesMessage ?: "Ничего не найдено по запросу $normalizedQuery."
            )
        }

        val success = PipelineToolResponse.Success(
            query = normalizedQuery,
            matchedCount = matchedCount,
            summary = summary,
            savedPath = savedPath,
            saved = saved
        )
        McpTrace.d(
            "event" to "pipeline_tool_call_success",
            "server" to McpServerTarget.PIPELINE.serverId,
            "matchedCount" to success.matchedCount,
            "saved" to success.saved,
            "savedPath" to success.savedPath
        )
        return success
    }

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
