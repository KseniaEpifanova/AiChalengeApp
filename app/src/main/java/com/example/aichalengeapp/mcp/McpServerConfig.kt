package com.example.aichalengeapp.mcp

data class McpServerConfig(
    val baseUrl: String
) {
    fun endpoint(): String {
        val normalized = baseUrl.trim().removeSuffix("/")
        require(normalized.isNotBlank()) {
            "MCP server base URL is blank. Set an MCP URL in gradle.properties."
        }
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "MCP server base URL must start with http:// or https://"
        }
        return normalized
    }

    companion object {
        fun remote(baseUrl: String): McpServerConfig = McpServerConfig(baseUrl = baseUrl)
    }
}
