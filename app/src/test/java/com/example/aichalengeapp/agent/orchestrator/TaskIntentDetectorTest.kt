package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.LlmResult
import com.example.aichalengeapp.repo.ChatRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskIntentDetectorTest {

    @Test
    fun `detects intents from LLM labels`() = runBlocking {
        assertIntent("START_COMPLEX_TASK", "спроектируй экран списка фильмов", TaskChatIntent.START_COMPLEX_TASK)
        assertIntent("START_COMPLEX_TASK", "Помоги спроектировать экран списка фильмов", TaskChatIntent.START_COMPLEX_TASK)
        assertIntent("APPROVE_PLAN", "соглашаюсь с планом", TaskChatIntent.APPROVE_PLAN)
        assertIntent("CONTINUE_TASK", "давай дальше", TaskChatIntent.CONTINUE_TASK)
        assertIntent("REQUEST_VALIDATION", "проверим результат", TaskChatIntent.REQUEST_VALIDATION)
        assertIntent("FINISH_TASK", "всё ок, завершаем", TaskChatIntent.FINISH_TASK)
        assertIntent("CANCEL_TASK", "отмени задачу", TaskChatIntent.CANCEL_TASK)
        assertIntent("NONE", "какая сегодня погода", TaskChatIntent.NONE)
    }

    @Test
    fun `exploration request short circuits to none when no active task`() = runBlocking {
        val repo = object : ChatRepository {
            override suspend fun ask(messages: List<AgentMessage>, maxOutputTokens: Int?): LlmResult {
                return LlmResult("START_COMPLEX_TASK")
            }
        }
        val detector = TaskIntentDetector(repo)
        val result = detector.detect(
            message = "Я хочу понять, как в проекте устроен чат. Начни с общего обзора ChatAgent и ViewModel.",
            activeProfile = AssistantProfile.mobileDeveloper(),
            taskState = null
        )
        assertEquals(TaskChatIntent.NONE, result.intent)
    }

    private suspend fun assertIntent(
        llmLabel: String,
        message: String,
        expected: TaskChatIntent
    ) {
        val repo = object : ChatRepository {
            override suspend fun ask(messages: List<AgentMessage>, maxOutputTokens: Int?): LlmResult {
                return LlmResult(llmLabel)
            }
        }
        val detector = TaskIntentDetector(repo)
        val result = detector.detect(
            message = message,
            activeProfile = AssistantProfile.mobileDeveloper(),
            taskState = TaskState(goal = "G", stage = TaskStage.PLANNING)
        )
        assertEquals(expected, result.intent)
    }
}
