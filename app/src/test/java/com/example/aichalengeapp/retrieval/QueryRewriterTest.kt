package com.example.aichalengeapp.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryRewriterTest {
    private val rewriter = QueryRewriter()

    @Test
    fun `baseline mode keeps query unchanged`() {
        val rewritten = rewriter.rewrite("Где реализован orchestrator?", enabled = false)

        assertEquals("Где реализован orchestrator?", rewritten)
    }

    @Test
    fun `improved mode enriches orchestrator query`() {
        val rewritten = rewriter.rewrite("Где реализован orchestrator?", enabled = true)

        assertTrue(rewritten.contains("AgentOrchestrator"))
    }

    @Test
    fun `improved mode enriches mcp connect query`() {
        val rewritten = rewriter.rewrite("Как работает MCP connect?", enabled = true)

        assertTrue(rewritten.contains("McpClientManager"))
        assertTrue(rewritten.contains("connect"))
    }
}
