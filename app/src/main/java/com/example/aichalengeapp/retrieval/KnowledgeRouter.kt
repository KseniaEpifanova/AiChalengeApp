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
        "show",
        "implemented",
        "located",
        "works",
        "покажи",
        "как",
        "где",
        "объясни",
        "расскажи",
        "реализ",
        "наход",
        "работ"
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
        "orchestrator",
        "viewmodel",
        "retriever",
        "embedding",
        "provider",
        "similarity",
        "cosine",
        "document",
        "chunk",
        "agentorchestrator",
        "chatviewmodel",
        "documentretriever",
        "queryembeddingproviderimpl",
        "cosinesimilarity",
        "mcpclientmanager",
        "подключение",
        "профил",
        "задач",
        "жизненн",
        "агент",
        "логика",
        "репозитор",
        "индекс",
        "оркестратор",
        "ретрив",
        "эмбед",
        "сходств",
        "документ",
        "чанк",
        "модел"
    )

    fun shouldRetrieve(message: String): Boolean {
        val normalized = message.trim()
        if (normalized.isEmpty()) return false

        val lowered = normalized.lowercase()
        val matchedQuestionSignals = knowledgeQuestionSignals.filter { lowered.contains(it) }.distinct()
        val matchedProjectSignals = projectKnowledgeSignals.filter { lowered.contains(it) }.distinct()
        val detectedCodeTerms = detectCodeTerms(normalized)
        val hasQuestionSignal = matchedQuestionSignals.isNotEmpty() || normalized.contains("?")
        val hasProjectSignal = matchedProjectSignals.isNotEmpty() || detectedCodeTerms.isNotEmpty()

        val matches = hasQuestionSignal && hasProjectSignal
        if (matches) {
            McpTrace.d(
                "event" to "knowledge_router_match",
                "message" to normalized,
                "questionSignals" to matchedQuestionSignals.joinToString(","),
                "projectSignals" to matchedProjectSignals.joinToString(","),
                "codeTerms" to detectedCodeTerms.joinToString(",")
            )
        }
        return matches
    }

    private fun detectCodeTerms(message: String): List<String> {
        return CODE_TERM_REGEX
            .findAll(message)
            .map { it.value }
            .filter { value ->
                val isLikelyAcronym = value.all { it.isUpperCase() } && value.length <= 4
                val hasCodeShape = !isLikelyAcronym && (
                    value.any { it.isUpperCase() } ||
                    value.contains("viewmodel", ignoreCase = true) ||
                    value.contains("retriever", ignoreCase = true) ||
                    value.contains("provider", ignoreCase = true) ||
                    value.contains("orchestrator", ignoreCase = true) ||
                    value.contains("similarity", ignoreCase = true) ||
                    value.contains("manager", ignoreCase = true)
                )
                hasCodeShape
            }
            .distinct()
            .toList()
    }

    private companion object {
        val CODE_TERM_REGEX = Regex("""[A-Za-z][A-Za-z0-9_]{2,}""")
    }
}
