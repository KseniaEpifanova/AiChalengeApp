package com.example.aichalengeapp.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrievalRerankerTest {
    private val reranker = RetrievalReranker()

    @Test
    fun `disabled reranker preserves original order`() {
        val chunks = listOf(
            chunk(id = "a", title = "Noise.kt", similarity = 0.61, text = "generic"),
            chunk(id = "b", title = "AgentOrchestrator.kt", similarity = 0.60, text = "orchestrator implementation")
        )

        val reranked = reranker.rerank("Где реализован orchestrator? AgentOrchestrator", chunks, enabled = false)

        assertEquals(listOf("a", "b"), reranked.map { it.chunkId })
    }

    @Test
    fun `reranker boosts direct class match above slightly higher similarity`() {
        val chunks = listOf(
            chunk(id = "noise", title = "SomethingElse.kt", similarity = 0.63, text = "generic helper"),
            chunk(id = "orchestrator", title = "AgentOrchestrator.kt", similarity = 0.58, text = "AgentOrchestrator handles execution context and prompt assembly")
        )

        val reranked = reranker.rerank("Где реализован orchestrator? AgentOrchestrator", chunks, enabled = true)

        assertEquals("orchestrator", reranked.first().chunkId)
        assertTrue(reranked.first().finalScore > reranked[1].finalScore)
        assertTrue(reranked.first().boostReasons.any { it.contains("file_exact:agentorchestrator") || it.contains("file_contains:agentorchestrator") })
    }

    @Test
    fun `reranker penalizes chunks with no structural code match`() {
        val chunks = listOf(
            chunk(
                id = "noise",
                title = "ChatViewModel.kt",
                similarity = 0.62,
                text = "ViewModel state management and send flow"
            ),
            chunk(
                id = "target",
                title = "AgentOrchestrator.kt",
                similarity = 0.56,
                text = "class AgentOrchestrator builds execution context and prompts"
            )
        )

        val reranked = reranker.rerank("What does AgentOrchestrator do?", chunks, enabled = true)

        assertEquals("target", reranked.first().chunkId)
        assertTrue(reranked.first().boostReasons.any { it.contains("file_exact:agentorchestrator") || it.contains("type_decl:agentorchestrator") })
        assertTrue(reranked.last().boostReasons.contains("penalty:no_structural_match"))
    }

    private fun chunk(id: String, title: String, similarity: Double, text: String): RetrievedChunk {
        return RetrievedChunk(
            source = "android-app",
            titleOrFile = title,
            section = "main",
            chunkId = id,
            strategy = "semantic",
            text = text,
            similarity = similarity
        )
    }
}
