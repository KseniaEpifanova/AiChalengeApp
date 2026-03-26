package com.example.aichalengeapp.retrieval

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeEntityMatchScorerTest {

    @Test
    fun `chat agent query gets stronger score for chat agent chunk than unrelated mcp chunk`() {
        val target = CodeEntityMatchScorer.evaluate(
            query = "How does ChatAgent process messages?",
            titleOrFile = "ChatAgent.kt",
            section = "handleUserMessage",
            text = "class ChatAgent handles user messages and builds the final llm request"
        )
        val noise = CodeEntityMatchScorer.evaluate(
            query = "How does ChatAgent process messages?",
            titleOrFile = "McpClientManager.kt",
            section = "connect",
            text = "class McpClientManager handles initialize and mcp sessions"
        )

        assertTrue(target.hasGroundingEvidence)
        assertTrue(target.boost > noise.boost)
    }

    @Test
    fun `viewmodel query recognizes partial code entity grounding`() {
        val target = CodeEntityMatchScorer.evaluate(
            query = "Where is ViewModel state updated?",
            titleOrFile = "ChatViewModel.kt",
            section = "state",
            text = "class ChatViewModel updates ui state and handles send flow"
        )

        assertTrue(target.hasGroundingEvidence)
        assertTrue(target.reasons.any { it.contains("title:viewmodel") || it.contains("file_contains:viewmodel") })
    }

    @Test
    fun `unrelated chunk for code entity query is not considered grounded`() {
        val noise = CodeEntityMatchScorer.evaluate(
            query = "How does DocumentRetriever find relevant chunks?",
            titleOrFile = "McpClientManager.kt",
            section = "connect",
            text = "class McpClientManager initializes sessions and tool calls"
        )

        assertFalse(noise.hasGroundingEvidence)
        assertTrue(noise.reasons.contains("penalty:no_structural_match"))
    }
}
