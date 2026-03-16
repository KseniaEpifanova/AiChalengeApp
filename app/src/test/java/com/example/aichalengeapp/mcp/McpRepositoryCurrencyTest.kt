package com.example.aichalengeapp.mcp

import org.junit.Assert.assertEquals
import org.junit.Test

class McpRepositoryCurrencyTest {

    @Test
    fun `exchange tool payload contains normalized args`() {
        val registry = McpServerRegistry(
            configs = mapOf(
                McpServerTarget.CURRENCY to McpServerConfig.remote("https://example.com/mcp"),
                McpServerTarget.PIPELINE to McpServerConfig.remote("https://example.com/pipeline")
            )
        )
        val repository = McpRepository(
            manager = McpClientManager(registry),
            multiServerRepository = McpMultiServerRepository(
                manager = McpClientManager(registry)
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
