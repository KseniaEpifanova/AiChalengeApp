package com.example.aichalengeapp.agent

import com.example.aichalengeapp.agent.context.AgentMemoryState
import com.example.aichalengeapp.agent.context.ContextStrategySelector
import com.example.aichalengeapp.agent.context.StrategyConfig
import com.example.aichalengeapp.agent.facts.FactsUpdater
import com.example.aichalengeapp.agent.guard.GuardResult
import com.example.aichalengeapp.agent.guard.InvariantGuard
import com.example.aichalengeapp.agent.guard.InvariantsProfile
import com.example.aichalengeapp.agent.guard.InvariantsStore
import com.example.aichalengeapp.agent.memory.AgentMemoryStore
import com.example.aichalengeapp.agent.memory.LongTermMemoryStore
import com.example.aichalengeapp.agent.memory.WorkingMemoryStore
import com.example.aichalengeapp.agent.orchestrator.AgentOrchestrator
import com.example.aichalengeapp.agent.orchestrator.ProfileResolver
import com.example.aichalengeapp.agent.orchestrator.RequestKind
import com.example.aichalengeapp.agent.orchestrator.TaskChatIntent
import com.example.aichalengeapp.agent.orchestrator.TaskConflictDetector
import com.example.aichalengeapp.agent.orchestrator.TaskIntentDetector
import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.profile.AssistantProfilesStore
import com.example.aichalengeapp.agent.profile.ResponseProfile
import com.example.aichalengeapp.agent.profile.UserProfile
import com.example.aichalengeapp.agent.profile.UserProfileStore
import com.example.aichalengeapp.agent.task.TaskManager
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import com.example.aichalengeapp.agent.task.TaskTransitionResult
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.debug.TaskTrace
import com.example.aichalengeapp.mcp.currency.CurrencyToolResponse
import com.example.aichalengeapp.mcp.currency.CurrencyToolRouter
import com.example.aichalengeapp.mcp.currency.McpCurrencyService
import com.example.aichalengeapp.repo.ChatRepository
import android.util.Log
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject

