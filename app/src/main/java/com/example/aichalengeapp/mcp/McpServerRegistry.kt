package com.example.aichalengeapp.mcp

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpServerRegistry @Inject constructor(
    private val configs: Map<McpServerTarget, McpServerConfig>
) {
    fun defaultTarget(): McpServerTarget = McpServerTarget.CURRENCY

    fun getConfig(target: McpServerTarget): McpServerConfig {
        return configs[target] ?: error("No MCP config registered for $target")
    }

    fun allTargets(): List<McpServerTarget> = McpServerTarget.entries.filter { configs.containsKey(it) }

    fun describe(): List<McpRegisteredServer> {
        return allTargets().map { target ->
            McpRegisteredServer(
                target = target,
                serverId = target.serverId,
                endpoint = getConfig(target).endpoint()
            )
        }
    }
}

data class McpRegisteredServer(
    val target: McpServerTarget,
    val serverId: String,
    val endpoint: String
)
