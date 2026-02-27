package com.example.aichalengeapp.agent

import com.example.aichalengeapp.data.MemorySnapshot
import com.example.aichalengeapp.repo.ChatRepository
import com.example.aichalengeapp.agent.context.ContextManager
import com.example.aichalengeapp.agent.memory.ChatMemoryStore
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class ChatAgent @Inject constructor(
    private val llmRepository: ChatRepository,
    private val memoryStore: ChatMemoryStore,
    private val contextManager: ContextManager,
    private val tokenEstimator: TokenEstimator
) {
    private val mutex = Mutex()
    private var snapshot = MemorySnapshot(
        summary = "",
        messages = emptyList(),
        summarizedCount = 0
    )
    private var initialized = false

    private val systemPrompt = "You are a helpful assistant"

    suspend fun init() = mutex.withLock {
        if (initialized) return@withLock
        snapshot = memoryStore.load()
        initialized = true
    }

    suspend fun reset() = mutex.withLock {
        snapshot = snapshot.copy(summary = "", messages = emptyList(), summarizedCount = 0)
        memoryStore.clear()
    }

    suspend fun getHistory(): List<AgentMessage> = mutex.withLock {
        if (!initialized) init()
        snapshot.messages
    }

    suspend fun handleUserMessage(userText: String, compressionEnabled: Boolean = true): AgentReply = mutex.withLock {
        if (!initialized) init()

        val text = userText.trim()
        if (text.isEmpty()) {
            return AgentReply("", TokenMetrics(0,0,0,null,null,null,null))
        }

        val updatedMessages = snapshot.messages + AgentMessage(AgentRole.USER, text)
        snapshot = snapshot.copy(messages = updatedMessages)

        val plan = if (compressionEnabled) contextManager.prepare(snapshot) else null

        if (plan?.updatedSnapshot != null) {
            snapshot = plan.updatedSnapshot
            memoryStore.save(snapshot)
        }

        val contextMessages = buildPromptMessages(
            summary = if (compressionEnabled) (plan?.summary ?: snapshot.summary) else "",
            tail = if (compressionEnabled) (plan?.tailMessages ?: snapshot.messages) else snapshot.messages
        )

        val estimatedPromptTokens = tokenEstimator.estimateTokens(contextMessages)
        val estimatedUserTokens = tokenEstimator.estimateTokens(text)
        val estimatedHistoryTokens = (estimatedPromptTokens - estimatedUserTokens).coerceAtLeast(0)

        val llm = llmRepository.ask(contextMessages, maxOutputTokens = 512)
        val answer = llm.text.trim()

        snapshot = snapshot.copy(messages = snapshot.messages + AgentMessage(AgentRole.ASSISTANT, answer))
        memoryStore.save(snapshot)

        val actualPrompt = llm.usage?.promptTokens
        val actualCompletion = llm.usage?.completionTokens
        val cost = if (actualPrompt != null && actualCompletion != null) {
            CostEstimator.estimateUsd(actualPrompt, actualCompletion)
        } else null

        return AgentReply(
            text = answer,
            metrics = TokenMetrics(
                estimatedUserTokens = estimatedUserTokens,
                estimatedHistoryTokens = estimatedHistoryTokens,
                estimatedPromptTokens = estimatedPromptTokens,
                actualPromptTokens = actualPrompt,
                actualCompletionTokens = actualCompletion,
                actualTotalTokens = llm.usage?.totalTokens,
                estimatedCostUsd = cost
            )
        )
    }

    private fun buildPromptMessages(summary: String, tail: List<AgentMessage>): List<AgentMessage> {
        val result = ArrayList<AgentMessage>(2 + tail.size)

        result += AgentMessage(AgentRole.SYSTEM, systemPrompt)

        if (summary.isNotBlank()) {
            result += AgentMessage(
                AgentRole.SYSTEM,
                "Conversation summary (memory):\n$summary"
            )
        }

        result += tail.filter { it.role != AgentRole.SYSTEM }

        return result
    }
}