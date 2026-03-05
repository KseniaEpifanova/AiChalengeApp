package com.example.aichalengeapp.agent.task

data class TaskState(
    val goal: String,
    val stage: TaskStage,
    val currentStep: Int,
    val steps: List<String>,
    val paused: Boolean
)

enum class TaskStage {
    PLANNING,
    EXECUTION,
    VALIDATION,
    DONE
}
