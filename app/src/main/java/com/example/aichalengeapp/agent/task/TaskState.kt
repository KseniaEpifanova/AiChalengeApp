package com.example.aichalengeapp.agent.task

data class TaskState(
    val goal: String,
    val stage: TaskStage = TaskStage.PLANNING,
    val paused: Boolean = false,
    val steps: List<TaskStep> = emptyList(),
    val planApproved: Boolean = false
)
