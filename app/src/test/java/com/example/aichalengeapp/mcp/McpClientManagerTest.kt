package com.example.aichalengeapp.mcp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpClientManagerTest {

    private val manager = McpClientManager(
        McpServerRegistry(
            configs = mapOf(
                McpServerTarget.DEVELOPER to McpServerConfig.remote("https://example.com/mcp")
            )
        )
    )

    @Test
    fun `initialize fallback detects method not found response`() {
        val raw = """{"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method initialize not found"}}"""

        assertTrue(manager.isInitializeMethodNotSupported(raw))
    }

    @Test
    fun `initialize fallback ignores unrelated error responses`() {
        val raw = """{"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"unknown tool"}}"""

        assertFalse(manager.isInitializeMethodNotSupported(raw))
    }
}
