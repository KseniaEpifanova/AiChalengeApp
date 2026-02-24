package com.example.aichalengeapp.agent

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatAgent(
    private val llmRepository: LlmRepository,
    private val config: AgentConfig = AgentConfig()
) {

    data class AgentConfig(
        val systemPrompt: String = "You are a helpful assistant.",
        val maxTurnsInContext: Int = 12,
        val maxCharsPerMessage: Int = 4000
    )

    private val mutex = Mutex()
    private val history = mutableListOf<AgentMessage>()

    suspend fun handleUserMessage(userText: String): String = mutex.withLock {
        val text = userText.trim()
        if (text.isEmpty()) return ""

        history += AgentMessage(AgentRole.USER, text.take(config.maxCharsPerMessage))

        val context = buildContextLocked()
        val answer = llmRepository.ask(context).trim()

        history += AgentMessage(AgentRole.ASSISTANT, answer.take(config.maxCharsPerMessage))

        return answer
    }

    suspend fun getHistory(): List<AgentMessage> = mutex.withLock { history.toList() }

    suspend fun reset() = mutex.withLock { history.clear() }

    private fun buildContextLocked(): List<AgentMessage> {
        val system = AgentMessage(AgentRole.SYSTEM, config.systemPrompt)

        val turns = history
            .filter { it.role == AgentRole.USER || it.role == AgentRole.ASSISTANT }
            .takeLast(config.maxTurnsInContext)

        return listOf(system) + turns
    }
}

interface LlmRepository {
    suspend fun ask(messages: List<AgentMessage>): String
}