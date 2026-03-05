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
    fun `executor transitions planning to execution`() {
        val executor = TaskExecutor()
        val initial = TaskState(
            goal = "Goal",
            stage = TaskStage.PLANNING,
            currentStep = 0,
            steps = listOf("A", "B"),
            paused = false
        )
        val next = executor.advance(initial)
        assertEquals(TaskStage.EXECUTION, next.stage)
        assertEquals(0, next.currentStep)
    }

    @Test
    fun `validator marks complete when all steps consumed`() {
        val validator = TaskValidator()
        val done = TaskState(
            goal = "Goal",
            stage = TaskStage.VALIDATION,
            currentStep = 2,
            steps = listOf("A", "B"),
            paused = false
        )
        assertTrue(validator.isComplete(done))
    }
}
