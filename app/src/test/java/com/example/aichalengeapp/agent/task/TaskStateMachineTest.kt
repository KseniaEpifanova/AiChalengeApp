package com.example.aichalengeapp.agent.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStateMachineTest {

    private val machine = TaskStateMachine()

    @Test
    fun `valid transitions work`() {
        val start = TaskState(goal = "Goal", stage = TaskStage.PLANNING, planApproved = true)
        val execution = (machine.transition(start, TaskStage.EXECUTION) as TaskTransitionResult.Success).newState
        assertEquals(TaskStage.EXECUTION, execution.stage)
    }

    @Test
    fun `invalid transition is rejected`() {
        val start = TaskState(goal = "Goal", stage = TaskStage.PLANNING)
        val result = machine.transition(start, TaskStage.DONE)
        assertTrue(result is TaskTransitionResult.Invalid)
        assertEquals(TaskStage.PLANNING, start.stage)
    }

    @Test
    fun `planning to done is rejected`() {
        val planning = TaskState(goal = "Goal", stage = TaskStage.PLANNING, planApproved = true)
        val result = machine.transition(planning, TaskStage.DONE)
        assertTrue(result is TaskTransitionResult.Invalid)
    }

    @Test
    fun `planning to validation is rejected`() {
        val planning = TaskState(goal = "Goal", stage = TaskStage.PLANNING, planApproved = true)
        val result = machine.transition(planning, TaskStage.VALIDATION)
        assertTrue(result is TaskTransitionResult.Invalid)
    }

    @Test
    fun `cannot execute before plan approval`() {
        val planning = TaskState(goal = "Goal", stage = TaskStage.PLANNING, planApproved = false)
        val result = machine.transition(planning, TaskStage.EXECUTION)
        assertTrue(result is TaskTransitionResult.Invalid)
    }

    @Test
    fun `approve plan allows planning to execution`() {
        val planning = TaskState(goal = "Goal", stage = TaskStage.PLANNING, planApproved = false)
        val approved = machine.approvePlan(planning)
        val transition = machine.transition(approved, TaskStage.EXECUTION)
        assertTrue(transition is TaskTransitionResult.Success)
    }

    @Test
    fun `cannot finish before validation`() {
        val execution = TaskState(goal = "Goal", stage = TaskStage.EXECUTION, planApproved = true)
        val result = machine.transition(execution, TaskStage.DONE)
        assertTrue(result is TaskTransitionResult.Invalid)
    }

    @Test
    fun `execution to done is rejected`() {
        val execution = TaskState(goal = "Goal", stage = TaskStage.EXECUTION, planApproved = true)
        val result = machine.transition(execution, TaskStage.DONE)
        assertTrue(result is TaskTransitionResult.Invalid)
    }

    @Test
    fun `valid transition still works after invalid attempt`() {
        val execution = TaskState(goal = "Goal", stage = TaskStage.EXECUTION, planApproved = true)
        val invalid = machine.transition(execution, TaskStage.DONE)
        assertTrue(invalid is TaskTransitionResult.Invalid)
        val valid = machine.transition(execution, TaskStage.VALIDATION)
        assertTrue(valid is TaskTransitionResult.Success)
        assertEquals(TaskStage.EXECUTION, execution.stage)
    }

    @Test
    fun `pause resume preserves stage`() {
        val execution = TaskState(goal = "Goal", stage = TaskStage.EXECUTION)
        val paused = machine.pause(execution)
        val resumed = machine.resume(paused)
        assertTrue(paused.paused)
        assertEquals(TaskStage.EXECUTION, resumed.stage)
        assertTrue(!resumed.paused)
    }

    @Test
    fun `execution validation done flow is allowed`() {
        val execution = TaskState(goal = "Goal", stage = TaskStage.EXECUTION)
        val validation = (machine.transition(execution, TaskStage.VALIDATION) as TaskTransitionResult.Success).newState
        val done = (machine.transition(validation, TaskStage.DONE) as TaskTransitionResult.Success).newState
        assertEquals(TaskStage.DONE, done.stage)
    }

    @Test
    fun `no transition while paused except cancel`() {
        val paused = TaskState(goal = "Goal", stage = TaskStage.EXECUTION, paused = true)
        val invalid = machine.transition(paused, TaskStage.VALIDATION)
        val cancelled = machine.transition(paused, TaskStage.CANCELLED)

        assertTrue(invalid is TaskTransitionResult.Invalid)
        assertTrue(cancelled is TaskTransitionResult.Success)
    }
}
