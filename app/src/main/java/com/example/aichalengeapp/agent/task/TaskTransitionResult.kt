package com.example.aichalengeapp.agent.task

sealed class TaskTransitionResult {
    data class Success(val newState: TaskState) : TaskTransitionResult()
    data class Invalid(
        val reason: String,
        val suggestedNextAction: String
    ) : TaskTransitionResult()
}
