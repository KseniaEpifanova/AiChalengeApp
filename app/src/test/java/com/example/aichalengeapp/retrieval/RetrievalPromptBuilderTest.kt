package com.example.aichalengeapp.retrieval

import org.junit.Assert.assertTrue
import org.junit.Test

class RetrievalPromptBuilderTest {
    private val builder = RetrievalPromptBuilder()

    @Test
    fun `builds retrieval prompt with chunk metadata`() {
        val prompt = builder.build(
            userQuestion = "How does MCP connection work?",
            chunks = listOf(
                RetrievedChunk(
                    source = "android-app",
                    titleOrFile = "McpClientManager.kt",
                    section = "connect",
                    chunkId = "mcp-1",
                    strategy = "semantic",
                    text = "The manager initializes MCP and stores session IDs.",
                    similarity = 0.92
                )
            )
        )

        assertTrue(prompt.contains("RETRIEVED PROJECT KNOWLEDGE"))
        assertTrue(prompt.contains("McpClientManager.kt"))
        assertTrue(prompt.contains("CHUNK_ID: mcp-1"))
        assertTrue(prompt.contains("Answer:"))
        assertTrue(prompt.contains("Sources:"))
        assertTrue(prompt.contains("Quotes:"))
        assertTrue(prompt.contains("QUOTE_SNIPPET:"))
        assertTrue(prompt.contains("The manager initializes MCP"))
    }

    @Test
    fun `builds strict no-knowledge prompt`() {
        val prompt = builder.buildNoKnowledgePrompt("What does MissingClass do?")

        assertTrue(prompt.contains("I don't know based on the retrieved context."))
        assertTrue(prompt.contains("Sources:"))
        assertTrue(prompt.contains("- no relevant sources"))
        assertTrue(prompt.contains("Quotes:"))
        assertTrue(prompt.contains("- no relevant quotes"))
        assertTrue(prompt.contains("Please specify the class, file, or method name."))
    }
}
