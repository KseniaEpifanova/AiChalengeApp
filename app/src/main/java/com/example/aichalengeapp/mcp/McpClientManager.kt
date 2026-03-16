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
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpClientManager @Inject constructor(
    private val serverRegistry: McpServerRegistry
) {
    private val mutex = Mutex()
    private val jsonMediaType = "application/json".toMediaType()
    private val acceptHeader = "application/json, text/event-stream"
    private val idCounter = AtomicLong(1L)
    private val httpClient = OkHttpClient()

    private val connections = mutableMapOf<McpServerTarget, ConnectionState>()

    suspend fun connect(): Result<Unit> = connect(serverRegistry.defaultTarget())

    suspend fun connect(target: McpServerTarget): Result<Unit> = runCatching {
        mutex.withLock {
            val current = connections[target]
            if (current?.connected == true) return@withLock

            val remoteEndpoint = serverRegistry.getConfig(target).endpoint()
            McpTrace.d("event" to "connect_start", "server" to target.serverId, "transport" to "streamable_http", "url" to remoteEndpoint)

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

            connections[target] = ConnectionState(
                endpoint = remoteEndpoint,
                sessionId = newSessionId,
                connected = true
            )
            McpTrace.d("event" to "connect_success", "server" to target.serverId, "url" to remoteEndpoint, "hasSession" to (newSessionId != null))
        }
    }.onFailure {
        McpTrace.d("event" to "connect_failure", "server" to target.serverId, "error" to (it.message ?: it::class.java.simpleName), "baseUrl" to serverRegistry.getConfig(target).baseUrl)
        disconnect(target)
    }

    suspend fun listTools(): Result<List<McpToolUiModel>> = listTools(serverRegistry.defaultTarget())

    suspend fun listTools(target: McpServerTarget): Result<List<McpToolUiModel>> = runCatching {
        mutex.withLock {
            val connection = connections[target] ?: error("MCP is not connected. Call connect() first.")
            val remoteEndpoint = connection.endpoint
            McpTrace.d("event" to "tools_list_start", "server" to target.serverId, "url" to remoteEndpoint)

            val payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", idCounter.getAndIncrement())
                .put("method", "tools/list")
                .put("params", JSONObject())

            val response = postJson(remoteEndpoint, payload, connection.sessionId)
            McpTrace.d("event" to "tools_list_response", "server" to target.serverId, "contentType" to response.contentType)
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
            McpTrace.d("event" to "tools_list_success", "server" to target.serverId, "count" to mapped.size)
            mapped
        }
    }.onFailure {
        McpTrace.d("event" to "tools_list_failure", "server" to target.serverId, "error" to (it.message ?: it::class.java.simpleName))
    }

    suspend fun callTool(
        toolName: String,
        arguments: JSONObject
    ): Result<JSONObject> = callTool(serverRegistry.defaultTarget(), toolName, arguments)

    suspend fun callTool(
        target: McpServerTarget,
        toolName: String,
        arguments: JSONObject
    ): Result<JSONObject> = runCatching {
        mutex.withLock {
            val connection = connections[target] ?: error("MCP is not connected. Call connect() first.")
            val remoteEndpoint = connection.endpoint
            val normalizedTool = toolName.trim()
            if (normalizedTool.isEmpty()) error("Tool name is blank")
            McpTrace.d("event" to "tool_call_start", "server" to target.serverId, "tool" to normalizedTool, "args" to arguments.toString())

            val payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", idCounter.getAndIncrement())
                .put("method", "tools/call")
                .put(
                    "params",
                    JSONObject()
                        .put("name", normalizedTool)
                        .put("arguments", arguments)
                )

            val response = postJson(remoteEndpoint, payload, connection.sessionId)
            val json = parseProtocolJson(response.body)
            if (json.has("error")) {
                error("MCP tools/call error: ${json.getJSONObject("error").optString("message", "unknown")}")
            }

            val result = json.optJSONObject("result") ?: JSONObject()
            McpTrace.d("event" to "tool_call_success", "server" to target.serverId, "tool" to normalizedTool, "hasResult" to (result.length() > 0))
            result
        }
    }.onFailure {
        McpTrace.d("event" to "tool_call_failure", "server" to target.serverId, "tool" to toolName, "error" to (it.message ?: it::class.java.simpleName))
    }

    suspend fun disconnect() = disconnect(serverRegistry.defaultTarget())

    suspend fun disconnect(target: McpServerTarget) {
        mutex.withLock {
            connections.remove(target)
            McpTrace.d("event" to "disconnect", "server" to target.serverId)
        }
    }

    suspend fun disconnectAll() {
        mutex.withLock {
            connections.clear()
            McpTrace.d("event" to "disconnect_all")
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

    fun extractTextContent(result: JSONObject): String? {
        val content = result.optJSONArray("content") ?: return null
        for (i in 0 until content.length()) {
            val item = content.optJSONObject(i) ?: continue
            val type = item.optString("type", "").lowercase(Locale.US)
            if (type == "text") {
                val text = item.optString("text", "").trim()
                if (text.isNotEmpty()) return text
            }
        }
        return null
    }

    private data class HttpResult(
        val body: String,
        val sessionId: String?,
        val contentType: String?
    )

    private data class ConnectionState(
        val endpoint: String,
        val sessionId: String?,
        val connected: Boolean
    )
}
