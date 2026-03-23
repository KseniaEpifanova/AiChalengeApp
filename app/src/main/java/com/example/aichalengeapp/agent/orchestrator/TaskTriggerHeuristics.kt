package com.example.aichalengeapp.agent.orchestrator

object TaskTriggerHeuristics {

    data class TriggerSignals(
        val hasExecutionSignals: Boolean,
        val hasExplorationSignals: Boolean,
        val executionHits: List<String>,
        val explorationHits: List<String>
    )

    private val executionMarkers = listOf(
        "implement", "build", "create", "fix", "refactor", "develop", "add", "write",
        "реализ", "сдела", "созда", "исправ", "добав", "разработ", "напиш", "отрефактор", "переработ"
    )

    private val explorationMarkers = listOf(
        "understand", "overview", "explain", "describe", "how does", "what does", "walk through", "tell me",
        "понять", "разобрать", "разобраться", "обзор", "объясни", "расскажи", "как работает", "как устро", "что делает",
        "хочу понять", "хочу разобраться", "начни с общего обзора", "общий обзор"
    )

    fun analyze(message: String): TriggerSignals {
        val normalized = message.trim().lowercase()
        val executionHits = executionMarkers.filter { normalized.contains(it) }
        val explorationHits = explorationMarkers.filter { normalized.contains(it) }.toMutableList()
        if (normalized.endsWith("?")) {
            explorationHits += "question_shape"
        }
        return TriggerSignals(
            hasExecutionSignals = executionHits.isNotEmpty(),
            hasExplorationSignals = explorationHits.isNotEmpty(),
            executionHits = executionHits,
            explorationHits = explorationHits
        )
    }
}
