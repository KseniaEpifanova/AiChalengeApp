package com.example.aichalengeapp.ui

import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPanelVisibilityTest {

    @Test
    fun `panel visible for active lifecycle stages`() {
        assertTrue(isTaskPanelVisible(TaskState(goal = "x", stage = TaskStage.PLANNING)))
        assertTrue(isTaskPanelVisible(TaskState(goal = "x", stage = TaskStage.EXECUTION)))
        assertTrue(isTaskPanelVisible(TaskState(goal = "x", stage = TaskStage.VALIDATION)))
    }

    @Test
    fun `panel hidden for done cancelled or null`() {
        assertFalse(isTaskPanelVisible(TaskState(goal = "x", stage = TaskStage.DONE)))
        assertFalse(isTaskPanelVisible(TaskState(goal = "x", stage = TaskStage.CANCELLED)))
        assertFalse(isTaskPanelVisible(null))
    }

    @Test
    fun `done reached through chat should still hide panel`() {
        val stateFromChatTransition = TaskState(goal = "x", stage = TaskStage.DONE)
        assertFalse(isTaskPanelVisible(stateFromChatTransition))
    }
}
