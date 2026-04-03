package com.example.aichalengeapp.mcp.git

import com.example.aichalengeapp.mcp.McpClientManager
import com.example.aichalengeapp.mcp.McpMultiServerRepository
import com.example.aichalengeapp.mcp.McpServerConfig
import com.example.aichalengeapp.mcp.McpServerRegistry
import com.example.aichalengeapp.mcp.McpServerTarget
import com.example.aichalengeapp.mcp.McpToolUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpGitServiceTest {

    private val registry = McpServerRegistry(
        configs = mapOf(
            McpServerTarget.DEVELOPER to McpServerConfig.remote("https://example.com/developer")
        )
    )
    private val manager = McpClientManager(registry)
    private val service = McpGitServiceImpl(
        mcpRepository = McpMultiServerRepository(manager),
        manager = manager,
        serverRegistry = registry
    )

    @Test
    fun `extract branch name prefers structured branch field`() {
        assertEquals(
            "feature/day-31-help",
            service.extractBranchName(
                branch = "feature/day-31-help",
                currentBranch = null,
                name = null,
                message = null
            )
        )
    }

    @Test
    fun `extract branch name falls back to message parsing`() {
        assertEquals(
            "feature/day-31-help",
            service.extractBranchName(
                branch = null,
                currentBranch = null,
                name = null,
                message = "Current branch is feature/day-31-help"
            )
        )
    }

    @Test
    fun `extract branch name returns null when payload is empty`() {
        assertNull(service.extractBranchName(branch = null, currentBranch = null, name = null, message = null))
    }

    @Test
    fun `resolve branch tool name matches supported aliases dynamically`() {
        val resolved = service.resolveBranchToolName(
            listOf(
                McpToolUiModel(name = "list_files", description = ""),
                McpToolUiModel(name = "current_branch", description = "")
            )
        )

        assertEquals("current_branch", resolved)
    }

    @Test
    fun `resolve branch tool name returns null when server exposes no branch tool`() {
        val resolved = service.resolveBranchToolName(
            listOf(
                McpToolUiModel(name = "list_files", description = ""),
                McpToolUiModel(name = "search_docs", description = "")
            )
        )

        assertNull(resolved)
    }

    @Test
    fun `resolve project files tool name matches developer file alias`() {
        val resolved = service.resolveProjectFilesToolName(
            listOf(
                McpToolUiModel(name = "search_docs", description = ""),
                McpToolUiModel(name = "list_project_files", description = "")
            )
        )

        assertEquals("list_project_files", resolved)
    }

    @Test
    fun `resolve read file tool name matches supported aliases dynamically`() {
        val resolved = service.resolveReadFileToolName(
            listOf(
                McpToolUiModel(name = "search_docs", description = ""),
                McpToolUiModel(name = "read_file", description = "")
            )
        )

        assertEquals("read_file", resolved)
    }

    @Test
    fun `resolve write file tool name matches supported aliases dynamically`() {
        val resolved = service.resolveWriteFileToolName(
            listOf(
                McpToolUiModel(name = "search_docs", description = ""),
                McpToolUiModel(name = "save-file", description = "")
            )
        )

        assertEquals("save-file", resolved)
    }

    @Test
    fun `extract project files from text parses json object safely`() {
        val raw = """{"files":["README.md","app/src/main/java/com/example/aichalengeapp/mcp/git/McpGitService.kt"]}"""

        val files = service.extractProjectFilesFromText(raw)

        assertEquals(
            listOf(
                "README.md",
                "app/src/main/java/com/example/aichalengeapp/mcp/git/McpGitService.kt"
            ),
            files
        )
        assertFalse(files.orEmpty().contains(raw))
    }

    @Test
    fun `extract project files from text rejects whole json payload as a path`() {
        val raw = """{"files":["README.md"]}"""

        val files = service.extractProjectFilesFromText(raw)

        assertTrue(files.orEmpty().all { it != raw })
    }
}
