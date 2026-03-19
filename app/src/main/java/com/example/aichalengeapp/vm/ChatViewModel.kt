package com.example.aichalengeapp.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.aichalengeapp.agent.ChatAgent
import com.example.aichalengeapp.agent.ContextOverflowException
import com.example.aichalengeapp.agent.context.StrategyConfig
import com.example.aichalengeapp.agent.guard.InvariantsProfile
import com.example.aichalengeapp.agent.orchestrator.TaskChatIntent
import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.profile.ComplexitySensitivity
import com.example.aichalengeapp.agent.profile.PlanningProfile
import com.example.aichalengeapp.agent.profile.ResponseProfile
import com.example.aichalengeapp.agent.profile.UserProfile
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import com.example.aichalengeapp.agent.task.TaskTransitionResult
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.debug.TaskTrace
import com.example.aichalengeapp.mcp.McpTrace
import com.example.aichalengeapp.retrieval.RetrievalMode
import com.example.aichalengeapp.ui.isTaskPanelVisible
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agent: ChatAgent
) : ViewModel() {
    private companion object {
        private const val TAG = "TaskNextAction"
        private const val TASK_UI_TAG = "TaskPanelVisibility"
    }

    data class UiMessage(
        val id: Long,
        val text: String,
        val isUser: Boolean,
        val isTyping: Boolean = false,
        val isExpanded: Boolean = false,
        val tokenInfo: String? = null
    )

    enum class StrategyTypeUi { SLIDING, FACTS, BRANCHING }

    data class StrategyUiState(
        val type: StrategyTypeUi = StrategyTypeUi.SLIDING,
        val tailN: Int = 6,
        val branches: List<String> = emptyList(),
        val activeBranchId: String? = null
    ) {
        fun toConfig(): StrategyConfig =
            when (type) {
                StrategyTypeUi.SLIDING -> StrategyConfig.SlidingWindow(tailMessageCount = tailN)
                StrategyTypeUi.FACTS -> StrategyConfig.StickyFacts(tailMessageCount = tailN)
                StrategyTypeUi.BRANCHING -> StrategyConfig.Branching(
                    branchId = activeBranchId ?: "A",
                    tailMessageCount = tailN
                )
            }
    }

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _strategy = MutableStateFlow(StrategyUiState())
    val strategy: StateFlow<StrategyUiState> = _strategy.asStateFlow()

    private val _ragEnabled = MutableStateFlow(true)
    val ragEnabled: StateFlow<Boolean> = _ragEnabled.asStateFlow()

    private val _retrievalMode = MutableStateFlow(RetrievalMode.BASELINE)
    val retrievalMode: StateFlow<RetrievalMode> = _retrievalMode.asStateFlow()

    private val _factsJson = MutableStateFlow("")
    val factsJson: StateFlow<String> = _factsJson.asStateFlow()

    private val _workingJson = MutableStateFlow("")
    val workingJson: StateFlow<String> = _workingJson.asStateFlow()

    private val _longTermJson = MutableStateFlow("")
    val longTermJson: StateFlow<String> = _longTermJson.asStateFlow()

    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _profileDirty = MutableStateFlow(false)
    val profileDirty: StateFlow<Boolean> = _profileDirty.asStateFlow()

    private val _profiles = MutableStateFlow<List<AssistantProfile>>(emptyList())
    val profiles: StateFlow<List<AssistantProfile>> = _profiles.asStateFlow()

    private val _editingProfileId = MutableStateFlow<String?>(null)
    val editingProfileId: StateFlow<String?> = _editingProfileId.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    private val _planningDraft = MutableStateFlow(PlanningProfile())
    val planningDraft: StateFlow<PlanningProfile> = _planningDraft.asStateFlow()

    private val _invariantsProfile = MutableStateFlow(InvariantsProfile())
    val invariantsProfile: StateFlow<InvariantsProfile> = _invariantsProfile.asStateFlow()

    private val _invariantsDirty = MutableStateFlow(false)
    val invariantsDirty: StateFlow<Boolean> = _invariantsDirty.asStateFlow()

    private val _guardActive = MutableStateFlow(false)
    val guardActive: StateFlow<Boolean> = _guardActive.asStateFlow()

    private val _taskState = MutableStateFlow<TaskState?>(null)
    val taskState: StateFlow<TaskState?> = _taskState.asStateFlow()

    private var nextId = 0L
    private fun newId(): Long = ++nextId

    init {
        viewModelScope.launch {
            agentIo { init() }
            restoreUiFromHistory(agentIo { getHistory() })
            refreshBranches()

            _factsJson.value = agentIo { getFactsJson() }
            _workingJson.value = agentIo { getWorkingJson() }
            _longTermJson.value = agentIo { getLongTermJson() }

            refreshProfiles()
            _profileDirty.value = false

            _invariantsProfile.value = agentIo { getInvariants() }
            _invariantsDirty.value = false
            _guardActive.value = !_invariantsProfile.value.isEmpty()

            applyTaskState(agentIo { getTaskState() })
        }
    }

    fun updateProfileStyle(v: String) {
        _profile.value = _profile.value.copy(style = v)
        _profileDirty.value = true
    }

    fun updateProfileFormat(v: String) {
        _profile.value = _profile.value.copy(format = v)
        _profileDirty.value = true
    }

    fun updateProfileConstraints(v: String) {
        _profile.value = _profile.value.copy(constraints = v)
        _profileDirty.value = true
    }

    fun saveProfile() {
        viewModelScope.launch {
            val targetId = _editingProfileId.value
            if (targetId == null) {
                agentIo { saveUserProfile(_profile.value) }
                refreshProfiles()
                _profileDirty.value = false
                appendUiMessage("✅ Profile saved", isUser = false)
                return@launch
            }

            val updated = _profiles.value.map { profileItem ->
                if (profileItem.id == targetId) {
                    profileItem.copy(
                        responseProfile = ResponseProfile(
                            style = _profile.value.style,
                            format = _profile.value.format,
                            constraints = _profile.value.constraints
                        ),
                        planningProfile = _planningDraft.value
                    )
                } else {
                    profileItem
                }
            }
            agentIo { saveProfiles(updated) }
            refreshProfiles()
            _profileDirty.value = false
            appendUiMessage("✅ Profile saved", isUser = false)
        }
    }

    fun clearProfile() {
        viewModelScope.launch {
            if (_editingProfileId.value != null) {
                _profile.value = UserProfile()
                _planningDraft.value = PlanningProfile()
                _profileDirty.value = true
                return@launch
            }
            agentIo { clearUserProfile() }
            refreshProfiles()
            _profileDirty.value = false
            appendUiMessage("🧹 Profile cleared", isUser = false)
        }
    }

    fun createProfile(name: String) {
        val profileName = name.trim().ifBlank { "Profile ${(_profiles.value.size + 1)}" }
        viewModelScope.launch {
            val current = agentIo { getProfiles() }
            val newProfile = AssistantProfile(
                id = "p_${System.currentTimeMillis()}",
                name = profileName,
                responseProfile = ResponseProfile(),
                planningProfile = PlanningProfile(),
                isDefault = false
            )
            agentIo { saveProfiles(current + newProfile) }
            refreshProfiles()
            _editingProfileId.value = newProfile.id
            _profile.value = UserProfile()
            _planningDraft.value = newProfile.planningProfile
            _profileDirty.value = false
            appendUiMessage("✅ Profile created: $profileName", isUser = false)
        }
    }

    fun startEditingProfile(profileId: String) {
        viewModelScope.launch {
            val selected = _profiles.value.firstOrNull { it.id == profileId } ?: return@launch
            _editingProfileId.value = selected.id
            _profile.value = UserProfile(
                style = selected.responseProfile.style,
                format = selected.responseProfile.format,
                constraints = selected.responseProfile.constraints
            )
            _planningDraft.value = selected.planningProfile
            _profileDirty.value = false
        }
    }

    fun stopEditingProfile() {
        _editingProfileId.value = null
        _profileDirty.value = false
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            val current = agentIo { getProfiles() }
            val updated = current.filterNot { it.id == profileId }
            agentIo { saveProfiles(updated) }
            refreshProfiles()
            if (_editingProfileId.value == profileId) {
                _editingProfileId.value = null
            }
        }
    }

    fun updatePlanningAutoDetect(value: Boolean) {
        _planningDraft.value = _planningDraft.value.copy(autoDetectComplexity = value)
        _profileDirty.value = true
    }

    fun updatePlanningSensitivity(value: ComplexitySensitivity) {
        _planningDraft.value = _planningDraft.value.copy(complexitySensitivity = value)
        _profileDirty.value = true
    }

    fun updatePlanningRequirePlanApproval(value: Boolean) {
        _planningDraft.value = _planningDraft.value.copy(requirePlanApproval = value)
        _profileDirty.value = true
    }

    fun updatePlanningAutoContinueExecution(value: Boolean) {
        _planningDraft.value = _planningDraft.value.copy(allowAutoContinueExecution = value)
        _profileDirty.value = true
    }

    fun updatePlanningRequireValidation(value: Boolean) {
        _planningDraft.value = _planningDraft.value.copy(requireValidationBeforeDone = value)
        _profileDirty.value = true
    }

    fun updateInvariantTechDecisions(v: String) {
        _invariantsProfile.value = _invariantsProfile.value.copy(techDecisions = v)
        _invariantsDirty.value = true
        _guardActive.value = !_invariantsProfile.value.isEmpty()
    }

    fun updateInvariantBusinessRules(v: String) {
        _invariantsProfile.value = _invariantsProfile.value.copy(businessRules = v)
        _invariantsDirty.value = true
        _guardActive.value = !_invariantsProfile.value.isEmpty()
    }

    fun saveInvariants() {
        viewModelScope.launch {
            agentIo { saveInvariants(_invariantsProfile.value) }
            _invariantsProfile.value = agentIo { getInvariants() }
            _invariantsDirty.value = false
            _guardActive.value = !_invariantsProfile.value.isEmpty()
            appendUiMessage("✅ Invariant Guard saved", isUser = false)
        }
    }

    fun clearInvariants() {
        viewModelScope.launch {
            agentIo { clearInvariants() }
            _invariantsProfile.value = agentIo { getInvariants() }
            _invariantsDirty.value = false
            _guardActive.value = false
            appendUiMessage("🧹 Invariant Guard cleared", isUser = false)
        }
    }

    fun setStrategyType(type: StrategyTypeUi) {
        _strategy.value = _strategy.value.copy(type = type)
        if (type == StrategyTypeUi.BRANCHING) {
            viewModelScope.launch {
                ensureDefaultBranch()
                refreshBranches()
            }
        }
    }

    fun setRagEnabled(enabled: Boolean) {
        _ragEnabled.value = enabled
    }

    fun setRetrievalMode(mode: RetrievalMode) {
        McpTrace.d("event" to "retrieval_mode_ui_click", "selected" to mode.name)
        _retrievalMode.value = mode
        McpTrace.d("event" to "retrieval_mode_selected", "mode" to mode.name)
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isLoading.value) return

        viewModelScope.launch {
            performSend(trimmed, useBootstrapForTask = false)
        }
    }

    fun startTask(goal: String) {
        val trimmed = goal.trim()
        if (trimmed.isEmpty() || _isLoading.value) return
        viewModelScope.launch {
            val state = agentIo { startTaskMode(trimmed) }
            applyTaskState(state)
            _workingJson.value = agentIo { getWorkingJson() }
            performSend(trimmed, useBootstrapForTask = true)
        }
    }

    fun startTaskMode(request: String) {
        startTask(request)
    }

    fun nextTaskStep() {
        if (_isLoading.value) return
        viewModelScope.launch {
            val before = _taskState.value?.stage
            if (before == TaskStage.DONE || before == TaskStage.CANCELLED) {
                TaskTrace.d(
                    "event" to "vm_next_click_ignored",
                    "source" to "button",
                    "taskId" to TaskTrace.taskId(_taskState.value),
                    "beforeStage" to before,
                    "reason" to "terminal_stage"
                )
                appendUiMessage("✅ Task already completed. Start a new task if needed.", isUser = false)
                return@launch
            }
            val cfg = _strategy.value.toConfig()
            appendUiMessage("▶️ Next step", isUser = true)
            TaskTrace.d(
                "event" to "vm_next_click",
                "source" to "button",
                "taskId" to TaskTrace.taskId(_taskState.value),
                "beforeStage" to before,
                "paused" to _taskState.value?.paused
            )
            Log.d(TAG, "nextClicked before=$before")
            val reply = agentIo { handleTaskIntentAction(TaskChatIntent.CONTINUE_TASK, cfg) }
            appendUiMessage(reply.text, isUser = false)
            TaskTrace.d(
                "event" to "vm_next_reply",
                "source" to "button",
                "taskId" to TaskTrace.taskId(_taskState.value),
                "responseLength" to reply.text.length,
                "responsePreview" to TaskTrace.preview(reply.text)
            )
            Log.d(TAG, "apiReplyLength=${reply.text.length}")

            _factsJson.value = agentIo { getFactsJson() }
            _workingJson.value = agentIo { getWorkingJson() }
            _longTermJson.value = agentIo { getLongTermJson() }
            applyTaskState(agentIo { getTaskState() })
        }
    }

    fun pauseTask() {
        if (_isLoading.value) return
        viewModelScope.launch {
            applyTaskState(agentIo { pauseTask() })
            _workingJson.value = agentIo { getWorkingJson() }
        }
    }

    fun resumeTask() {
        if (_isLoading.value) return
        viewModelScope.launch {
            applyTaskState(agentIo { resumeTask() })
            _workingJson.value = agentIo { getWorkingJson() }
        }
    }

    fun approveTaskPlan() {
        if (_isLoading.value) return
        viewModelScope.launch {
            applyTaskState(agentIo { approveTaskPlan() })
            _workingJson.value = agentIo { getWorkingJson() }
            appendUiMessage("✅ Plan approved", isUser = false)
        }
    }

    fun cancelTask() {
        if (_isLoading.value) return
        viewModelScope.launch {
            applyTaskState(agentIo { cancelTask() })
            _workingJson.value = agentIo { getWorkingJson() }
            appendUiMessage("🛑 Task cancelled", isUser = false)
        }
    }

    fun attemptTaskTransition(toStage: TaskStage) {
        if (_isLoading.value) return
        viewModelScope.launch {
            when (val result = agentIo { attemptTransition(toStage) }) {
                is TaskTransitionResult.Success -> {
                    applyTaskState(result.newState)
                }
                is TaskTransitionResult.Invalid -> {
                    appendUiMessage("🚫 ${result.reason}. ${result.suggestedNextAction}", isUser = false)
                    applyTaskState(agentIo { getTaskState() })
                }
            }
            _workingJson.value = agentIo { getWorkingJson() }
        }
    }

    fun stopTask() {
        if (_isLoading.value) return
        viewModelScope.launch {
            agentIo { stopTask() }
            applyTaskState(null)
            _workingJson.value = agentIo { getWorkingJson() }
            appendUiMessage("🛑 Task stopped", isUser = false)
        }
    }

    fun refreshTaskState() {
        viewModelScope.launch {
            applyTaskState(agentIo { getTaskState() })
            _workingJson.value = agentIo { getWorkingJson() }
        }
    }

    private suspend fun performSend(trimmed: String, useBootstrapForTask: Boolean) {
        _isLoading.value = true
        appendUiMessage(trimmed, isUser = true)
        TaskTrace.d(
            "event" to "vm_send",
            "source" to if (useBootstrapForTask) "auto" else "chat",
            "taskId" to TaskTrace.taskId(_taskState.value),
            "msg" to trimmed,
            "beforeStage" to _taskState.value?.stage,
            "paused" to _taskState.value?.paused
        )
        val typingId = addTypingMessage()

        try {
            if (_profileDirty.value) {
                agentIo { saveUserProfile(_profile.value) }
                refreshProfiles()
                _profileDirty.value = false
            }

            if (_invariantsDirty.value) {
                agentIo { saveInvariants(_invariantsProfile.value) }
                _invariantsProfile.value = agentIo { getInvariants() }
                _invariantsDirty.value = false
            }

            if (_strategy.value.type == StrategyTypeUi.BRANCHING) ensureDefaultBranch()

            val cfg = _strategy.value.toConfig()
            val ragEnabled = _ragEnabled.value
            val retrievalMode = _retrievalMode.value
            val reply = if (useBootstrapForTask) {
                agentIo { handleTaskBootstrapMessage(trimmed, cfg) }
            } else {
                agentIo { handleUserMessage(trimmed, cfg, ragEnabled = ragEnabled, retrievalMode = retrievalMode) }
            }

            removeMessageById(typingId)
            val m = reply.metrics
            val costStr = m.estimatedCostUsd?.let { String.format(Locale.US, "%.6f", it) } ?: "—"
            val tokenInfo = "📊 Tokens: user≈${m.estimatedUserTokens}  history≈${m.estimatedHistoryTokens}  prompt≈${m.estimatedPromptTokens}\n" +
                "actual prompt=${m.actualPromptTokens ?: "—"}  completion=${m.actualCompletionTokens ?: "—"}  cost≈$$costStr"
            appendUiMessage(reply.text, isUser = false, tokenInfo = tokenInfo)
            TaskTrace.d(
                "event" to "vm_reply_applied",
                "source" to if (useBootstrapForTask) "auto" else "chat",
                "taskId" to TaskTrace.taskId(_taskState.value),
                "responseLength" to reply.text.length,
                "responsePreview" to TaskTrace.preview(reply.text)
            )

            _factsJson.value = agentIo { getFactsJson() }
            _workingJson.value = agentIo { getWorkingJson() }
            _longTermJson.value = agentIo { getLongTermJson() }
            _guardActive.value = !_invariantsProfile.value.isEmpty()
            applyTaskState(agentIo { getTaskState() })

            if (_strategy.value.type == StrategyTypeUi.BRANCHING) refreshBranches()

        } catch (e: ContextOverflowException) {
            removeMessageById(typingId)
            appendUiMessage("🚫 ${e.message}", isUser = false)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            removeMessageById(typingId)
            appendUiMessage("⚠️ Error: ${t.message ?: t::class.java.simpleName}", isUser = false)
        } finally {
            applyTaskState(agentIo { getTaskState() })
            _workingJson.value = agentIo { getWorkingJson() }
            _isLoading.value = false
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            agentIo { resetAll() }
            _messages.value = emptyList()
            _strategy.value = StrategyUiState()
            _ragEnabled.value = true
            _retrievalMode.value = RetrievalMode.BASELINE
            _factsJson.value = ""
            _workingJson.value = ""
            _longTermJson.value = ""
            _profile.value = UserProfile()
            _profileDirty.value = false
            _invariantsProfile.value = InvariantsProfile()
            _invariantsDirty.value = false
            _guardActive.value = false
            applyTaskState(null)
            nextId = 0L
            refreshProfiles()
            refreshBranches()
        }
    }

    fun clearChatSession() {
        if (_isLoading.value) return
        viewModelScope.launch {
            agentIo { clearChatSession() }
            _messages.value = emptyList()
            nextId = 0L
            _factsJson.value = agentIo { getFactsJson() }
            _workingJson.value = agentIo { getWorkingJson() }
            _longTermJson.value = agentIo { getLongTermJson() }
            applyTaskState(agentIo { getTaskState() })
        }
    }

    fun setCheckpoint() {
        if (_isLoading.value) return
        viewModelScope.launch {
            agentIo { setCheckpointAtCurrent() }
            appendUiMessage("✅ Checkpoint set", isUser = false)
        }
    }

    fun createBranch(branchId: String) {
        if (_isLoading.value) return
        viewModelScope.launch {
            agentIo { createBranch(branchId) }
            _strategy.value = _strategy.value.copy(activeBranchId = branchId)
            refreshBranches()
            appendUiMessage("🌿 Branch created: $branchId", isUser = false)
        }
    }

    fun switchBranch(branchId: String) {
        if (_isLoading.value) return
        viewModelScope.launch {
            agentIo { switchBranch(branchId) }
            _strategy.value = _strategy.value.copy(activeBranchId = branchId)
            refreshBranches()
            appendUiMessage("🔀 Switched to branch: $branchId", isUser = false)
        }
    }

    fun toggleExpand(id: Long) {
        _messages.value = _messages.value.map { if (it.id == id) it.copy(isExpanded = !it.isExpanded) else it }
    }

    private suspend fun applyTaskState(state: TaskState?) {
        _taskState.value = state
        val visible = isTaskPanelVisible(state)
        val snapshot = TaskTrace.snapshot(state, panelVisible = visible)
        TaskTrace.d(
            "event" to "vm_task_state_observed",
            "source" to "viewmodel",
            "taskId" to snapshot.taskId,
            "vmStage" to snapshot.stage,
            "planApproved" to snapshot.planApproved,
            "paused" to snapshot.paused,
            "panelVisible" to snapshot.panelVisible
        )
        Log.d(TASK_UI_TAG, "taskState=${state?.stage} paused=${state?.paused} panelVisible=$visible")
    }

    private suspend fun refreshProfiles() {
        val list = agentIo { getProfiles() }
        val active = agentIo { getActiveProfile() }
        _profiles.value = list
        _activeProfileId.value = active.id
        val editingId = _editingProfileId.value
        if (editingId != null) {
            val editing = list.firstOrNull { it.id == editingId }
            if (editing != null) {
                _profile.value = UserProfile(
                    style = editing.responseProfile.style,
                    format = editing.responseProfile.format,
                    constraints = editing.responseProfile.constraints
                )
                _planningDraft.value = editing.planningProfile
            } else {
                _editingProfileId.value = null
                _profile.value = UserProfile(
                    style = active.responseProfile.style,
                    format = active.responseProfile.format,
                    constraints = active.responseProfile.constraints
                )
                _planningDraft.value = active.planningProfile
            }
        } else {
            _profile.value = UserProfile(
                style = active.responseProfile.style,
                format = active.responseProfile.format,
                constraints = active.responseProfile.constraints
            )
            _planningDraft.value = active.planningProfile
        }
    }

    private suspend fun refreshBranches() {
        val branches = agentIo { getBranches() }
        val active = agentIo { getActiveBranchId() }
        _strategy.value = _strategy.value.copy(
            branches = branches,
            activeBranchId = active ?: _strategy.value.activeBranchId
        )
    }

    private suspend fun ensureDefaultBranch() {
        val branches = agentIo { getBranches() }
        if (branches.isEmpty()) agentIo { createBranch("A") }
        val active = agentIo { getActiveBranchId() }
        if (active == null) agentIo { switchBranch("A") }
        refreshBranches()
    }

    private suspend fun <T> agentIo(block: suspend ChatAgent.() -> T): T {
        return withContext(Dispatchers.IO) { agent.block() }
    }

    private fun restoreUiFromHistory(history: List<AgentMessage>) {
        val ui = history
            .filter { it.role != AgentRole.SYSTEM }
            .map { m -> UiMessage(id = newId(), text = m.content, isUser = m.role == AgentRole.USER) }
        _messages.value = ui
    }

    private fun appendUiMessage(text: String, isUser: Boolean, tokenInfo: String? = null) {
        _messages.value = _messages.value + UiMessage(newId(), text, isUser, tokenInfo = tokenInfo)
    }

    private fun addTypingMessage(): Long {
        val id = newId()
        _messages.value = _messages.value + UiMessage(id, "Typing…", isUser = false, isTyping = true)
        return id
    }

    private fun removeMessageById(id: Long) {
        _messages.value = _messages.value.filterNot { it.id == id }
    }

}
