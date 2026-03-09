package com.example.aichalengeapp.ui

import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState

internal fun isTaskPanelVisible(state: TaskState?): Boolean {
    return state != null && state.stage != TaskStage.DONE && state.stage != TaskStage.CANCELLED
}
