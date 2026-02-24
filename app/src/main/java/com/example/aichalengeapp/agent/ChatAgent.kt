package com.example.aichalengeapp.agent

import com.example.aichalengeapp.data.AgentConfig
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.repo.ChatRepository
import jakarta.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatAgent @Inject constructor(
    private val llmRepository: ChatRepository,
    private val store: ChatHistoryStore,
) {

    private val mutex = Mutex()
    private val history = mutableListOf<AgentMessage>()
    private var isInitialized = false

    private val config: AgentConfig = AgentConfig()

    suspend fun init() = mutex.withLock {
        if (isInitialized) return@withLock
        val saved = store.load()
        history.clear()
        history.addAll(saved)
        isInitialized = true
    }

    suspend fun handleUserMessage(userText: String): String = mutex.withLock {
        if (!isInitialized) {
            val saved = store.load()
            history.clear()
            history.addAll(saved)
            isInitialized = true
        }

        val text = userText.trim()
        if (text.isEmpty()) return ""

        history += AgentMessage(AgentRole.USER, text.take(config.maxCharsPerMessage))

        val context = buildContextLocked()
        val answer = llmRepository.ask(context).trim()
        history += AgentMessage(AgentRole.ASSISTANT, answer.take(config.maxCharsPerMessage))
        store.save(history)

        return answer
    }

    suspend fun getHistory(): List<AgentMessage> = mutex.withLock {
        if (!isInitialized) {
            val saved = store.load()
            history.clear()
            history.addAll(saved)
            isInitialized = true
        }
        history.toList()
    }

    suspend fun reset() = mutex.withLock {
        history.clear()
        store.clear()
    }

    private fun buildContextLocked(): List<AgentMessage> {
        val system = AgentMessage(AgentRole.SYSTEM, config.systemPrompt)

        val turns = history
            .filter { it.role == AgentRole.USER || it.role == AgentRole.ASSISTANT }
            .takeLast(config.maxTurnsInContext)

        return listOf(system) + turns
    }
}
