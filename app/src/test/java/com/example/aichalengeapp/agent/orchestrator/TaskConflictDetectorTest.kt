package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskConflictDetectorTest {

    private val detector = TaskConflictDetector()
    private val activeTask = TaskState(
        goal = "Спроектировать экран списка фильмов с поиском",
        stage = TaskStage.PLANNING
    )

    @Test
    fun `blocks explicit new task while another task is active`() {
        val blocked = detector.shouldBlockConcurrentStart(
            activeTask = activeTask,
            incomingMessage = "Start a new task: design checkout flow",
            requestKind = RequestKind.COMPLEX,
            hasTransitionIntent = false
        )
        assertTrue(blocked)
    }

    @Test
    fun `does not block continuation of same task topic`() {
        val blocked = detector.shouldBlockConcurrentStart(
            activeTask = activeTask,
            incomingMessage = "Добавь шаги для экрана списка фильмов и поиска",
            requestKind = RequestKind.COMPLEX,
            hasTransitionIntent = false
        )
        assertFalse(blocked)
    }

    @Test
    fun `does not block stage transition intent`() {
        val blocked = detector.shouldBlockConcurrentStart(
            activeTask = activeTask,
            incomingMessage = "перейти в execution",
            requestKind = RequestKind.COMPLEX,
            hasTransitionIntent = true
        )
        assertFalse(blocked)
    }
}
