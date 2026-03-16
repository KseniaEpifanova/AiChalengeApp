package com.example.aichalengeapp.mcp.pipeline

import com.example.aichalengeapp.mcp.McpTrace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PipelineToolRouter @Inject constructor() {

    private val ruQueryRegex = Regex("""(?iu)по\s+слову\s+([\p{L}\p{N}_-]+)""")
    private val enQueryRegex = Regex("""(?iu)\bfor\s+([A-Za-z0-9_-]+)\b""")
    private val quotedRegex = Regex("""["']([^"']+)["']""")
    private val filenameRegex = Regex("""(?iu)(?:в\s+файл|to\s+file)\s+([A-Za-z0-9._-]+)""")
    private val limitRegex = Regex("""(?iu)\b(\d{1,2})\s*(?:posts?|пост(?:а|ов)?)\b""")

    fun route(message: String): PipelineToolIntent? {
        val normalized = message.trim()
        if (normalized.isEmpty()) {
            McpTrace.d("event" to "pipeline_router_no_match", "reason" to "empty_message")
            return null
        }

        val lowered = normalized.lowercase()
        val hasSearch = lowered.contains("найди") || lowered.contains("search") || lowered.contains("find")
        val hasPosts = lowered.contains("пост") || lowered.contains("posts")
        val hasSummary = lowered.contains("свод") || lowered.contains("summary") || lowered.contains("summar") || lowered.contains("кратк")
        val hasSave = lowered.contains("сохран") || lowered.contains("save")
        val hasFile = lowered.contains("файл") || lowered.contains("file")

        if (!(hasSearch && hasPosts && hasSummary && hasSave && hasFile)) {
            McpTrace.d("event" to "pipeline_router_no_match", "reason" to "missing_signals", "message" to normalized)
            return null
        }

        val query = extractQuery(normalized)
        if (query.isNullOrBlank()) {
            McpTrace.d("event" to "pipeline_router_no_match", "reason" to "query_not_found", "message" to normalized)
            return null
        }

        val filename = filenameRegex.find(normalized)?.groupValues?.getOrNull(1)
        val limit = limitRegex.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()

        McpTrace.d(
            "event" to "pipeline_router_match",
            "query" to query,
            "filename" to filename,
            "limit" to limit
        )

        return PipelineToolIntent(
            query = query,
            filename = filename,
            limit = limit
        )
    }

    private fun extractQuery(text: String): String? {
        return ruQueryRegex.find(text)?.groupValues?.getOrNull(1)
            ?: enQueryRegex.find(text)?.groupValues?.getOrNull(1)
            ?: quotedRegex.find(text)?.groupValues?.getOrNull(1)
    }
}
