package com.example.aichalengeapp.agent

import com.example.aichalengeapp.agent.context.AgentMemoryState
import com.example.aichalengeapp.agent.context.ContextStrategySelector
import com.example.aichalengeapp.agent.context.StrategyConfig
import com.example.aichalengeapp.agent.facts.FactsUpdater
import com.example.aichalengeapp.agent.invariants.InvariantGuard
import com.example.aichalengeapp.agent.invariants.InvariantsProfile
import com.example.aichalengeapp.agent.invariants.InvariantsStore
import com.example.aichalengeapp.agent.memory.AgentMemoryStore
import com.example.aichalengeapp.agent.memory.LongTermMemoryStore
import com.example.aichalengeapp.agent.memory.WorkingMemoryStore
import com.example.aichalengeapp.agent.profile.UserProfile
import com.example.aichalengeapp.agent.profile.UserProfileStore
import com.example.aichalengeapp.agent.task.TaskManager
import com.example.aichalengeapp.agent.task.TaskState
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.repo.ChatRepository
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@ViewModelScoped
class ChatAgent @Inject constructor(
    private val llmRepository: ChatRepository,
    private val shortTermStore: AgentMemoryStore,
    private val workingStore: WorkingMemoryStore,
    private val longTermStore: LongTermMemoryStore,
    private val userProfileStore: UserProfileStore,
    private val invariantsStore: InvariantsStore,
    private val selector: ContextStrategySelector,
    private val tokenEstimator: TokenEstimator,
    private val factsUpdater: FactsUpdater,
    private val promptComposer: PromptComposer,
    private val taskManager: TaskManager,
    private val invariantGuard: InvariantGuard
) {
    private val mutex = Mutex()

    private var shortTerm: AgentMemoryState = AgentMemoryState()
    private var workingJson: String = ""
    private var longTermJson: String = ""

    private var userProfile: UserProfile = UserProfile()
    private var invariants: InvariantsProfile = InvariantsProfile()
    private var guardEnabled: Boolean = false

    private var initialized = false

    private val systemPromptBase = "You are a helpful assistant."
    private val maxOutputTokens = 512

    suspend fun init() = mutex.withLock {
        if (initialized) return@withLock
        shortTerm = shortTermStore.load()
        workingJson = workingStore.loadJson()
        longTermJson = longTermStore.loadJson()
        userProfile = userProfileStore.load()
        invariants = invariantsStore.loadProfile()
        guardEnabled = invariantsStore.loadGuardEnabled()
        initialized = true
    }

    suspend fun resetAll() = mutex.withLock {
        if (!initialized) init()
        shortTerm = AgentMemoryState()
        workingJson = ""
        longTermJson = ""
        userProfile = UserProfile()
        invariants = InvariantsProfile()
        guardEnabled = false
        shortTermStore.clear()
        workingStore.clear()
        longTermStore.clear()
        userProfileStore.clear()
        invariantsStore.clearProfile()
        invariantsStore.saveGuardEnabled(false)
    }

    suspend fun getUserProfile(): UserProfile = mutex.withLock {
        if (!initialized) init()
        userProfile = userProfileStore.load()
        userProfile
    }

    suspend fun saveUserProfile(profile: UserProfile) = mutex.withLock {
        if (!initialized) init()
        userProfile = profile
        userProfileStore.save(profile)
        userProfile = userProfileStore.load()
    }

    suspend fun clearUserProfile() = mutex.withLock {
        if (!initialized) init()
        userProfile = UserProfile()
        userProfileStore.clear()
    }

    suspend fun getInvariants(): InvariantsProfile = mutex.withLock {
        if (!initialized) init()
        invariants = invariantsStore.loadProfile()
        invariants
    }

    suspend fun saveInvariants(profile: InvariantsProfile) = mutex.withLock {
        if (!initialized) init()
        invariants = profile
        invariantsStore.saveProfile(profile)
    }

    suspend fun clearInvariants() = mutex.withLock {
        if (!initialized) init()
        invariants = InvariantsProfile()
        invariantsStore.clearProfile()
    }

    suspend fun isGuardEnabled(): Boolean = mutex.withLock {
        if (!initialized) init()
        guardEnabled = invariantsStore.loadGuardEnabled()
        guardEnabled
    }

    suspend fun setGuardEnabled(enabled: Boolean) = mutex.withLock {
        if (!initialized) init()
        guardEnabled = enabled
        invariantsStore.saveGuardEnabled(enabled)
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

    suspend fun getTaskState(): TaskState? = mutex.withLock {
        if (!initialized) init()
        taskManager.getTaskState()
    }

    suspend fun startTask(goal: String): TaskState = mutex.withLock {
        if (!initialized) init()
        val started = taskManager.startTask(goal)
        workingJson = workingStore.loadJson()
        started
    }

    suspend fun nextTaskStep(): TaskState? = mutex.withLock {
        if (!initialized) init()
        val next = taskManager.nextTaskStep()
        workingJson = workingStore.loadJson()
        next
    }

    suspend fun pauseTask(): TaskState? = mutex.withLock {
        if (!initialized) init()
        val paused = taskManager.pauseTask()
        workingJson = workingStore.loadJson()
        paused
    }

    suspend fun resumeTask(): TaskState? = mutex.withLock {
        if (!initialized) init()
        val resumed = taskManager.resumeTask()
        workingJson = workingStore.loadJson()
        resumed
    }

    suspend fun stopTask() = mutex.withLock {
        if (!initialized) init()
        taskManager.stopTask()
        workingJson = workingStore.loadJson()
    }

    suspend fun handleUserMessage(
        userText: String,
        strategyConfig: StrategyConfig
    ): AgentReply = handleUserMessageInternal(
        userText = userText,
        strategyConfig = strategyConfig,
        allowTaskAutoProgress = true
    )

    suspend fun handleTaskBootstrapMessage(
        userText: String,
        strategyConfig: StrategyConfig
    ): AgentReply = handleUserMessageInternal(
        userText = userText,
        strategyConfig = strategyConfig,
        allowTaskAutoProgress = false
    )

    private suspend fun handleUserMessageInternal(
        userText: String,
        strategyConfig: StrategyConfig,
        allowTaskAutoProgress: Boolean
    ): AgentReply = mutex.withLock {
        if (!initialized) init()

        val trimmed = userText.trim()
        if (trimmed.isEmpty()) {
            return AgentReply("", TokenMetrics(0, 0, 0, null, null, null, null))
        }

        val taskState = taskManager.getTaskState()
        if (taskState?.paused == true) {
            return AgentReply(
                text = "⏸️ Task mode is paused. Tap Resume to continue task progression.",
                metrics = TokenMetrics(0, 0, 0, null, null, null, null),
                debugLabel = "task-paused"
            )
        }

        userProfile = userProfileStore.load()

        appendUserMessage(strategyConfig, trimmed)
        if (strategyConfig is StrategyConfig.StickyFacts) {
            val updatedFacts = factsUpdater.updateFacts(shortTerm.factsJson, trimmed)
            shortTerm = shortTerm.copy(factsJson = updatedFacts)
        }
        shortTermStore.save(shortTerm)

        val strategy = selector.select(strategyConfig)
        val plan = strategy.build(shortTerm, strategyConfig)

        val profileDirective = promptComposer.buildProfileDirective(userProfile)
        val invariantGuardDirective = if (guardEnabled) {
            promptComposer.buildInvariantGuardDirective(invariants)
        } else {
            ""
        }

        val systemPrompt = promptComposer.buildSystemPromptWithMemoryLayers(
            base = systemPromptBase,
            profileDirective = profileDirective,
            longTerm = longTermJson,
            working = workingJson,
            invariantGuardDirective = invariantGuardDirective
        )

        val llmMessages = buildList {
            add(AgentMessage(AgentRole.SYSTEM, systemPrompt))
            add(AgentMessage(AgentRole.USER, profileDirective))
            addAll(plan.messagesForLlm)
        }

        val estimatedPrompt = tokenEstimator.estimateTokens(llmMessages)
        val estimatedUser = tokenEstimator.estimateTokens(trimmed)
        val estimatedHistory = (estimatedPrompt - estimatedUser).coerceAtLeast(0)

        val result = llmRepository.ask(llmMessages, maxOutputTokens = maxOutputTokens)
        val rawAnswer = result.text.trim()
        val answer = when (val guardResult = invariantGuard.check(rawAnswer, invariants, guardEnabled)) {
            InvariantGuard.GuardResult.Ok -> rawAnswer
            is InvariantGuard.GuardResult.Violation -> buildInvariantViolationResponse(guardResult)
        }

        appendAssistantMessage(strategyConfig, answer)
        shortTermStore.save(shortTerm)

        if (allowTaskAutoProgress && taskState != null && taskState.stage != TaskStage.DONE) {
            taskManager.nextTaskStep()
            workingJson = workingStore.loadJson()
        }

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

    private fun buildInvariantViolationResponse(violation: InvariantGuard.GuardResult.Violation): String {
        return """
            🚫 This request conflicts with Invariant Guard rules.

            Rule:
            ${violation.invariant}

            ${violation.explanation}

            Instead I can suggest:
            • improving ViewModel separation
            • refactoring repositories and use-cases inside current architecture
            • strengthening module boundaries without breaking invariants
        """.trimIndent()
    }

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
                shortTerm = shortTerm.copy(
                    branching = shortTerm.branching.copy(
                        branches = branches,
                        activeBranchId = id
                    )
                )
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
                shortTerm = shortTerm.copy(
                    branching = shortTerm.branching.copy(
                        branches = branches,
                        activeBranchId = id
                    )
                )
            }
            else -> shortTerm = shortTerm.copy(history = shortTerm.history + msg)
        }
    }
}
