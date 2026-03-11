package com.example.aichalengeapp.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpClientManager @Inject constructor(
    private val serverConfig: McpServerConfig
) {
    private val mutex = Mutex()
    private val jsonMediaType = "application/json".toMediaType()
    private val acceptHeader = "application/json, text/event-stream"
    private val idCounter = AtomicLong(1L)
    private val httpClient = OkHttpClient()

    private var endpoint: String? = null
    private var sessionId: String? = null
    private var connected: Boolean = false

    suspend fun connect(): Result<Unit> = runCatching {
        mutex.withLock {
            if (connected) return@withLock

            val remoteEndpoint = serverConfig.endpoint()
            McpTrace.d("event" to "connect_start", "transport" to "streamable_http", "url" to remoteEndpoint)

            val initializeId = idCounter.getAndIncrement()
            val initializePayload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", initializeId)
                .put("method", "initialize")
                .put(
                    "params",
                    JSONObject()
                        .put("protocolVersion", "2024-11-05")
                        .put("capabilities", JSONObject())
                        .put(
                            "clientInfo",
                            JSONObject()
                                .put("name", "AiChallengeApp")
                                .put("version", "1.0.0")
                        )
                )

            val initResponse = postJson(remoteEndpoint, initializePayload, session = null)
            McpTrace.d("event" to "connect_initialize_response", "contentType" to initResponse.contentType)
            val initJson = parseProtocolJson(initResponse.body)
            if (initJson.has("error")) {
                error("MCP initialize error: ${initJson.getJSONObject("error").optString("message", "unknown")}")
            }
            if (!initJson.has("result")) {
                error("MCP initialize missing result")
            }

            val sessionFromResult = initJson
                .optJSONObject("result")
                ?.optString("sessionId")
                ?.takeIf { it.isNotBlank() }
            val newSessionId = initResponse.sessionId ?: sessionFromResult
            val initializedPayload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "notifications/initialized")

            postJson(remoteEndpoint, initializedPayload, newSessionId)

            endpoint = remoteEndpoint
            sessionId = newSessionId
            connected = true
            McpTrace.d("event" to "connect_success", "url" to remoteEndpoint, "hasSession" to (newSessionId != null))
        }
    }.onFailure {
        McpTrace.d("event" to "connect_failure", "error" to (it.message ?: it::class.java.simpleName), "baseUrl" to serverConfig.baseUrl)
        disconnect()
    }

    suspend fun listTools(): Result<List<McpToolUiModel>> = runCatching {
        mutex.withLock {
            val remoteEndpoint = endpoint ?: error("MCP is not connected. Call connect() first.")
            McpTrace.d("event" to "tools_list_start", "url" to remoteEndpoint)

            val payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", idCounter.getAndIncrement())
                .put("method", "tools/list")
                .put("params", JSONObject())

            val response = postJson(remoteEndpoint, payload, sessionId)
            McpTrace.d("event" to "tools_list_response", "contentType" to response.contentType)
            val json = parseProtocolJson(response.body)
            if (json.has("error")) {
                error("MCP tools/list error: ${json.getJSONObject("error").optString("message", "unknown")}")
            }

            val toolsArray = json.optJSONObject("result")?.optJSONArray("tools") ?: JSONArray()
            val mapped = buildList {
                for (i in 0 until toolsArray.length()) {
                    val tool = toolsArray.optJSONObject(i) ?: continue
                    val name = tool.optString("name", "").trim()
                    if (name.isBlank()) continue
                    add(
                        McpToolUiModel(
                            name = name,
                            description = tool.optString("description", "")
                        )
                    )
                }
            }
            McpTrace.d("event" to "tools_list_success", "count" to mapped.size)
            mapped
        }
    }.onFailure {
        McpTrace.d("event" to "tools_list_failure", "error" to (it.message ?: it::class.java.simpleName))
    }

    suspend fun disconnect() {
        mutex.withLock {
            endpoint = null
            sessionId = null
            connected = false
            McpTrace.d("event" to "disconnect")
        }
    }

    private suspend fun postJson(
        url: String,
        payload: JSONObject,
        session: String?
    ): HttpResult = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", acceptHeader)
            .header("Content-Type", "application/json")
            .header("User-Agent", "AiChallengeApp-McpClient/1.0")
            .post(payload.toString().toRequestBody(jsonMediaType))

        if (!session.isNullOrBlank()) {
            requestBuilder.header("Mcp-Session-Id", session)
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: $body")
            }
            HttpResult(
                body = body,
                sessionId = response.header("Mcp-Session-Id"),
                contentType = response.header("Content-Type")
            )
        }
    }

    private fun parseProtocolJson(raw: String): JSONObject {
        val trimmed = raw.trim()
        runCatching { JSONObject(trimmed) }.getOrNull()?.let { return it }

        val ssePayloads = extractSseDataPayloads(trimmed)
        for (payload in ssePayloads) {
            runCatching { JSONObject(payload) }.getOrNull()?.let { parsed ->
                if (parsed.has("jsonrpc") || parsed.has("result") || parsed.has("error")) {
                    return parsed
                }
            }
        }
        for (payload in ssePayloads) {
            runCatching { JSONObject(payload) }.getOrNull()?.let { return it }
        }

        error("Invalid JSON response: ${trimmed.take(200)}")
    }

    private fun extractSseDataPayloads(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val payloads = mutableListOf<String>()
        val current = StringBuilder()

        raw.lineSequence().forEach { line ->
            when {
                line.startsWith("data:") -> {
                    val chunk = line.removePrefix("data:").trimStart()
                    if (chunk.isNotBlank()) {
                        if (current.isNotEmpty()) current.append('\n')
                        current.append(chunk)
                    }
                }
                line.isBlank() -> {
                    if (current.isNotEmpty()) {
                        payloads += current.toString()
                        current.clear()
                    }
                }
            }
        }
        if (current.isNotEmpty()) {
            payloads += current.toString()
        }
        return payloads
    }

    private data class HttpResult(
        val body: String,
        val sessionId: String?,
        val contentType: String?
    )
}
