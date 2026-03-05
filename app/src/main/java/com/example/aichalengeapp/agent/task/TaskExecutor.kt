package com.example.aichalengeapp.agent.task

import javax.inject.Inject

class TaskExecutor @Inject constructor() {

    fun advance(state: TaskState): TaskState {
        if (state.paused || state.stage == TaskStage.DONE) return state

        return when (state.stage) {
            TaskStage.PLANNING -> {
                if (state.steps.isEmpty()) {
                    state.copy(stage = TaskStage.DONE)
                } else {
                    state.copy(stage = TaskStage.EXECUTION, currentStep = 0)
                }
            }
            TaskStage.EXECUTION -> {
                val next = state.currentStep + 1
                if (next >= state.steps.size) {
                    state.copy(stage = TaskStage.VALIDATION, currentStep = state.steps.size)
                } else {
                    state.copy(currentStep = next)
                }
            }
            TaskStage.VALIDATION -> state
            TaskStage.DONE -> state
        }
    }
}
