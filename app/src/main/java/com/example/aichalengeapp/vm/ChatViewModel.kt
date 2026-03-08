package com.example.aichalengeapp.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichalengeapp.agent.ChatAgent
import com.example.aichalengeapp.agent.ContextOverflowException
import com.example.aichalengeapp.agent.context.StrategyConfig
import com.example.aichalengeapp.agent.invariants.InvariantsProfile
import com.example.aichalengeapp.agent.profile.UserProfile
import com.example.aichalengeapp.agent.task.TaskState
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
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

    data class UiMessage(
        val id: Long,
        val text: String,
        val isUser: Boolean,
        val isTyping: Boolean = false,
        val isExpanded: Boolean = false
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

    private val _invariantsProfile = MutableStateFlow(InvariantsProfile())
    val invariantsProfile: StateFlow<InvariantsProfile> = _invariantsProfile.asStateFlow()

    private val _invariantsDirty = MutableStateFlow(false)
    val invariantsDirty: StateFlow<Boolean> = _invariantsDirty.asStateFlow()

    private val _guardEnabled = MutableStateFlow(false)
    val guardEnabled: StateFlow<Boolean> = _guardEnabled.asStateFlow()

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

            _profile.value = agentIo { getUserProfile() }
            _profileDirty.value = false

            _invariantsProfile.value = agentIo { getInvariants() }
            _invariantsDirty.value = false
            _guardEnabled.value = agentIo { isGuardEnabled() }

            _taskState.value = agentIo { getTaskState() }
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
            agentIo { saveUserProfile(_profile.value) }
            _profile.value = agentIo { getUserProfile() }
            _profileDirty.value = false
            appendUiMessage("✅ Profile saved", isUser = false)
        }
    }

    fun clearProfile() {
        viewModelScope.launch {
            agentIo { clearUserProfile() }
            _profile.value = agentIo { getUserProfile() }
            _profileDirty.value = false
            appendUiMessage("🧹 Profile cleared", isUser = false)
        }
    }

    fun updateInvariantTechDecisions(v: String) {
        _invariantsProfile.value = _invariantsProfile.value.copy(techDecisions = v)
        _invariantsDirty.value = true
    }

    fun updateInvariantBusinessRules(v: String) {
        _invariantsProfile.value = _invariantsProfile.value.copy(businessRules = v)
        _invariantsDirty.value = true
    }

    fun saveInvariants() {
        viewModelScope.launch {
            agentIo { saveInvariants(_invariantsProfile.value) }
            _invariantsProfile.value = agentIo { getInvariants() }
            _invariantsDirty.value = false
            appendUiMessage("✅ Invariant Guard saved", isUser = false)
        }
    }

    fun clearInvariants() {
        viewModelScope.launch {
            agentIo { clearInvariants() }
            _invariantsProfile.value = agentIo { getInvariants() }
            _invariantsDirty.value = false
            appendUiMessage("🧹 Invariant Guard cleared", isUser = false)
        }
    }

    fun setGuardEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _guardEnabled.value = enabled
            agentIo { setGuardEnabled(enabled) }
            _guardEnabled.value = agentIo { isGuardEnabled() }
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
            val state = agentIo { startTask(trimmed) }
            _taskState.value = state
            _workingJson.value = agentIo { getWorkingJson() }
            performSend(trimmed, useBootstrapForTask = true)
        }
    }

    fun nextTaskStep() {
        if (_isLoading.value) return
        viewModelScope.launch {
            val oldStage = _taskState.value?.stage
            val state = agentIo { nextTaskStep() }
            applyTaskState(state)
            _workingJson.value = agentIo { getWorkingJson() }
            if (state == null) {
                appendUiMessage("ℹ️ No active task", isUser = false)
            } else if (state.stage == TaskStage.DONE) {
                appendUiMessage("✅ Task completed", isUser = false)
            } else if (oldStage != state.stage) {
                appendUiMessage("➡️ Task stage: $oldStage → ${state.stage}", isUser = false)
            }
        }
    }

    fun pauseTask() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _taskState.value = agentIo { pauseTask() }
            _workingJson.value = agentIo { getWorkingJson() }
        }
    }

    fun resumeTask() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _taskState.value = agentIo { resumeTask() }
            _workingJson.value = agentIo { getWorkingJson() }
        }
    }

    fun stopTask() {
        if (_isLoading.value) return
        viewModelScope.launch {
            agentIo { stopTask() }
            _taskState.value = null
            _workingJson.value = agentIo { getWorkingJson() }
            appendUiMessage("🛑 Task stopped", isUser = false)
        }
    }

    private suspend fun performSend(trimmed: String, useBootstrapForTask: Boolean) {
        _isLoading.value = true
        appendUiMessage(trimmed, isUser = true)
        val typingId = addTypingMessage()

        try {
            if (_profileDirty.value) {
                agentIo { saveUserProfile(_profile.value) }
                _profile.value = agentIo { getUserProfile() }
                _profileDirty.value = false
            }

            if (_strategy.value.type == StrategyTypeUi.BRANCHING) ensureDefaultBranch()

            val cfg = _strategy.value.toConfig()
            val reply = if (useBootstrapForTask) {
                agentIo { handleTaskBootstrapMessage(trimmed, cfg) }
            } else {
                agentIo { handleUserMessage(trimmed, cfg) }
            }

            removeMessageById(typingId)
            appendUiMessage(reply.text, isUser = false)

            val m = reply.metrics
            val costStr = m.estimatedCostUsd?.let { String.format(Locale.US, "%.6f", it) } ?: "—"
            appendUiMessage(
                "📊 Tokens: user≈${m.estimatedUserTokens}, history≈${m.estimatedHistoryTokens}, prompt≈${m.estimatedPromptTokens} | actual prompt=${m.actualPromptTokens ?: "—"}, completion=${m.actualCompletionTokens ?: "—"} | cost≈$$costStr",
                isUser = false
            )

            _factsJson.value = agentIo { getFactsJson() }
            _workingJson.value = agentIo { getWorkingJson() }
            _longTermJson.value = agentIo { getLongTermJson() }
            _profile.value = agentIo { getUserProfile() }
            _guardEnabled.value = agentIo { isGuardEnabled() }
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
            _isLoading.value = false
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            agentIo { resetAll() }
            _messages.value = emptyList()
            _strategy.value = StrategyUiState()
            _factsJson.value = ""
            _workingJson.value = ""
            _longTermJson.value = ""
            _profile.value = UserProfile()
            _profileDirty.value = false
            _invariantsProfile.value = InvariantsProfile()
            _invariantsDirty.value = false
            _guardEnabled.value = false
            nextId = 0L
            refreshBranches()
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
        if (state?.stage == TaskStage.DONE) {
            agentIo { stopTask() }
            _taskState.value = null
            _workingJson.value = agentIo { getWorkingJson() }
        } else {
            _taskState.value = state
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

    private fun appendUiMessage(text: String, isUser: Boolean) {
        _messages.value = _messages.value + UiMessage(newId(), text, isUser)
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
