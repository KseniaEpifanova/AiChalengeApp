package com.example.aichalengeapp.mcp

import org.junit.Assert.assertEquals
import org.junit.Test

class McpRepositoryCurrencyTest {

    @Test
    fun `exchange tool payload contains normalized args`() {
        val repository = McpRepository(
            manager = McpClientManager(
                serverConfig = McpServerConfig.remote("https://example.com/mcp")
            )
        )

        val args = repository.buildExchangeToolArguments(
            base = " eur ",
            target = "usd",
            amount = 100.0
        )

        assertEquals("EUR", args["base"])
        assertEquals("USD", args["target"])
        assertEquals(100.0, args["amount"])
    }
}
