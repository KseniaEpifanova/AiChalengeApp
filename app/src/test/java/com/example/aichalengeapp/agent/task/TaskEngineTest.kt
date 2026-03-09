package com.example.aichalengeapp.agent.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskEngineTest {

    @Test
    fun `planner creates fallback steps for short goals`() {
        val planner = TaskPlanner()
        val steps = planner.plan("Ship release")
        assertEquals(3, steps.size)
    }

    @Test
    fun `executor does not move planning without approval`() {
        val executor = TaskExecutor(TaskStateMachine())
        val initial = TaskState(goal = "Goal", stage = TaskStage.PLANNING, planApproved = false)
        val next = executor.advance(initial)
        assertEquals(TaskStage.PLANNING, next.stage)
    }

    @Test
    fun `executor moves planning to execution when approved`() {
        val executor = TaskExecutor(TaskStateMachine())
        val initial = TaskState(goal = "Goal", stage = TaskStage.PLANNING, planApproved = true)
        val next = executor.advance(initial)
        assertEquals(TaskStage.EXECUTION, next.stage)
    }

    @Test
    fun `validator marks complete when all steps completed`() {
        val validator = TaskValidator()
        val done = TaskState(
            goal = "Goal",
            stage = TaskStage.VALIDATION
        )
        assertTrue(validator.isComplete(done))
    }

    @Test
    fun `stage to step mapping is deterministic`() {
        assertEquals("planning", stageToStep(TaskStage.PLANNING).id)
        assertEquals("implementation", stageToStep(TaskStage.EXECUTION).id)
        assertEquals("validation", stageToStep(TaskStage.VALIDATION).id)
        assertEquals("done", stageToStep(TaskStage.DONE).id)
        assertEquals("cancelled", stageToStep(TaskStage.CANCELLED).id)
    }
}
