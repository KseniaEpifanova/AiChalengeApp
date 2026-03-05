package com.example.aichalengeapp.agent.task

import javax.inject.Inject

class TaskPlanner @Inject constructor() {

    fun plan(goal: String): List<String> {
        val normalized = goal.trim()
        if (normalized.isEmpty()) return emptyList()

        val explicit = normalized
            .split("\n", ";")
            .map { it.trim().trimStart('-', '•', '*', ' ') }
            .filter { it.isNotBlank() }

        return if (explicit.size >= 2) {
            explicit
        } else {
            listOf(
                "Clarify success criteria for: $normalized",
                "Execute the core work for: $normalized",
                "Validate and finalize the result"
            )
        }
    }
}