@ViewModelScoped
class ChatAgent @Inject constructor(
    private val llmRepository: ChatRepository,
    private val shortTermStore: AgentMemoryStore,
    private val workingStore: WorkingMemoryStore,
    private val longTermStore: LongTermMemoryStore,
    private val userProfileStore: UserProfileStore,
    private val profilesStore: AssistantProfilesStore,
    private val invariantsStore: InvariantsStore,
    private val selector: ContextStrategySelector,
    private val tokenEstimator: TokenEstimator,
    private val factsUpdater: FactsUpdater,
    private val taskManager: TaskManager,
    private val invariantGuard: InvariantGuard,
    private val profileResolver: ProfileResolver,
    private val orchestrator: AgentOrchestrator,
    private val taskConflictDetector: TaskConflictDetector,
    private val taskIntentDetector: TaskIntentDetector,
    private val currencyToolRouter: CurrencyToolRouter,
    private val mcpCurrencyService: McpCurrencyService
) {
    private companion object {
        private const val TAG = "TaskClassifier"
    }

    private val mutex = Mutex()

    private var shortTerm: AgentMemoryState = AgentMemoryState()
    private var workingJson: String = ""
    private var longTermJson: String = ""

    private var profiles: List<AssistantProfile> = listOf(AssistantProfile.default())
    private var activeProfileId: String = AssistantProfile.DEFAULT_ID

    private var invariants: InvariantsProfile = InvariantsProfile()

    private var initialized = false

    private val systemPromptBase = "You are a helpful assistant."
    private val maxOutputTokens = 1536
    suspend fun init() = mutex.withLock {
        if (initialized) return@withLock

        shortTerm = shortTermStore.load()
        workingJson = workingStore.loadJson()
        longTermJson = longTermStore.loadJson()

        profiles = profilesStore.loadProfiles().ifEmpty { listOf(AssistantProfile.default()) }
        profiles = ensureDeveloperProfileDefaults(profiles)
        profilesStore.saveProfiles(profiles)
        activeProfileId = profilesStore.loadActiveProfileId()
            ?: profileResolver.resolve(profiles, null).id

        val legacyProfile = userProfileStore.load()
        if (!legacyProfile.isEmpty()) {
            migrateLegacyUserProfileIfNeeded(legacyProfile)
        }

        invariants = invariantsStore.loadProfile()

        initialized = true
    }

    suspend fun resetAll() = mutex.withLock {
        if (!initialized) init()

        shortTerm = AgentMemoryState()
        workingJson = ""
        longTermJson = ""

        profiles = listOf(AssistantProfile.default())
        activeProfileId = AssistantProfile.DEFAULT_ID

        invariants = InvariantsProfile()

        shortTermStore.clear()
        workingStore.clear()
        longTermStore.clear()

        profilesStore.saveProfiles(profiles)
        profilesStore.saveActiveProfileId(activeProfileId)
        userProfileStore.clear()

        invariantsStore.clearProfile()

        taskManager.stopTask()
    }

    suspend fun clearChatSession() = mutex.withLock {
        if (!initialized) init()
        shortTerm = AgentMemoryState()
        shortTermStore.clear()
        taskManager.stopTask()
        workingJson = workingStore.loadJson()
    }

    suspend fun getProfiles(): List<AssistantProfile> = mutex.withLock {
        if (!initialized) init()
        profiles = profilesStore.loadProfiles().ifEmpty { listOf(AssistantProfile.default()) }
        profiles
    }

    suspend fun getActiveProfile(): AssistantProfile = mutex.withLock {
        if (!initialized) init()
        profileResolver.resolve(profiles, activeProfileId)
    }

    suspend fun saveProfiles(updated: List<AssistantProfile>) = mutex.withLock {
        if (!initialized) init()
        profiles = updated.ifEmpty { listOf(AssistantProfile.default()) }
        profilesStore.saveProfiles(profiles)
        val resolved = profileResolver.resolve(profiles, activeProfileId)
        activeProfileId = resolved.id
        profilesStore.saveActiveProfileId(activeProfileId)
    }

    suspend fun setActiveProfile(profileId: String) = mutex.withLock {
        if (!initialized) init()
        val exists = profiles.any { it.id == profileId }
        if (!exists) return@withLock
        activeProfileId = profileId
        profilesStore.saveActiveProfileId(profileId)
    }

    suspend fun clearProfilesToDefault() = mutex.withLock {
        if (!initialized) init()
        profiles = listOf(AssistantProfile.default())
        activeProfileId = AssistantProfile.DEFAULT_ID
        profilesStore.saveProfiles(profiles)
        profilesStore.saveActiveProfileId(activeProfileId)
    }

    suspend fun getUserProfile(): UserProfile = mutex.withLock {
        if (!initialized) init()
        val active = profileResolver.resolve(profiles, activeProfileId)
        active.responseProfile.toUserProfile()
    }

    suspend fun saveUserProfile(profile: UserProfile) = mutex.withLock {
        if (!initialized) init()
        val active = profileResolver.resolve(profiles, activeProfileId)
        profiles = profiles.map {
            if (it.id == active.id) it.copy(responseProfile = profile.toResponseProfile()) else it
        }
        profilesStore.saveProfiles(profiles)
        userProfileStore.save(profile)
    }

    suspend fun clearUserProfile() = mutex.withLock {
        if (!initialized) init()
        saveUserProfile(UserProfile())
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

    suspend fun startTaskMode(request: String): TaskState = startTask(request)

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

    suspend fun cancelTask(): TaskState? = mutex.withLock {
        if (!initialized) init()
        val cancelled = taskManager.cancelTask()
        workingJson = workingStore.loadJson()
        cancelled
    }

    suspend fun approveTaskPlan(): TaskState? = mutex.withLock {
        if (!initialized) init()
        val approved = taskManager.approvePlan()
        workingJson = workingStore.loadJson()
        approved
    }

    suspend fun attemptTransition(toStage: TaskStage): TaskTransitionResult = mutex.withLock {
        if (!initialized) init()
        val result = taskManager.attemptTransition(toStage)
        workingJson = workingStore.loadJson()
        result
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
        userText = "Let's start by planning this task: ${userText.trim()}",
        strategyConfig = strategyConfig,
        allowTaskAutoProgress = false,
        forcedIntent = TaskChatIntent.START_COMPLEX_TASK,
        transitionSource = "bootstrap"
    )

    suspend fun handleTaskIntentAction(
        intent: TaskChatIntent,
        strategyConfig: StrategyConfig
    ): AgentReply = handleUserMessageInternal(
        userText = "Task action: $intent",
        strategyConfig = strategyConfig,
        allowTaskAutoProgress = false,
        forcedIntent = intent,
        transitionSource = "button"
    )

    private suspend fun handleUserMessageInternal(
        userText: String,
        strategyConfig: StrategyConfig,
        allowTaskAutoProgress: Boolean,
        forcedIntent: TaskChatIntent? = null,
        transitionSource: String = "chat"
    ): AgentReply = mutex.withLock {
        if (!initialized) init()

        val trimmed = userText.trim()
        if (trimmed.isEmpty()) {
            return AgentReply("", TokenMetrics(0, 0, 0, null, null, null, null))
        }

        if (forcedIntent == null && transitionSource == "chat") {
            val currencyIntent = currencyToolRouter.route(trimmed)
            if (currencyIntent != null) {
                appendUserMessage(strategyConfig, trimmed)
                if (strategyConfig is StrategyConfig.StickyFacts) {
                    val updatedFacts = factsUpdater.updateFacts(shortTerm.factsJson, trimmed)
                    shortTerm = shortTerm.copy(factsJson = updatedFacts)
                }
                shortTermStore.save(shortTerm)

                val currencyResponse = mcpCurrencyService.getExchangeRate(
                    base = currencyIntent.base,
                    target = currencyIntent.target,
                    amount = currencyIntent.amount
                )
                val text = formatCurrencyReply(currencyResponse)
                appendAssistantMessage(strategyConfig, text)
                shortTermStore.save(shortTerm)
                return AgentReply(
                    text = text,
                    metrics = TokenMetrics(0, 0, 0, null, null, null, null),
                    debugLabel = "mcp-currency"
                )
            }
        }

        var taskState = taskManager.getTaskState()
        val stageAtTurnStart = taskState?.stage
        val hadActiveTaskAtTurnStart = taskState != null
        TaskTrace.d(
            "event" to "incoming_message",
            "source" to transitionSource,
            "taskId" to TaskTrace.taskId(taskState),
            "msg" to trimmed,
            "beforeStage" to taskState?.stage,
            "beforeApproved" to taskState?.planApproved,
            "paused" to taskState?.paused
        )
        if (taskState?.paused == true) {
            TaskTrace.d(
                "event" to "transition_blocked_paused",
                "source" to transitionSource,
                "taskId" to TaskTrace.taskId(taskState),
                "beforeStage" to taskState.stage
            )
            return AgentReply(
                text = "⏸️ Task lifecycle is paused. Resume or cancel to continue.",
                metrics = TokenMetrics(0, 0, 0, null, null, null, null),
                debugLabel = "task-paused"
            )
        }

        val guardActive = isInvariantGuardActive()

        val context = orchestrator.buildExecutionContext(
            profiles = profiles,
            activeProfileId = activeProfileId,
            invariants = invariants,
            taskState = taskState,
            strategyConfig = strategyConfig,
            message = trimmed,
            longTermJson = longTermJson,
            workingJson = workingJson
        )

        Log.d(
            TAG,
            "profileId=${context.activeProfile.id} profileName=${context.activeProfile.name} " +
                "autoDetect=${context.activeProfile.planningProfile.autoDetectComplexity} " +
                "sensitivity=${context.activeProfile.planningProfile.complexitySensitivity} " +
                "result=${context.requestKind} reason=${context.classificationReason}"
        )

        val intentDecision = if (forcedIntent != null) {
            TaskIntentDetector.IntentDecision(forcedIntent, forcedIntent.name)
        } else {
            taskIntentDetector.detect(
                message = trimmed,
                activeProfile = context.activeProfile,
                taskState = taskState,
                source = transitionSource
            )
        }
        val detectedIntent = intentDecision.intent
        Log.d(
            "TaskIntentDetector",
            "message=\"$trimmed\" intent=$detectedIntent raw=\"${intentDecision.rawLabel}\" profile=${context.activeProfile.name} activeStage=${taskState?.stage}"
        )
        val normalizedIntent = normalizeIntentForStage(
            detectedIntent = detectedIntent,
            currentStage = taskState?.stage
        )
        TaskTrace.d(
            "event" to "intent_normalized",
            "source" to transitionSource,
            "taskId" to TaskTrace.taskId(taskState),
            "detectedIntent" to detectedIntent,
            "normalizedIntent" to normalizedIntent,
            "beforeStage" to taskState?.stage
        )
        Log.d(
            "TaskIntent",
            "message=\"$trimmed\" currentStage=${taskState?.stage} detected=$detectedIntent normalized=$normalizedIntent source=$transitionSource"
        )

        val isLifecycleActionIntent = normalizedIntent == TaskChatIntent.APPROVE_PLAN ||
            normalizedIntent == TaskChatIntent.CONTINUE_TASK ||
            normalizedIntent == TaskChatIntent.REQUEST_VALIDATION ||
            normalizedIntent == TaskChatIntent.FINISH_TASK ||
            normalizedIntent == TaskChatIntent.CANCEL_TASK

        var taskStartedThisTurn = false

        if (
            taskState != null &&
            isActiveTask(taskState) &&
            taskConflictDetector.shouldBlockConcurrentStart(
                activeTask = taskState,
                incomingMessage = trimmed,
                requestKind = context.requestKind,
                hasTransitionIntent = isLifecycleActionIntent
            )
        ) {
            appendUserMessage(strategyConfig, trimmed)
            shortTermStore.save(shortTerm)
            return AgentReply(
                text = "⚠️ A task is already in progress: \"${taskState.goal}\". Finish or cancel it before starting a new one.",
                metrics = TokenMetrics(0, 0, 0, null, null, null, null),
                debugLabel = "task-conflict"
            )
        }

        if (taskState == null && (normalizedIntent == TaskChatIntent.START_COMPLEX_TASK || context.requestKind == RequestKind.COMPLEX)) {
            taskState = taskManager.startTask(trimmed)
            taskStartedThisTurn = true
            workingJson = workingStore.loadJson()
            TaskTrace.d(
                "event" to "task_auto_started",
                "source" to transitionSource,
                "taskId" to TaskTrace.taskId(taskState),
                "msg" to trimmed,
                "intent" to normalizedIntent,
                "afterStage" to taskState?.stage,
                "persisted" to true
            )
            Log.d(TAG, "taskAutoStarted=true stage=${taskState?.stage} goal=\"${taskState?.goal}\"")
        }

        if (taskState == null && isLifecycleActionIntent) {
            return AgentReply(
                text = "⚠️ No active task. Start a task first.",
                metrics = TokenMetrics(0, 0, 0, null, null, null, null),
                debugLabel = "task-no-active"
            )
        }

        if (taskState != null && isLifecycleActionIntent && taskStartedThisTurn) {
            Log.d(
                "TaskTransition",
                "source=$transitionSource skipLifecycleAction=true reason=task_started_this_turn intent=$normalizedIntent stage=${taskState.stage}"
            )
        } else if (taskState != null && isLifecycleActionIntent) {
            val actionResult = applyTaskIntentAction(taskState, normalizedIntent, transitionSource)
            workingJson = workingStore.loadJson()
            if (actionResult is IntentActionResult.Invalid) {
                TaskTrace.d(
                    "event" to "intent_action_invalid",
                    "source" to transitionSource,
                    "taskId" to TaskTrace.taskId(taskState),
                    "intent" to normalizedIntent,
                    "beforeStage" to taskState.stage,
                    "reason" to actionResult.reason,
                    "suggestedNextAction" to actionResult.nextAction
                )
                return AgentReply(
                    text = "🚫 ${actionResult.reason}. Next valid step: ${actionResult.nextAction}.",
                    metrics = TokenMetrics(0, 0, 0, null, null, null, null),
                    debugLabel = "task-transition-invalid"
                )
            }
            taskState = taskManager.getTaskState()
            TaskTrace.d(
                "event" to "intent_action_persisted",
                "source" to transitionSource,
                "taskId" to TaskTrace.taskId(taskState),
                "intent" to normalizedIntent,
                "afterStage" to taskState?.stage,
                "afterApproved" to taskState?.planApproved,
                "persisted" to true
            )
            Log.d("TaskTransition", "source=$transitionSource persistedStage=${taskState?.stage}")
        }

        if (!hadActiveTaskAtTurnStart && taskStartedThisTurn) {
            Log.d("TaskTransition", "source=$transitionSource newTaskBaselineStage=${taskState?.stage}")
        }

        appendUserMessage(strategyConfig, trimmed)
        if (strategyConfig is StrategyConfig.StickyFacts) {
            val updatedFacts = factsUpdater.updateFacts(shortTerm.factsJson, trimmed)
            shortTerm = shortTerm.copy(factsJson = updatedFacts)
        }
        shortTermStore.save(shortTerm)

        val strategy = selector.select(strategyConfig)
        val plan = strategy.build(shortTerm, strategyConfig)

        val persistedTaskState = taskManager.getTaskState()
        val responseTaskState = persistedTaskState ?: taskState
        val stageChangedNotice = when {
            responseTaskState == null -> null
            stageAtTurnStart == responseTaskState.stage -> null
            else -> "The task stage has changed to ${responseTaskState.stage}. " +
                "You must strictly follow the stage contract for ${responseTaskState.stage}."
        }
        TaskTrace.d(
            "event" to "response_stage_context",
            "source" to transitionSource,
            "taskId" to TaskTrace.taskId(responseTaskState),
            "responseStageContext" to responseTaskState?.stage,
            "responseApproved" to responseTaskState?.planApproved,
            "persistedStage" to persistedTaskState?.stage,
            "stageChanged" to (stageChangedNotice != null)
        )

        val refreshedContext = orchestrator.buildExecutionContext(
            profiles = profiles,
            activeProfileId = activeProfileId,
            invariants = invariants,
            taskState = responseTaskState,
            strategyConfig = strategyConfig,
            message = trimmed,
            longTermJson = longTermJson,
            workingJson = workingJson
        )

        Log.d(
            "PromptBuilder",
            "profile=${refreshedContext.activeProfile.name} style=${refreshedContext.activeProfile.responseProfile.style.ifBlank { "-" }} " +
                "constraints=${refreshedContext.activeProfile.responseProfile.constraints.ifBlank { "-" }} " +
                "guardActive=$guardActive stage=${refreshedContext.taskState?.stage}"
        )

        val systemPrompt = orchestrator.buildSystemPrompt(systemPromptBase, refreshedContext)
        TaskTrace.d(
            "event" to "llm_prompt_context",
            "source" to transitionSource,
            "taskId" to TaskTrace.taskId(refreshedContext.taskState),
            "stageForGeneration" to refreshedContext.taskState?.stage,
            "guardActive" to guardActive
        )

        val llmMessages = buildList {
            add(AgentMessage(AgentRole.SYSTEM, systemPrompt))
            if (stageChangedNotice != null) {
                add(AgentMessage(AgentRole.SYSTEM, stageChangedNotice))
            }
            addAll(plan.messagesForLlm)
        }
        if (stageChangedNotice != null) {
            TaskTrace.d(
                "event" to "stage_change_notice_injected",
                "source" to transitionSource,
                "taskId" to TaskTrace.taskId(responseTaskState),
                "beforeStage" to stageAtTurnStart,
                "afterStage" to responseTaskState?.stage,
                "notice" to stageChangedNotice
            )
        }

        val estimatedPrompt = tokenEstimator.estimateTokens(llmMessages)
        val estimatedUser = tokenEstimator.estimateTokens(trimmed)
        val estimatedHistory = (estimatedPrompt - estimatedUser).coerceAtLeast(0)

        val result = llmRepository.ask(llmMessages, maxOutputTokens = maxOutputTokens)
        val rawAnswer = result.text.trim()
        TaskTrace.d(
            "event" to "llm_response",
            "source" to transitionSource,
            "taskId" to TaskTrace.taskId(taskState),
            "stageForResponse" to refreshedContext.taskState?.stage,
            "responsePreview" to TaskTrace.preview(rawAnswer),
            "responseLength" to rawAnswer.length
        )

        val answer = when (val guardResult = invariantGuard.check(rawAnswer, if (guardActive) invariants else InvariantsProfile())) {
            GuardResult.Ok -> rawAnswer
            is GuardResult.Violation -> buildInvariantViolationResponse(guardResult)
        }
        appendAssistantMessage(strategyConfig, answer)
        shortTermStore.save(shortTerm)

        val activeProfile = profileResolver.resolve(profiles, activeProfileId)
        if (allowTaskAutoProgress && activeProfile.planningProfile.allowAutoContinueExecution) {
            val liveTask = taskManager.getTaskState()
            if (liveTask != null && !liveTask.paused && liveTask.stage != TaskStage.DONE && liveTask.stage != TaskStage.CANCELLED) {
                taskManager.nextTaskStep()
                workingJson = workingStore.loadJson()
                val progressed = taskManager.getTaskState()
                TaskTrace.d(
                    "event" to "auto_progress_applied",
                    "source" to transitionSource,
                    "taskId" to TaskTrace.taskId(progressed),
                    "afterStage" to progressed?.stage,
                    "persisted" to true
                )
            }
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

    private suspend fun migrateLegacyUserProfileIfNeeded(legacy: UserProfile) {
        val active = profileResolver.resolve(profiles, activeProfileId)
        val activeResponse = active.responseProfile
        if (activeResponse.style.isNotBlank() || activeResponse.format.isNotBlank() || activeResponse.constraints.isNotBlank()) {
            return
        }

        profiles = profiles.map {
            if (it.id == active.id) it.copy(responseProfile = legacy.toResponseProfile()) else it
        }
        profilesStore.saveProfiles(profiles)
    }

    private fun ensureDeveloperProfileDefaults(source: List<AssistantProfile>): List<AssistantProfile> {
        val developerTemplate = AssistantProfile.mobileDeveloper()
        var updated = source
        val existingIndex = updated.indexOfFirst {
            it.name.equals("Developer", ignoreCase = true) ||
                it.name.equals("Mobile Developer", ignoreCase = true) ||
                it.id == "developer" ||
                it.id == developerTemplate.id
        }
        updated = if (existingIndex >= 0) {
            updated.mapIndexed { index, profile ->
                if (index == existingIndex) {
                    profile.copy(
                        id = developerTemplate.id,
                        name = developerTemplate.name,
                        planningProfile = developerTemplate.planningProfile
                    )
                } else {
                    profile
                }
            }
        } else {
            updated + developerTemplate
        }
        return updated
    }

    private fun buildInvariantViolationResponse(violation: GuardResult.Violation): String {
        return """
            🚫 This request conflicts with Invariant Guard rules.

            Rule:
            ${violation.invariant}

            ${violation.explanation}

            Instead I can suggest:
            • a compliant option using current technical decisions
            • a safe alternative without violating business rules
            • an incremental path that keeps architecture constraints
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

    private fun UserProfile.toResponseProfile(): ResponseProfile {
        return ResponseProfile(style = style, format = format, constraints = constraints)
    }

    private fun ResponseProfile.toUserProfile(): UserProfile {
        return UserProfile(style = style, format = format, constraints = constraints)
    }

    private sealed class IntentActionResult {
        data class Success(val state: TaskState?) : IntentActionResult()
        data class Invalid(val reason: String, val nextAction: String) : IntentActionResult()
    }

    private suspend fun applyTaskIntentAction(
        state: TaskState,
        intent: TaskChatIntent,
        source: String
    ): IntentActionResult {
        TaskTrace.d(
            "event" to "task_action_requested",
            "source" to source,
            "taskId" to TaskTrace.taskId(state),
            "intent" to intent,
            "beforeStage" to state.stage,
            "beforeApproved" to state.planApproved,
            "paused" to state.paused
        )
        Log.d("TaskTransition", "source=$source currentStage=${state.stage} intent=$intent")
        if (state.stage == TaskStage.DONE) {
            return IntentActionResult.Invalid(
                reason = "Task is already completed",
                nextAction = "Start a new task if you need more work"
            )
        }
        if (state.stage == TaskStage.CANCELLED) {
            return IntentActionResult.Invalid(
                reason = "Task is cancelled",
                nextAction = "Start a new task to continue"
            )
        }
        return when (intent) {
            TaskChatIntent.APPROVE_PLAN -> {
                if (state.stage != TaskStage.PLANNING && state.stage != TaskStage.PLAN_REVIEW) {
                    return IntentActionResult.Invalid(
                        reason = "Plan approval is only valid in planning stage",
                        nextAction = "Continue current stage workflow"
                    )
                }
                taskManager.approvePlan()
                val result = taskManager.attemptTransition(TaskStage.EXECUTION)
                when (result) {
                    is TaskTransitionResult.Success -> {
                        TaskTrace.d(
                            "event" to "task_action_result",
                            "source" to source,
                            "taskId" to TaskTrace.taskId(result.newState),
                            "intent" to intent,
                            "fsmResult" to "SUCCESS",
                            "afterStage" to result.newState.stage,
                            "persisted" to true
                        )
                        Log.d("TaskTransition", "source=$source result=SUCCESS newStage=${result.newState.stage}")
                        IntentActionResult.Success(result.newState)
                    }
                    is TaskTransitionResult.Invalid -> {
                        TaskTrace.d(
                            "event" to "task_action_result",
                            "source" to source,
                            "taskId" to TaskTrace.taskId(state),
                            "intent" to intent,
                            "fsmResult" to "INVALID",
                            "reason" to result.reason,
                            "suggestedNextAction" to result.suggestedNextAction,
                            "afterStage" to state.stage,
                            "persisted" to false
                        )
                        Log.d("TaskTransition", "source=$source result=INVALID reason=${result.reason}")
                        IntentActionResult.Invalid(result.reason, result.suggestedNextAction)
                    }
                }
            }
            TaskChatIntent.CONTINUE_TASK -> {
                if (state.stage == TaskStage.PLANNING && !state.planApproved) {
                    IntentActionResult.Invalid(
                        reason = "Plan is not approved",
                        nextAction = "Confirm plan approval before execution"
                    )
                } else {
                    val next = taskManager.nextTaskStep()
                    val afterStage = next?.stage ?: state.stage
                    TaskTrace.d(
                        "event" to "task_action_result",
                        "source" to source,
                        "taskId" to TaskTrace.taskId(next ?: state),
                        "intent" to intent,
                        "fsmResult" to if (next?.stage == state.stage) "INVALID" else "SUCCESS",
                        "afterStage" to afterStage,
                        "persisted" to true
                    )
                    Log.d("TaskTransition", "source=$source result=SUCCESS newStage=${next?.stage}")
                    IntentActionResult.Success(next)
                }
            }
            TaskChatIntent.REQUEST_VALIDATION -> {
                val result = taskManager.attemptTransition(TaskStage.VALIDATION)
                when (result) {
                    is TaskTransitionResult.Success -> {
                        TaskTrace.d(
                            "event" to "task_action_result",
                            "source" to source,
                            "taskId" to TaskTrace.taskId(result.newState),
                            "intent" to intent,
                            "fsmResult" to "SUCCESS",
                            "afterStage" to result.newState.stage,
                            "persisted" to true
                        )
                        Log.d("TaskTransition", "source=$source result=SUCCESS newStage=${result.newState.stage}")
                        IntentActionResult.Success(result.newState)
                    }
                    is TaskTransitionResult.Invalid -> {
                        TaskTrace.d(
                            "event" to "task_action_result",
                            "source" to source,
                            "taskId" to TaskTrace.taskId(state),
                            "intent" to intent,
                            "fsmResult" to "INVALID",
                            "reason" to result.reason,
                            "suggestedNextAction" to result.suggestedNextAction,
                            "afterStage" to state.stage,
                            "persisted" to false
                        )
                        Log.d("TaskTransition", "source=$source result=INVALID reason=${result.reason}")
                        IntentActionResult.Invalid(result.reason, result.suggestedNextAction)
                    }
                }
            }
            TaskChatIntent.FINISH_TASK -> {
                val result = taskManager.attemptTransition(TaskStage.DONE)
                when (result) {
                    is TaskTransitionResult.Success -> {
                        TaskTrace.d(
                            "event" to "task_action_result",
                            "source" to source,
                            "taskId" to TaskTrace.taskId(result.newState),
                            "intent" to intent,
                            "fsmResult" to "SUCCESS",
                            "afterStage" to result.newState.stage,
                            "persisted" to true
                        )
                        Log.d("TaskTransition", "source=$source result=SUCCESS newStage=${result.newState.stage}")
                        IntentActionResult.Success(result.newState)
                    }
                    is TaskTransitionResult.Invalid -> {
                        TaskTrace.d(
                            "event" to "task_action_result",
                            "source" to source,
                            "taskId" to TaskTrace.taskId(state),
                            "intent" to intent,
                            "fsmResult" to "INVALID",
                            "reason" to result.reason,
                            "suggestedNextAction" to result.suggestedNextAction,
                            "afterStage" to state.stage,
                            "persisted" to false
                        )
                        Log.d("TaskTransition", "source=$source result=INVALID reason=${result.reason}")
                        IntentActionResult.Invalid(result.reason, result.suggestedNextAction)
                    }
                }
            }
            TaskChatIntent.CANCEL_TASK -> {
                val cancelled = taskManager.cancelTask()
                val afterStage = cancelled?.stage ?: state.stage
                TaskTrace.d(
                    "event" to "task_action_result",
                    "source" to source,
                    "taskId" to TaskTrace.taskId(cancelled ?: state),
                    "intent" to intent,
                    "fsmResult" to "SUCCESS",
                    "afterStage" to afterStage,
                    "persisted" to true
                )
                Log.d("TaskTransition", "source=$source result=SUCCESS newStage=${cancelled?.stage}")
                IntentActionResult.Success(cancelled)
            }
            TaskChatIntent.START_COMPLEX_TASK, TaskChatIntent.NONE -> {
                IntentActionResult.Success(state)
            }
        }
    }

    private fun normalizeIntentForStage(
        detectedIntent: TaskChatIntent,
        currentStage: TaskStage?
    ): TaskChatIntent {
        if (currentStage == null) return detectedIntent

        return when (currentStage) {
            TaskStage.VALIDATION -> {
                when (detectedIntent) {
                    TaskChatIntent.APPROVE_PLAN -> TaskChatIntent.FINISH_TASK
                    TaskChatIntent.CONTINUE_TASK -> TaskChatIntent.CONTINUE_TASK
                    else -> detectedIntent
                }
            }
            TaskStage.EXECUTION -> {
                if (detectedIntent == TaskChatIntent.APPROVE_PLAN) TaskChatIntent.CONTINUE_TASK else detectedIntent
            }
            else -> detectedIntent
        }
    }

    private fun isInvariantGuardActive(): Boolean = !invariants.isEmpty()

    private fun isActiveTask(state: TaskState?): Boolean {
        return state != null && state.stage != TaskStage.DONE && state.stage != TaskStage.CANCELLED
    }

    private fun formatCurrencyReply(response: CurrencyToolResponse): String {
        return when (response) {
            is CurrencyToolResponse.InvalidCurrency -> {
                "Не удалось получить курс для пары ${response.base} → ${response.target}. Проверь код валюты."
            }
            is CurrencyToolResponse.Failure -> response.message
            is CurrencyToolResponse.Success -> {
                val amountRequested = response.amountRequested ?: 1.0
                val converted = response.convertedAmount
                val rate = response.rate
                if (converted != null) {
                    "${trimTrailingZeros(amountRequested)} ${response.base} ≈ ${trimTrailingZeros(converted)} ${response.target} по текущему курсу."
                } else if (rate != null) {
                    "Курс ${response.base} → ${response.target}: ${trimTrailingZeros(rate)}."
                } else {
                    "Получен курс ${response.base} → ${response.target}."
                }
            }
        }
    }

    private fun trimTrailingZeros(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        }
    }

}
