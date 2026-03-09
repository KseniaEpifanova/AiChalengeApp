package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.task.TaskState
import javax.inject.Inject

class TaskConflictDetector @Inject constructor() {

    fun shouldBlockConcurrentStart(
        activeTask: TaskState,
        incomingMessage: String,
        requestKind: RequestKind,
        hasTransitionIntent: Boolean
    ): Boolean {
        if (requestKind != RequestKind.COMPLEX) return false

        val normalized = incomingMessage.trim().lowercase()
        if (normalized.isEmpty()) return false

        val controlIntent = listOf(
            "pause", "resume", "cancel", "stop", "next", "continue",
            "пауза", "продолж", "отмен", "стоп", "дальше", "далее"
        ).any { normalized.contains(it) }
        if (controlIntent || hasTransitionIntent) return false

        val explicitNewTaskIntent = listOf(
            "new task", "another task", "start task", "switch task",
            "новую задачу", "другая задача", "начни задачу", "новый таск"
        ).any { normalized.contains(it) }

        val messageTokens = normalized.tokenizeForTaskSimilarity()
        val goalTokens = activeTask.goal.lowercase().tokenizeForTaskSimilarity()
        val overlap = messageTokens.intersect(goalTokens).size

        return explicitNewTaskIntent || overlap < 2
    }

    private fun String.tokenizeForTaskSimilarity(): Set<String> {
        val stopWords = setOf(
            "the", "a", "an", "and", "or", "to", "for", "with", "on", "in", "of", "is",
            "и", "в", "на", "по", "с", "к", "для", "как", "это", "что", "помоги", "сделать"
        )
        return lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
    }
}
