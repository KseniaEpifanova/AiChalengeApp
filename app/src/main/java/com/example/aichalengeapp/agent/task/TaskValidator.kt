package com.example.aichalengeapp.agent.task

import javax.inject.Inject

class TaskValidator @Inject constructor() {

    fun isComplete(state: TaskState): Boolean {
        return state.currentStep >= state.steps.size
    }
}
