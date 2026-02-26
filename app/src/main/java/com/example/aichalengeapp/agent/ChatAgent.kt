package com.example.aichalengeapp.agent

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.repo.ChatRepository
import jakarta.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatAgent @Inject constructor(
    private val llmRepository: ChatRepository,
    private val store: ChatHistoryStore,
    private val tokenEstimator: TokenEstimator
) {
    private val mutex = Mutex()
    private val history = mutableListOf<AgentMessage>()
    private var initialized = false

    private val contextLimitTokens = 128_000
    private val maxOutputTokens = 512

    private val systemPrompt = "You are a helpful assistant."

    suspend fun init() = mutex.withLock {
        if (initialized) return@withLock
        history.clear()
        history.addAll(store.load())
        initialized = true
    }

    suspend fun getHistory(): List<AgentMessage> = mutex.withLock {
        if (!initialized) init()
        history.toList()
    }

    suspend fun reset() = mutex.withLock {
        history.clear()
        store.clear()
    }

    suspend fun handleUserMessage(userText: String): AgentReply = mutex.withLock {
        if (!initialized) init()

        val text = userText.trim()
        if (text.isEmpty()) {
            return AgentReply(
                text = "",
                metrics = TokenMetrics(
                    estimatedUserTokens = 0,
                    estimatedHistoryTokens = 0,
                    estimatedPromptTokens = 0,
                    actualPromptTokens = null,
                    actualCompletionTokens = null,
                    actualTotalTokens = null,
                    estimatedCostUsd = null
                )
            )
        }

        val userMsg = AgentMessage(AgentRole.USER, text)
        history += userMsg

        val contextMessages = buildContextLocked()
        val estimatedPromptTokens = tokenEstimator.estimateTokens(contextMessages)

        val estimatedUserTokens = tokenEstimator.estimateTokens(text)
        val estimatedHistoryTokens = (estimatedPromptTokens - estimatedUserTokens).coerceAtLeast(0)

        if (estimatedPromptTokens + maxOutputTokens > contextLimitTokens) {
            history.removeAt(history.lastIndex)
            throw ContextOverflowException(
                "Context overflow: estimated prompt=$estimatedPromptTokens + maxOutput=$maxOutputTokens > limit=$contextLimitTokens"
            )
        }

        val llm = llmRepository.ask(contextMessages, maxOutputTokens)
        val answer = llm.text.trim()

        history += AgentMessage(AgentRole.ASSISTANT, answer)
        store.save(history)

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

    private fun buildContextLocked(): List<AgentMessage> {
        val system = AgentMessage(AgentRole.SYSTEM, systemPrompt)

        val turns = history
            .filter { it.role == AgentRole.USER || it.role == AgentRole.ASSISTANT }
            .takeLast(40)

        return listOf(system) + turns
    }
}
