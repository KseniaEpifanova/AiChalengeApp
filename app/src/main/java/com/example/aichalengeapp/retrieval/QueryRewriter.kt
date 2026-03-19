package com.example.aichalengeapp.retrieval

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueryRewriter @Inject constructor() {
    fun rewrite(query: String, enabled: Boolean): String {
        if (!enabled) return query

        val normalized = query.trim()
        val lower = normalized.lowercase()
        val enrichments = buildList {
            if (lower.contains("orchestrator")) add("AgentOrchestrator")
            if (lower.contains("mcp") && (lower.contains("connect") || lower.contains("подключ"))) add("McpClientManager connect MCP")
            if (lower.contains("cosine") || lower.contains("similarity") || lower.contains("сходств")) add("CosineSimilarity similarity")
            if (lower.contains("queryembeddingproviderimpl") || (lower.contains("query") && lower.contains("embedding"))) add("QueryEmbeddingProviderImpl")
            if (lower.contains("documentretriever") || (lower.contains("document") && lower.contains("retriever")) || lower.contains("document retriever")) add("DocumentRetriever")
            if (lower.contains("chatviewmodel") || (lower.contains("chat") && lower.contains("viewmodel"))) add("ChatViewModel send")
            if (lower.contains("agentorchestrator")) add("AgentOrchestrator")
        }

        val rewritten = if (enrichments.isEmpty()) {
            normalized
        } else {
            "$normalized ${enrichments.joinToString(" ")}".trim()
        }
        return rewritten
    }
}
