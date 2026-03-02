package com.example.aichalengeapp.agent

import com.example.aichalengeapp.agent.context.AgentMemoryState
import com.example.aichalengeapp.agent.context.BranchData
import com.example.aichalengeapp.agent.context.ContextStrategySelector
import com.example.aichalengeapp.agent.context.StrategyConfig
import com.example.aichalengeapp.agent.facts.FactsUpdater
import com.example.aichalengeapp.agent.memory.AgentMemoryStore
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.repo.ChatRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class ChatAgent @Inject constructor(
    private val llmRepository: ChatRepository,
    private val memoryStore: AgentMemoryStore,
    private val selector: ContextStrategySelector,
    private val tokenEstimator: TokenEstimator,
    private val factsUpdater: FactsUpdater
) {
    private val mutex = Mutex()
    private var memory: AgentMemoryState = AgentMemoryState()
    private var initialized = false

    private val systemPrompt = "You are a helpful assistant."
    private val maxOutputTokens = 512

    suspend fun init() = mutex.withLock {
        if (initialized) return@withLock
        memory = memoryStore.load()
        initialized = true

        // На всякий случай: если activeBranchId есть, но ветки нет — создадим.
        val active = memory.branching.activeBranchId
        if (active != null && !memory.branching.branches.containsKey(active)) {
            val branches = memory.branching.branches.toMutableMap()
            branches[active] = BranchData(emptyList())
            memory = memory.copy(branching = memory.branching.copy(branches = branches))
            memoryStore.save(memory)
        }
    }

    suspend fun getHistory(): List<AgentMessage> = mutex.withLock {
        if (!initialized) init()
        memory.history
    }

    suspend fun reset() = mutex.withLock {
        memory = AgentMemoryState()
        memoryStore.clear()
    }

    suspend fun handleUserMessage(
        userText: String,
        strategyConfig: StrategyConfig
    ): AgentReply = mutex.withLock {
        if (!initialized) init()

        val trimmed = userText.trim()
        if (trimmed.isEmpty()) {
            return@withLock AgentReply("", TokenMetrics(0, 0, 0, null, null, null, null))
        }

        appendUserMessage(strategyConfig, trimmed)

        // Sticky facts
        if (strategyConfig is StrategyConfig.StickyFacts) {
            val updatedFacts = factsUpdater.updateFacts(memory.factsJson, trimmed)
            memory = memory.copy(factsJson = updatedFacts)
            memoryStore.save(memory)
        }

        val strategy = selector.select(strategyConfig)
        val plan = strategy.build(memory, strategyConfig)

        val llmMessages = buildList {
            add(AgentMessage(AgentRole.SYSTEM, systemPrompt))
            addAll(plan.messagesForLlm)
        }

        val estimatedPrompt = tokenEstimator.estimateTokens(llmMessages)
        val estimatedUser = tokenEstimator.estimateTokens(trimmed)
        val estimatedHistory = (estimatedPrompt - estimatedUser).coerceAtLeast(0)

        val result = llmRepository.ask(llmMessages, maxOutputTokens = maxOutputTokens)
        val answer = result.text.trim()

        appendAssistantMessage(strategyConfig, answer)
        memoryStore.save(memory)

        val actualPrompt = result.usage?.promptTokens
        val actualCompletion = result.usage?.completionTokens
        val cost = if (actualPrompt != null && actualCompletion != null) {
            CostEstimator.estimateUsd(actualPrompt, actualCompletion)
        } else null

        return@withLock AgentReply(
            text = answer,
            metrics = TokenMetrics(
                estimatedUserTokens = estimatedUser,
                estimatedHistoryTokens = estimatedHistory,
                estimatedPromptTokens = estimatedPrompt,
                actualPromptTokens = actualPrompt,
                actualCompletionTokens = actualCompletion,
                actualTotalTokens = result.usage?.totalTokens,
                estimatedCostUsd = cost
            )
        )
    }

    /**
     * Branching API
     */
    suspend fun setCheckpointAtCurrent() = mutex.withLock {
        if (!initialized) init()

        // checkpoint — это текущая длина базы (history без system)
        val idx = memory.history.count { it.role != AgentRole.SYSTEM }
        memory = memory.copy(branching = memory.branching.copy(checkpointIndex = idx))

        // если нет активной ветки — по умолчанию "A"
        if (memory.branching.activeBranchId == null) {
            val branches = memory.branching.branches.toMutableMap()
            if (!branches.containsKey("A")) branches["A"] = BranchData(emptyList())
            memory = memory.copy(
                branching = memory.branching.copy(
                    branches = branches,
                    activeBranchId = "A"
                )
            )
        }

        memoryStore.save(memory)
    }

    suspend fun createBranch(branchId: String) = mutex.withLock {
        if (!initialized) init()
        val branches = memory.branching.branches.toMutableMap()
        if (!branches.containsKey(branchId)) {
            branches[branchId] = BranchData(messages = emptyList())
        }
        memory = memory.copy(branching = memory.branching.copy(branches = branches, activeBranchId = branchId))
        memoryStore.save(memory)
    }

    suspend fun switchBranch(branchId: String) = mutex.withLock {
        if (!initialized) init()
        if (!memory.branching.branches.containsKey(branchId)) return@withLock
        memory = memory.copy(branching = memory.branching.copy(activeBranchId = branchId))
        memoryStore.save(memory)
    }

    suspend fun getBranches(): List<String> = mutex.withLock {
        if (!initialized) init()
        memory.branching.branches.keys.sorted()
    }

    suspend fun getActiveBranchId(): String? = mutex.withLock {
        if (!initialized) init()
        memory.branching.activeBranchId
    }

    suspend fun getFactsJson(): String = mutex.withLock {
        if (!initialized) init()
        memory.factsJson
    }

    private fun appendUserMessage(config: StrategyConfig, text: String) {
        val msg = AgentMessage(AgentRole.USER, text)

        when (config) {
            is StrategyConfig.Branching -> {
                val checkpoint = memory.branching.checkpointIndex
                if (checkpoint == null) {
                    // до чекпоинта — пишем в общую историю (это база)
                    memory = memory.copy(history = memory.history + msg)
                } else {
                    // после чекпоинта — пишем в активную ветку
                    val id = memory.branching.activeBranchId ?: config.branchId
                    val branches = memory.branching.branches.toMutableMap()
                    val existing = branches[id]?.messages.orEmpty()
                    branches[id] = BranchData(messages = existing + msg)
                    memory = memory.copy(branching = memory.branching.copy(branches = branches, activeBranchId = id))
                }
            }

            else -> memory = memory.copy(history = memory.history + msg)
        }
    }

    private fun appendAssistantMessage(config: StrategyConfig, text: String) {
        val msg = AgentMessage(AgentRole.ASSISTANT, text)

        when (config) {
            is StrategyConfig.Branching -> {
                val checkpoint = memory.branching.checkpointIndex
                if (checkpoint == null) {
                    memory = memory.copy(history = memory.history + msg)
                } else {
                    val id = memory.branching.activeBranchId ?: config.branchId
                    val branches = memory.branching.branches.toMutableMap()
                    val existing = branches[id]?.messages.orEmpty()
                    branches[id] = BranchData(messages = existing + msg)
                    memory = memory.copy(branching = memory.branching.copy(branches = branches, activeBranchId = id))
                }
            }

            else -> memory = memory.copy(history = memory.history + msg)
        }
    }
}
