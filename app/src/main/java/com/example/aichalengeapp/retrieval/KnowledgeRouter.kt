package com.example.aichalengeapp.retrieval

import com.example.aichalengeapp.mcp.McpTrace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRouter @Inject constructor() {

    private val explorationTokens = setOf(
        "how", "where", "what", "explain", "describe", "show",
        "как", "где", "что", "объясни", "расскажи", "покажи"
    )

    private val projectRoots = listOf(
        "project", "codebase", "file", "class", "method", "module", "implementation", "architecture",
        "проект", "код", "файл", "класс", "метод", "модул", "архитект", "реализ", "хран"
    )

    private val technicalRoots = listOf(
        "mcp", "retrieval", "embedding", "sqlite", "rag", "pipeline", "chunk", "prompt",
        "ретрив", "эмбед", "индекс", "чанк", "промпт", "пайплайн"
    )

    fun shouldRetrieve(message: String): Boolean {
        val decision = evaluate(message)
        if (decision.matches) {
            McpTrace.d(
                "event" to "knowledge_router_match",
                "message" to decision.message,
                "reason" to decision.reason,
                "questionSignals" to decision.questionSignals.joinToString(","),
                "groundingSignals" to decision.groundingSignals.joinToString(","),
                "codeTerms" to decision.codeTerms.joinToString(",")
            )
        } else {
            McpTrace.d(
                "event" to "knowledge_router_no_match",
                "message" to decision.message,
                "reason" to decision.reason,
                "questionSignals" to decision.questionSignals.joinToString(","),
                "groundingSignals" to decision.groundingSignals.joinToString(","),
                "codeTerms" to decision.codeTerms.joinToString(",")
            )
        }
        return decision.matches
    }

    private fun evaluate(message: String): RoutingDecision {
        val normalized = message.trim()
        if (normalized.isEmpty()) {
            return RoutingDecision(
                message = normalized,
                matches = false,
                reason = "empty_message"
            )
        }

        val lowered = normalized.lowercase()
        val questionSignals = buildList {
            addAll(explorationTokens.filter { lowered.contains(it) })
            if (normalized.contains("?")) add("question_mark")
        }.distinct()

        val explicitProjectSignals = buildList {
            addAll(projectRoots.filter { lowered.contains(it) })
            addAll(technicalRoots.filter { lowered.contains(it) })
        }.distinct()

        val codeTerms = detectCodeTerms(normalized)
        val hasQuestionIntent = questionSignals.isNotEmpty()
        val hasGroundedProjectEvidence = explicitProjectSignals.isNotEmpty() || codeTerms.isNotEmpty()

        if (!hasQuestionIntent) {
            return RoutingDecision(
                message = normalized,
                matches = false,
                reason = "no_question_or_exploration_intent",
                questionSignals = questionSignals,
                groundingSignals = explicitProjectSignals,
                codeTerms = codeTerms
            )
        }

        if (!hasGroundedProjectEvidence) {
            return RoutingDecision(
                message = normalized,
                matches = false,
                reason = "missing_project_grounding",
                questionSignals = questionSignals,
                groundingSignals = explicitProjectSignals,
                codeTerms = codeTerms
            )
        }

        return RoutingDecision(
            message = normalized,
            matches = true,
            reason = if (codeTerms.isNotEmpty()) "strong_code_entity_detected" else "explicit_project_context",
            questionSignals = questionSignals,
            groundingSignals = explicitProjectSignals,
            codeTerms = codeTerms
        )
    }

    private fun detectCodeTerms(message: String): List<String> {
        return CODE_TERM_REGEX.findAll(message)
            .map { it.value }
            .filter { value ->
                hasStrongCodeShape(value) || hasCodeSuffix(value) || value.endsWith(".kt", ignoreCase = true)
            }
            .distinct()
            .toList()
    }

    private fun hasStrongCodeShape(value: String): Boolean {
        val isShortAcronym = value.length <= 4 && value.all { it.isUpperCase() }
        if (isShortAcronym) return false
        return value.drop(1).any { it.isUpperCase() } || value.contains('_')
    }

    private fun hasCodeSuffix(value: String): Boolean {
        return CODE_SUFFIXES.any { suffix ->
            value.endsWith(suffix, ignoreCase = false) && value.length > suffix.length
        }
    }

    private data class RoutingDecision(
        val message: String,
        val matches: Boolean,
        val reason: String,
        val questionSignals: List<String> = emptyList(),
        val groundingSignals: List<String> = emptyList(),
        val codeTerms: List<String> = emptyList()
    )

    private companion object {
        val CODE_TERM_REGEX = Regex("""[A-Za-z][A-Za-z0-9_.]{2,}""")
        val CODE_SUFFIXES = listOf(
            "ViewModel", "Manager", "Retriever", "Provider", "Router", "Repository", "Orchestrator", "Client", "Agent"
        )
    }
}
