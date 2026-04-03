package com.example.aichalengeapp.agent.file

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.LlmResult
import com.example.aichalengeapp.mcp.git.McpGitService
import com.example.aichalengeapp.repo.ChatRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileOperationServiceTest {

    @Test
    fun `search returns at least one file`() = runBlocking {
        val mcpService = FakeMcpGitService()
        val service = FileOperationService(
            mcpGitService = mcpService,
            llmRepository = FakeChatRepository("# unused")
        )

        val result = service.execute(FileOperationIntent.Search("Find where MCP is used in the project"))

        assertTrue(result.text.contains("McpClientManager.kt"))
        assertEquals(
            setOf(
                "app/src/main/java/com/example/aichalengeapp/mcp/McpClientManager.kt",
                "app/src/main/java/com/example/aichalengeapp/agent/ChatAgent.kt",
                "docs/help-command.md"
            ),
            mcpService.readRequests.toSet()
        )
        assertFalse(mcpService.readRequests.any { it.startsWith("{") })
    }

    @Test
    fun `generation produces content and creates file`() = runBlocking {
        val outputFile = File.createTempFile("support-readme", ".md")
        outputFile.delete()
        val mcpService = FakeMcpGitService(writeTarget = outputFile)
        val service = FileOperationService(
            mcpGitService = mcpService,
            llmRepository = FakeChatRepository(
                """
                # Support Module

                This module handles support answers.
                """.trimIndent()
            )
        )

        val result = service.execute(
            FileOperationIntent.Generate(
                request = "Generate README for support module",
                outputPath = outputFile.absolutePath
            )
        )

        assertTrue(result.generatedContent.orEmpty().isNotBlank())
        assertTrue(outputFile.exists())
        assertTrue(outputFile.readText().contains("Support Module"))
    }

    private class FakeMcpGitService(
        private val writeTarget: File? = null
    ) : McpGitService {
        val readRequests = mutableListOf<String>()

        override suspend fun getCurrentGitBranch(): String? = "feature/day-34"

        override suspend fun listProjectFiles(): List<String> {
            return listOf(
                "app/src/main/java/com/example/aichalengeapp/mcp/McpClientManager.kt",
                "app/src/main/java/com/example/aichalengeapp/agent/ChatAgent.kt",
                "app/src/main/java/com/example/aichalengeapp/vm/SupportViewModel.kt",
                "app/src/main/java/com/example/aichalengeapp/support/SupportAssistant.kt",
                "docs/help-command.md"
            )
        }

        override suspend fun readProjectFile(path: String): String? {
            readRequests += path
            return when (path) {
                "app/src/main/java/com/example/aichalengeapp/mcp/McpClientManager.kt" -> "McpClientManager handles initialize, tools/list, and tools/call."
                "app/src/main/java/com/example/aichalengeapp/agent/ChatAgent.kt" -> "ChatAgent intercepts /help and MCP-backed requests."
                "app/src/main/java/com/example/aichalengeapp/vm/SupportViewModel.kt" -> "SupportViewModel submits questions and exposes loading, success, and error state."
                "app/src/main/java/com/example/aichalengeapp/support/SupportAssistant.kt" -> "SupportAssistant builds support answers from FAQ docs and user context."
                "docs/help-command.md" -> "The /help command is separate from normal chat."
                else -> null
            }
        }

        override suspend fun writeProjectFile(path: String, content: String): Boolean {
            val target = writeTarget ?: File(path)
            target.parentFile?.mkdirs()
            target.writeText(content)
            return true
        }
    }

    private class FakeChatRepository(
        private val response: String
    ) : ChatRepository {
        override suspend fun ask(messages: List<AgentMessage>, maxOutputTokens: Int?): LlmResult {
            return LlmResult(response)
        }
    }
}
