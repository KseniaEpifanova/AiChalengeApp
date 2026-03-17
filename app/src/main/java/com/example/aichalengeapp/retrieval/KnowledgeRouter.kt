package com.example.aichalengeapp.retrieval

import com.example.aichalengeapp.mcp.McpTrace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRouter @Inject constructor() {
    private val knowledgeQuestionSignals = listOf(
        "how",
        "where",
        "what",
        "explain",
        "describe",
        "покажи",
        "как",
        "где",
        "объясни",
        "расскажи"
    )

    private val projectKnowledgeSignals = listOf(
        "mcp",
        "connection",
        "connect",
        "profile",
        "task lifecycle",
        "task",
        "chat agent",
        "agent",
        "guard",
        "invariant",
        "repository",
        "retrieval",
        "index",
        "sqlite",
        "подключение",
        "профил",
        "задач",
        "жизненн",
        "агент",
        "логика",
        "репозитор",
        "индекс"
    )

    fun shouldRetrieve(message: String): Boolean {
        val normalized = message.trim()
        if (normalized.isEmpty()) {
            McpTrace.d("event" to "knowledge_router_no_match", "reason" to "empty_message")
            return false
        }

        val lowered = normalized.lowercase()
        val hasQuestionSignal = knowledgeQuestionSignals.any { lowered.contains(it) } || normalized.contains("?")
        val hasProjectSignal = projectKnowledgeSignals.any { lowered.contains(it) }

        val matches = hasQuestionSignal && hasProjectSignal
        if (matches) {
            McpTrace.d("event" to "knowledge_router_match", "message" to normalized)
        } else {
            McpTrace.d(
                "event" to "knowledge_router_no_match",
                "reason" to "missing_signals",
                "hasQuestionSignal" to hasQuestionSignal,
                "hasProjectSignal" to hasProjectSignal
            )
        }
        return matches
    }
}
