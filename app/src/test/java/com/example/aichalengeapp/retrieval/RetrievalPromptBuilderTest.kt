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

    @Test
    fun `builds compact retrieval prompt for local mode`() {
        val prompt = builder.build(
            userQuestion = "How does ChatAgent process messages?",
            chunks = listOf(
                RetrievedChunk(
                    source = "android-app",
                    titleOrFile = "ChatAgent.kt",
                    section = "handleUserMessage",
                    chunkId = "chat-agent-1",
                    strategy = "semantic",
                    text = "ChatAgent handles user messages, retrieval gating, prompt assembly, and final request execution for the llm provider.",
                    similarity = 0.88
                )
            ),
            options = RetrievalPromptBuilder.PromptOptions(
                compactMode = true,
                maxQuotes = 2,
                quoteMaxLength = 80,
                excerptMaxLength = 120
            )
        )

        assertTrue(prompt.contains("RETRIEVED PROJECT KNOWLEDGE"))
        assertTrue(prompt.contains("Question:"))
        assertTrue(prompt.contains("Quotes:"))
        assertTrue(prompt.contains("Use at most 2 quotes"))
        assertTrue(prompt.contains("EXCERPT:"))
        assertTrue(!prompt.contains("\nTEXT:\n"))
    }
}
