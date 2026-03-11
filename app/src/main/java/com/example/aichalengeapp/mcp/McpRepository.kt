package com.example.aichalengeapp.mcp

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpRepository @Inject constructor(
    private val manager: McpClientManager
) {
    suspend fun connect(): Result<Unit> = manager.connect()

    suspend fun listTools(): Result<List<McpToolUiModel>> = manager.listTools()

    suspend fun disconnect() = manager.disconnect()
}
