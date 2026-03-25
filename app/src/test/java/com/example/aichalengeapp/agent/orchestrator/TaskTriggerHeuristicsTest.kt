package com.example.aichalengeapp.agent.orchestrator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTriggerHeuristicsTest {

    @Test
    fun `greeting stays normal chat`() {
        val decision = TaskTriggerHeuristics.evaluateAutoStart(
            message = "привет",
            requestKind = RequestKind.SIMPLE,
            normalizedIntent = TaskChatIntent.START_COMPLEX_TASK
        )

        assertFalse(decision.shouldStartTask)
        assertTrue(decision.reason.contains("social"))
    }

    @Test
    fun `acknowledgement stays normal chat`() {
        val decision = TaskTriggerHeuristics.evaluateAutoStart(
            message = "thanks",
            requestKind = RequestKind.SIMPLE,
            normalizedIntent = TaskChatIntent.START_COMPLEX_TASK
        )

        assertFalse(decision.shouldStartTask)
        assertTrue(decision.reason.contains("social"))
    }

    @Test
    fun `simple exploratory request stays normal chat`() {
        val decision = TaskTriggerHeuristics.evaluateAutoStart(
            message = "Я хочу понять, как устроен чат",
            requestKind = RequestKind.SIMPLE,
            normalizedIntent = TaskChatIntent.START_COMPLEX_TASK
        )

        assertFalse(decision.shouldStartTask)
        assertTrue(decision.reason.contains("exploration"))
    }

    @Test
    fun `explicit execution request starts task`() {
        val decision = TaskTriggerHeuristics.evaluateAutoStart(
            message = "Исправь баг с запуском task lifecycle",
            requestKind = RequestKind.COMPLEX,
            normalizedIntent = TaskChatIntent.START_COMPLEX_TASK
        )

        assertTrue(decision.shouldStartTask)
    }

    @Test
    fun `normalized intent alone cannot force start task without execution evidence`() {
        val decision = TaskTriggerHeuristics.evaluateAutoStart(
            message = "hello",
            requestKind = RequestKind.SIMPLE,
            normalizedIntent = TaskChatIntent.START_COMPLEX_TASK
        )

        assertFalse(decision.shouldStartTask)
        assertTrue(decision.reason.contains("social") || decision.reason.contains("simple"))
    }
}
