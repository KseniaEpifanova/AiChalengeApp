package com.example.aichalengeapp.mcp.git

import com.example.aichalengeapp.mcp.McpClientManager
import com.example.aichalengeapp.mcp.McpMultiServerRepository
import com.example.aichalengeapp.mcp.McpServerRegistry
import com.example.aichalengeapp.mcp.McpServerTarget
import com.example.aichalengeapp.mcp.McpTrace
import com.example.aichalengeapp.mcp.McpToolUiModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface McpGitService {
    suspend fun getCurrentGitBranch(): String?
    suspend fun listProjectFiles(): List<String>?
    suspend fun readProjectFile(path: String): String?
    suspend fun writeProjectFile(path: String, content: String): Boolean
}

@Singleton
class McpGitServiceImpl @Inject constructor(
    private val mcpRepository: McpMultiServerRepository,
    private val manager: McpClientManager,
    private val serverRegistry: McpServerRegistry
) : McpGitService {
    private val developerToolsMutex = Mutex()
    private var cachedDeveloperEndpoint: String? = null
    private var cachedDeveloperTools: List<McpToolUiModel>? = null

    override suspend fun getCurrentGitBranch(): String? {
        val result = callDeveloperTool(
            operation = "get_current_git_branch",
            candidateNames = BRANCH_TOOL_CANDIDATES,
            missingEvent = "mcp_branch_tool_missing",
            resolvedEvent = "mcp_branch_tool_resolved"
        ) ?: return null

        if (result.optBoolean("isError", false)) {
            McpTrace.d(
                "event" to "git_branch_tool_call_failure",
                "server" to McpServerTarget.DEVELOPER.serverId,
                "reason" to "tool_error",
                "message" to manager.extractTextContent(result).orEmpty()
            )
            return null
        }

        val structured = result.optJSONObject("structuredContent")
        val branch = extractBranchName(structured)
            ?: manager.extractTextContent(result)?.trim()?.takeIf { it.isNotBlank() }
        McpTrace.d(
            "event" to if (branch.isNullOrBlank()) "git_branch_tool_call_failure" else "git_branch_tool_call_success",
            "server" to McpServerTarget.DEVELOPER.serverId,
            "reason" to if (branch.isNullOrBlank()) "branch_missing" else "ok",
            "branch" to branch
        )
        return branch
    }

    override suspend fun listProjectFiles(): List<String>? {
        val result = callDeveloperTool(
            operation = "list_project_files",
            candidateNames = PROJECT_FILES_TOOL_CANDIDATES,
            missingEvent = "mcp_project_files_tool_missing",
            resolvedEvent = "mcp_project_files_tool_resolved"
        ) ?: return null

        if (result.optBoolean("isError", false)) {
            McpTrace.d(
                "event" to "developer_mcp_call_failure",
                "operation" to "list_project_files",
                "server" to McpServerTarget.DEVELOPER.serverId,
                "reason" to "tool_error",
                "message" to manager.extractTextContent(result).orEmpty()
            )
            return null
        }

        val structured = result.optJSONObject("structuredContent")
        val files = extractProjectFiles(structured)
            ?: extractProjectFilesFromText(manager.extractTextContent(result))
        McpTrace.d(
            "event" to "project_files_received",
            "server" to McpServerTarget.DEVELOPER.serverId,
            "count" to (files?.size ?: 0),
            "files" to files.orEmpty().joinToString(",")
        )
        return files
    }

    override suspend fun readProjectFile(path: String): String? {
        McpTrace.d(
            "event" to "read_project_file_request",
            "server" to McpServerTarget.DEVELOPER.serverId,
            "path" to path
        )
        val result = callDeveloperTool(
            operation = "read_project_file",
            candidateNames = READ_FILE_TOOL_CANDIDATES,
            missingEvent = "mcp_read_file_tool_missing",
            resolvedEvent = "mcp_read_file_tool_resolved",
            arguments = JSONObject().put("path", path)
        ) ?: return null

        if (result.optBoolean("isError", false)) {
            McpTrace.d(
                "event" to "mcp_call_failure",
                "operation" to "read_project_file",
                "server" to McpServerTarget.DEVELOPER.serverId,
                "reason" to "tool_error",
                "path" to path,
                "message" to manager.extractTextContent(result).orEmpty()
            )
            return null
        }

        return extractFileContent(result.optJSONObject("structuredContent"))
            ?: manager.extractTextContent(result)?.takeIf { it.isNotBlank() }
    }

    override suspend fun writeProjectFile(path: String, content: String): Boolean {
        val result = callDeveloperTool(
            operation = "write_project_file",
            candidateNames = WRITE_FILE_TOOL_CANDIDATES,
            missingEvent = "mcp_write_file_tool_missing",
            resolvedEvent = "mcp_write_file_tool_resolved",
            arguments = JSONObject()
                .put("path", path)
                .put("content", content)
        ) ?: return false

        if (result.optBoolean("isError", false)) {
            McpTrace.d(
                "event" to "mcp_call_failure",
                "operation" to "write_project_file",
                "server" to McpServerTarget.DEVELOPER.serverId,
                "reason" to "tool_error",
                "path" to path,
                "message" to manager.extractTextContent(result).orEmpty()
            )
            return false
        }

        val structured = result.optJSONObject("structuredContent")
        return structured?.optBoolean("ok", true)
            ?: manager.extractTextContent(result)?.isNotBlank()
            ?: true
    }

    internal suspend fun callDeveloperTool(
        operation: String,
        candidateNames: List<String>,
        missingEvent: String,
        resolvedEvent: String,
        arguments: JSONObject = JSONObject()
    ): JSONObject? {
        val endpoint = serverRegistry.getConfig(McpServerTarget.DEVELOPER).endpoint()
        McpTrace.d(
            "event" to "mcp_single_server_mode_enabled",
            "target" to McpServerTarget.DEVELOPER.serverId,
            "operation" to operation
        )
        McpTrace.d(
            "event" to "mcp_base_url_used",
            "target" to McpServerTarget.DEVELOPER.serverId,
            "baseUrl" to endpoint,
            "operation" to operation
        )
        McpTrace.d(
            "event" to "mcp_call_started",
            "operation" to operation,
            "target" to McpServerTarget.DEVELOPER.serverId
        )

        val connected = mcpRepository.connect(McpServerTarget.DEVELOPER)
        if (connected.isFailure) {
            McpTrace.d(
                "event" to "mcp_call_failure",
                "operation" to operation,
                "server" to McpServerTarget.DEVELOPER.serverId,
                "reason" to "connect_failed"
            )
            return null
        }

        McpTrace.d(
            "event" to "mcp_tools_list_requested",
            "server" to McpServerTarget.DEVELOPER.serverId,
            "operation" to operation
        )
        val tools = listDeveloperTools(
            operation = operation,
            missingEvent = missingEvent
        ) ?: return null
        McpTrace.d(
            "event" to "mcp_tools_list_received",
            "server" to McpServerTarget.DEVELOPER.serverId,
            "count" to tools.size,
            "operation" to operation,
            "tools" to tools.joinToString(",") { it.name }
        )

        val toolName = resolveToolName(tools, candidateNames)
        if (toolName == null) {
            McpTrace.d(
                "event" to missingEvent,
                "server" to McpServerTarget.DEVELOPER.serverId,
                "reason" to "no_matching_tool",
                "operation" to operation
            )
            return null
        }
        McpTrace.d(
            "event" to resolvedEvent,
            "server" to McpServerTarget.DEVELOPER.serverId,
            "operation" to operation,
            "tool" to toolName
        )

        val result = manager.callTool(
            target = McpServerTarget.DEVELOPER,
            toolName = toolName,
            arguments = arguments
        ).getOrElse { error ->
            McpTrace.d(
                "event" to "mcp_call_failure",
                "operation" to operation,
                "server" to McpServerTarget.DEVELOPER.serverId,
                "reason" to "tool_call_transport_error",
                "error" to (error.message ?: error::class.java.simpleName)
            )
            return null
        }
        McpTrace.d(
            "event" to "mcp_call_success",
            "operation" to operation,
            "server" to McpServerTarget.DEVELOPER.serverId,
            "tool" to toolName
        )
        return result
    }

    private suspend fun listDeveloperTools(
        operation: String,
        missingEvent: String
    ): List<McpToolUiModel>? {
        val endpoint = serverRegistry.getConfig(McpServerTarget.DEVELOPER).endpoint()
        developerToolsMutex.withLock {
            if (cachedDeveloperEndpoint != endpoint) {
                cachedDeveloperEndpoint = endpoint
                cachedDeveloperTools = null
            }

            cachedDeveloperTools?.let { cached ->
                McpTrace.d(
                    "event" to "mcp_tools_list_cache_hit",
                    "server" to McpServerTarget.DEVELOPER.serverId,
                    "count" to cached.size,
                    "operation" to operation
                )
                return cached
            }
        }

        val tools = mcpRepository.listTools(McpServerTarget.DEVELOPER).getOrElse { error ->
            McpTrace.d(
                "event" to missingEvent,
                "server" to McpServerTarget.DEVELOPER.serverId,
                "reason" to "tools_list_failed",
                "operation" to operation,
                "error" to (error.message ?: error::class.java.simpleName)
            )
            return null
        }

        developerToolsMutex.withLock {
            if (cachedDeveloperEndpoint == endpoint) {
                cachedDeveloperTools = tools
            }
        }
        return tools
    }

    internal fun resolveBranchToolName(tools: List<McpToolUiModel>): String? {
        return resolveToolName(tools, BRANCH_TOOL_CANDIDATES)
    }

    internal fun resolveProjectFilesToolName(tools: List<McpToolUiModel>): String? {
        return resolveToolName(tools, PROJECT_FILES_TOOL_CANDIDATES)
    }

    internal fun resolveReadFileToolName(tools: List<McpToolUiModel>): String? {
        return resolveToolName(tools, READ_FILE_TOOL_CANDIDATES)
    }

    internal fun resolveWriteFileToolName(tools: List<McpToolUiModel>): String? {
        return resolveToolName(tools, WRITE_FILE_TOOL_CANDIDATES)
    }

    internal fun resolveToolName(tools: List<McpToolUiModel>, candidateNames: List<String>): String? {
        val byNormalizedName = tools.associateBy { normalizeToolName(it.name) }
        for (candidate in candidateNames) {
            val match = byNormalizedName[normalizeToolName(candidate)]
            if (match != null) return match.name
        }
        return null
    }

    internal fun extractBranchName(payload: JSONObject?): String? {
        if (payload == null) return null
        return extractBranchName(
            branch = payload.optString("branch").trim(),
            currentBranch = payload.optString("currentBranch").trim(),
            name = payload.optString("name").trim(),
            message = payload.optString("message").trim()
        )
    }

    internal fun extractBranchName(
        branch: String?,
        currentBranch: String?,
        name: String?,
        message: String?
    ): String? {
        val directBranch = sequenceOf(branch, currentBranch, name)
            .map { it.orEmpty().trim() }
            .firstOrNull { it.isNotBlank() }
        if (!directBranch.isNullOrBlank()) return directBranch

        val normalizedMessage = message.orEmpty().trim()
        if (normalizedMessage.isBlank()) return null
        return BRANCH_NAME_REGEX.findAll(normalizedMessage)
            .map { it.value }
            .firstOrNull { candidate ->
                candidate.contains('/') || candidate.contains('-') || candidate.contains('_')
            }
    }

    internal fun extractProjectFiles(payload: JSONObject?): List<String>? {
        return extractProjectFilesFromJsonText(payload?.toString())
    }

    internal fun extractProjectFilesFromText(text: String?): List<String>? {
        val normalizedText = text?.trim().orEmpty()
        if (normalizedText.isBlank()) return null

        extractProjectFilesFromJsonText(normalizedText)
            ?.let { return it }

        parseJsonStringArray(normalizedText)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        return normalizedText
            .lineSequence()
            .map { it.removePrefix("-").trim() }
            .filter { it.isLikelyProjectPath() }
            .toList()
            .takeIf { it.isNotEmpty() }
    }

    private fun extractProjectFilesFromJsonText(text: String?): List<String>? {
        val normalizedText = text?.trim().orEmpty()
        if (!normalizedText.startsWith("{")) return null

        val arrayText = extractNamedJsonArray(normalizedText, "files")
            ?: extractNamedJsonArray(normalizedText, "paths")
            ?: return null

        return parseJsonStringArray(arrayText)
            ?.takeIf { it.isNotEmpty() }
    }

    internal fun extractFileContent(payload: JSONObject?): String? {
        if (payload == null) return null
        return sequenceOf("content", "text", "body")
            .map { key -> payload.optString(key).trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private companion object {
        val BRANCH_TOOL_CANDIDATES = listOf(
            "get_current_git_branch",
            "git_branch",
            "current_branch",
            "get_branch"
        )
        val PROJECT_FILES_TOOL_CANDIDATES = listOf(
            "list_project_files",
            "get_project_files",
            "project_files"
        )
        val READ_FILE_TOOL_CANDIDATES = listOf(
            "read_project_file",
            "read_file",
            "get_file_content"
        )
        val WRITE_FILE_TOOL_CANDIDATES = listOf(
            "write_project_file",
            "write_file",
            "save_file",
            "create_file"
        )
        val BRANCH_NAME_REGEX = Regex("""[A-Za-z0-9._/\-]+""")
    }

    private fun normalizeToolName(name: String): String {
        return name.trim()
            .lowercase(Locale.US)
            .replace('-', '_')
            .replace(' ', '_')
    }

    private fun String.isLikelyProjectPath(): Boolean {
        if (isBlank()) return false
        if (startsWith("{") || startsWith("[")) return false
        return contains("/") || contains(".")
    }

    private fun extractNamedJsonArray(json: String, key: String): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex < 0) return null

        val colonIndex = json.indexOf(':', startIndex = keyIndex + key.length + 2)
        if (colonIndex < 0) return null

        var arrayStart = -1
        for (index in colonIndex + 1 until json.length) {
            val char = json[index]
            if (!char.isWhitespace()) {
                if (char != '[') return null
                arrayStart = index
                break
            }
        }
        if (arrayStart < 0) return null

        var inString = false
        var escaping = false
        var depth = 0
        for (index in arrayStart until json.length) {
            val char = json[index]
            if (escaping) {
                escaping = false
                continue
            }
            when (char) {
                '\\' -> if (inString) escaping = true
                '"' -> inString = !inString
                '[' -> if (!inString) depth += 1
                ']' -> if (!inString) {
                    depth -= 1
                    if (depth == 0) {
                        return json.substring(arrayStart, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun parseJsonStringArray(text: String): List<String>? {
        val normalized = text.trim()
        if (!normalized.startsWith("[") || !normalized.endsWith("]")) return null

        val result = mutableListOf<String>()
        var index = 1
        while (index < normalized.length - 1) {
            while (index < normalized.length - 1 && normalized[index].isWhitespace()) index += 1
            if (index >= normalized.length - 1) break
            if (normalized[index] == ',') {
                index += 1
                continue
            }
            if (normalized[index] != '"') return null

            index += 1
            val item = StringBuilder()
            var escaping = false
            while (index < normalized.length - 1) {
                val char = normalized[index]
                if (escaping) {
                    item.append(
                        when (char) {
                            '\\', '"', '/' -> char
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'u' -> {
                                if (index + 4 >= normalized.length) return null
                                val hex = normalized.substring(index + 1, index + 5)
                                index += 4
                                hex.toIntOrNull(16)?.toChar() ?: return null
                            }
                            else -> return null
                        }
                    )
                    escaping = false
                } else if (char == '\\') {
                    escaping = true
                } else if (char == '"') {
                    break
                } else {
                    item.append(char)
                }
                index += 1
            }
            if (index >= normalized.length - 1 || normalized[index] != '"') return null
            result += item.toString()
            index += 1
        }
        return result
    }
}
