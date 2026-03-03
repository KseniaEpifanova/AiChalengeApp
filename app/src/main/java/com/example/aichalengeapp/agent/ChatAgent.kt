package com.example.aichalengeapp.agent

import com.example.aichalengeapp.agent.context.AgentMemoryState
import com.example.aichalengeapp.agent.context.ContextStrategySelector
import com.example.aichalengeapp.agent.context.StrategyConfig
import com.example.aichalengeapp.agent.facts.FactsUpdater
import com.example.aichalengeapp.agent.memory.AgentMemoryStore
import com.example.aichalengeapp.agent.memory.LongTermMemoryStore
import com.example.aichalengeapp.agent.memory.WorkingMemoryStore
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.repo.ChatRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class ChatAgent @Inject constructor(
    private val llmRepository: ChatRepository,
    private val shortTermStore: AgentMemoryStore,
    private val workingStore: WorkingMemoryStore,
    private val longTermStore: LongTermMemoryStore,
    private val selector: ContextStrategySelector,
    private val tokenEstimator: TokenEstimator,
    private val factsUpdater: FactsUpdater
) {
    private val mutex = Mutex()

    private var shortTerm: AgentMemoryState = AgentMemoryState()
    private var initialized = false

    private var workingJson: String = ""
    private var longTermJson: String = ""

    private val systemPromptBase = "You are a helpful assistant."
    private val maxOutputTokens = 512

    suspend fun init() = mutex.withLock {
        if (initialized) return@withLock
        shortTerm = shortTermStore.load()
        workingJson = workingStore.loadJson()
        longTermJson = longTermStore.loadJson()
        initialized = true
    }

    suspend fun resetAll() = mutex.withLock {
        if (!initialized) init()
        shortTerm = AgentMemoryState()
        workingJson = ""
        longTermJson = ""
        shortTermStore.clear()
        workingStore.clear()
        longTermStore.clear()
    }

    suspend fun resetShortTermOnly() = mutex.withLock {
        if (!initialized) init()
        shortTerm = AgentMemoryState()
        shortTermStore.clear()
    }

    suspend fun clearWorkingOnly() = mutex.withLock {
        if (!initialized) init()
        workingJson = ""
        workingStore.clear()
    }

    suspend fun clearLongTermOnly() = mutex.withLock {
        if (!initialized) init()
        longTermJson = ""
        longTermStore.clear()
    }

    suspend fun getHistory(): List<AgentMessage> = mutex.withLock {
        if (!initialized) init()
        shortTerm.history
    }

    suspend fun getFactsJson(): String = mutex.withLock {
        if (!initialized) init()
        shortTerm.factsJson
    }

    suspend fun getWorkingJson(): String = mutex.withLock {
        if (!initialized) init()
        workingJson
    }

    suspend fun getLongTermJson(): String = mutex.withLock {
        if (!initialized) init()
        longTermJson
    }

    suspend fun handleUserMessage(
        userText: String,
        strategyConfig: StrategyConfig
    ): AgentReply = mutex.withLock {
        if (!initialized) init()

        val trimmed = userText.trim()
        if (trimmed.isEmpty()) {
            return AgentReply("", TokenMetrics(0, 0, 0, null, null, null, null))
        }

        // memory commands (без LLM)
        parseMemoryCommand(trimmed)?.let { text ->
            return AgentReply(
                text = text,
                metrics = TokenMetrics(
                    estimatedUserTokens = tokenEstimator.estimateTokens(trimmed),
                    estimatedHistoryTokens = tokenEstimator.estimateTokens(shortTerm.history),
                    estimatedPromptTokens = 0,
                    actualPromptTokens = null,
                    actualCompletionTokens = null,
                    actualTotalTokens = null,
                    estimatedCostUsd = null
                )
            )
        }

        // short-term user append
        appendUserMessage(strategyConfig, trimmed)

        // facts strategy (facts остаются в short-term, это ок)
        if (strategyConfig is StrategyConfig.StickyFacts) {
            val updatedFacts = factsUpdater.updateFacts(shortTerm.factsJson, trimmed)
            shortTerm = shortTerm.copy(factsJson = updatedFacts)
        }
        shortTermStore.save(shortTerm)

        // build context plan
        val strategy = selector.select(strategyConfig)
        val plan = strategy.build(shortTerm, strategyConfig)

        // system prompt with layers
        val systemPrompt = buildSystemPromptWithMemoryLayers(
            base = systemPromptBase,
            longTerm = longTermJson,
            working = workingJson
        )

        val llmMessages = buildList {
            add(AgentMessage(AgentRole.SYSTEM, systemPrompt))
            addAll(plan.messagesForLlm)
        }

        // token estimate
        val estimatedPrompt = tokenEstimator.estimateTokens(llmMessages)
        val estimatedUser = tokenEstimator.estimateTokens(trimmed)
        val estimatedHistory = (estimatedPrompt - estimatedUser).coerceAtLeast(0)

        // call LLM
        val result = llmRepository.ask(llmMessages, maxOutputTokens = maxOutputTokens)
        val answer = result.text.trim()

        // short-term assistant append
        appendAssistantMessage(strategyConfig, answer)
        shortTermStore.save(shortTerm)

        val actualPrompt = result.usage?.promptTokens
        val actualCompletion = result.usage?.completionTokens
        val cost = if (actualPrompt != null && actualCompletion != null) {
            CostEstimator.estimateUsd(actualPrompt, actualCompletion)
        } else null

        AgentReply(
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

    private suspend fun parseMemoryCommand(text: String): String? {
        if (!text.startsWith("/")) return null
        val lower = text.lowercase()

        return when {
            lower.startsWith("/work ") -> {
                val kv = text.removePrefix("/work").trim()
                if (kv.isBlank()) return "⚠️ Usage: /work key=value"
                workingJson = mergeKeyValueIntoJson(workingJson, kv)
                workingStore.saveJson(workingJson)
                "✅ Saved to WORKING memory: $kv"
            }

            lower.startsWith("/profile ") -> {
                val kv = text.removePrefix("/profile").trim()
                if (kv.isBlank()) return "⚠️ Usage: /profile key=value"
                longTermJson = mergeKeyValueIntoJson(longTermJson, kv)
                longTermStore.saveJson(longTermJson)
                "✅ Saved to LONG-TERM profile: $kv"
            }

            lower.startsWith("/forget ") -> {
                when (text.removePrefix("/forget").trim().lowercase()) {
                    "work", "working" -> {
                        workingJson = ""
                        workingStore.clear()
                        "🧹 Cleared WORKING memory."
                    }
                    "profile", "long" -> {
                        longTermJson = ""
                        longTermStore.clear()
                        "🧹 Cleared LONG-TERM profile."
                    }
                    "short", "short-term", "chat" -> {
                        resetShortTermOnly()
                        "🧹 Cleared SHORT-TERM memory."
                    }
                    "all" -> {
                        resetAll()
                        "🧹 Cleared ALL memories (short/working/long)."
                    }
                    else -> "⚠️ Usage: /forget short | work | profile | all"
                }
            }

            lower == "/showmem" -> {
                val w = if (workingJson.isBlank()) "—" else workingJson
                val p = if (longTermJson.isBlank()) "—" else longTermJson
                val s = "history=${shortTerm.history.size}, facts=${if (shortTerm.factsJson.isBlank()) "—" else "yes"}"
                """
                🧠 Memory layers:
                • SHORT: $s
                • WORKING: $w
                • LONG-TERM: $p
                """.trimIndent()
            }

            else -> "⚠️ Unknown command. Try: /work, /profile, /forget, /showmem"
        }
    }

    private fun buildSystemPromptWithMemoryLayers(
        base: String,
        longTerm: String,
        working: String
    ): String {
        val lt = if (longTerm.isBlank()) "{}" else longTerm
        val wk = if (working.isBlank()) "{}" else working

        return """
            $base

            You have 3 memory layers:
            1) SHORT-TERM: current dialog messages (provided below).
            2) WORKING: current task state (JSON).
            3) LONG-TERM: user profile & stable preferences (JSON).

            Use WORKING for current task constraints/decisions.
            Use LONG-TERM only for stable preferences.
            If conflict: WORKING overrides LONG-TERM.

            [LONG_TERM_PROFILE_JSON]
            $lt

            [WORKING_MEMORY_JSON]
            $wk
        """.trimIndent()
    }

    private fun mergeKeyValueIntoJson(existingJson: String, kv: String): String {
        val idx = kv.indexOf('=')
        if (idx <= 0 || idx == kv.lastIndex) return existingJson
        val key = kv.substring(0, idx).trim()
        val value = kv.substring(idx + 1).trim()

        fun q(s: String) = buildString {
            append('"')
            s.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }

        val base = existingJson.trim()
        val map = LinkedHashMap<String, String>()

        val parsedOk = runCatching {
            if (base.startsWith("{") && base.endsWith("}")) {
                val inner = base.removePrefix("{").removeSuffix("}").trim()
                if (inner.isNotBlank()) {
                    inner.split(',').map { it.trim() }.forEach { pair ->
                        val p = pair.split(':', limit = 2)
                        if (p.size == 2) {
                            val k = p[0].trim().trim('"')
                            val v = p[1].trim().trim('"')
                            if (k.isNotBlank()) map[k] = v
                        }
                    }
                }
            }
        }.isSuccess

        if (!parsedOk && base.isNotBlank()) map.clear()
        map[key] = value

        return buildString {
            append("{")
            map.entries.forEachIndexed { i, e ->
                if (i > 0) append(", ")
                append(q(e.key)).append(": ").append(q(e.value))
            }
            append("}")
        }
    }

    // -------- Branching API (оставляю как ты делала раньше) --------

    suspend fun setCheckpointAtCurrent() = mutex.withLock {
        if (!initialized) init()
        val idx = shortTerm.history.count { it.role != AgentRole.SYSTEM }
        shortTerm = shortTerm.copy(branching = shortTerm.branching.copy(checkpointIndex = idx))
        shortTermStore.save(shortTerm)
    }

    suspend fun createBranch(branchId: String) = mutex.withLock {
        if (!initialized) init()
        val branches = shortTerm.branching.branches.toMutableMap()
        if (!branches.containsKey(branchId)) {
            branches[branchId] = com.example.aichalengeapp.agent.context.BranchData(messages = emptyList())
        }
        shortTerm = shortTerm.copy(
            branching = shortTerm.branching.copy(
                branches = branches,
                activeBranchId = branchId
            )
        )
        shortTermStore.save(shortTerm)
    }

    suspend fun switchBranch(branchId: String) = mutex.withLock {
        if (!initialized) init()
        if (!shortTerm.branching.branches.containsKey(branchId)) return@withLock
        shortTerm = shortTerm.copy(branching = shortTerm.branching.copy(activeBranchId = branchId))
        shortTermStore.save(shortTerm)
    }

    suspend fun getBranches(): List<String> = mutex.withLock {
        if (!initialized) init()
        shortTerm.branching.branches.keys.sorted()
    }

    suspend fun getActiveBranchId(): String? = mutex.withLock {
        if (!initialized) init()
        shortTerm.branching.activeBranchId
    }

    private fun appendUserMessage(config: StrategyConfig, text: String) {
        val msg = AgentMessage(AgentRole.USER, text)
        when (config) {
            is StrategyConfig.Branching -> {
                val id = config.branchId
                val branches = shortTerm.branching.branches.toMutableMap()
                val existing = branches[id]?.messages.orEmpty()
                branches[id] = com.example.aichalengeapp.agent.context.BranchData(messages = existing + msg)
                shortTerm = shortTerm.copy(branching = shortTerm.branching.copy(branches = branches, activeBranchId = id))
            }
            else -> shortTerm = shortTerm.copy(history = shortTerm.history + msg)
        }
    }

    private fun appendAssistantMessage(config: StrategyConfig, text: String) {
        val msg = AgentMessage(AgentRole.ASSISTANT, text)
        when (config) {
            is StrategyConfig.Branching -> {
                val id = config.branchId
                val branches = shortTerm.branching.branches.toMutableMap()
                val existing = branches[id]?.messages.orEmpty()
                branches[id] = com.example.aichalengeapp.agent.context.BranchData(messages = existing + msg)
                shortTerm = shortTerm.copy(branching = shortTerm.branching.copy(branches = branches, activeBranchId = id))
            }
            else -> shortTerm = shortTerm.copy(history = shortTerm.history + msg)
        }
    }
}
