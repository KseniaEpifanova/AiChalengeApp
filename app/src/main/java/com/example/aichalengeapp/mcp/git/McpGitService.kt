package com.example.aichalengeapp.mcp.git

import com.example.aichalengeapp.mcp.McpClientManager
import com.example.aichalengeapp.mcp.McpMultiServerRepository
import com.example.aichalengeapp.mcp.McpServerRegistry
import com.example.aichalengeapp.mcp.McpServerTarget
import com.example.aichalengeapp.mcp.McpTrace
import com.example.aichalengeapp.mcp.McpToolUiModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface McpGitService {
    suspend fun getCurrentGitBranch(): String?
    suspend fun listProjectFiles(): List<String>?
}

@Singleton
class McpGitServiceImpl @Inject constructor(
    private val mcpRepository: McpMultiServerRepository,
    private val manager: McpClientManager,
    private val serverRegistry: McpServerRegistry
) : McpGitService {

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
        return extractProjectFiles(structured)
            ?: manager.extractTextContent(result)
                ?.lineSequence()
                ?.map { it.removePrefix("-").trim() }
                ?.filter { it.isNotBlank() }
                ?.toList()
                ?.takeIf { it.isNotEmpty() }
    }

    internal suspend fun callDeveloperTool(
        operation: String,
        candidateNames: List<String>,
        missingEvent: String,
        resolvedEvent: String
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
            arguments = JSONObject()
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

    internal fun resolveBranchToolName(tools: List<McpToolUiModel>): String? {
        return resolveToolName(tools, BRANCH_TOOL_CANDIDATES)
    }

    internal fun resolveProjectFilesToolName(tools: List<McpToolUiModel>): String? {
        return resolveToolName(tools, PROJECT_FILES_TOOL_CANDIDATES)
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
        if (payload == null) return null
        val files = payload.optJSONArray("files") ?: payload.optJSONArray("paths")
        return files?.toStringList()?.takeIf { it.isNotEmpty() }
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
        val BRANCH_NAME_REGEX = Regex("""[A-Za-z0-9._/\-]+""")
    }

    private fun normalizeToolName(name: String): String {
        return name.trim()
            .lowercase(Locale.US)
            .replace('-', '_')
            .replace(' ', '_')
    }

    private fun JSONArray.toStringList(): List<String> {
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }
}
