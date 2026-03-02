package com.example.aichalengeapp.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.agent.ChatAgent
import com.example.aichalengeapp.agent.ContextOverflowException
import com.example.aichalengeapp.agent.context.StrategyConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

        // Branching
        val branches: List<String> = emptyList(),
        val activeBranchId: String? = null
    ) {
        fun toConfig(): StrategyConfig =
            when (type) {
                StrategyTypeUi.SLIDING -> StrategyConfig.SlidingWindow(tailMessageCount = tailN)
                StrategyTypeUi.FACTS -> StrategyConfig.StickyFacts(tailMessageCount = tailN)
                StrategyTypeUi.BRANCHING -> {
                    val id = activeBranchId ?: "A"
                    StrategyConfig.Branching(branchId = id, tailMessageCount = tailN)
                }
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

    private var nextId = 0L
    private fun newId(): Long = ++nextId

    init {
        viewModelScope.launch {
            agent.init()
            restoreUiFromHistory(agent.getHistory())
            refreshBranches()
            _factsJson.value = agent.getFactsJson()
        }
    }

    fun setStrategyType(type: StrategyTypeUi) {
        _strategy.value = _strategy.value.copy(type = type)
        // если перешли в Branching — убедимся что есть ветка
        if (type == StrategyTypeUi.BRANCHING) {
            viewModelScope.launch {
                ensureDefaultBranch()
                refreshBranches()
            }
        }
    }

    fun setTailN(n: Int) {
        _strategy.value = _strategy.value.copy(tailN = n.coerceIn(4, 60))
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            appendUiMessage(trimmed, isUser = true)
            val typingId = addTypingMessage()

            try {
                // Для Branching: если ветка не создана — создадим
                if (_strategy.value.type == StrategyTypeUi.BRANCHING) {
                    ensureDefaultBranch()
                }

                val config = _strategy.value.toConfig()
                val reply = agent.handleUserMessage(trimmed, config)

                removeMessageById(typingId)
                appendUiMessage(reply.text, isUser = false)

                // tokens/cost
                val m = reply.metrics
                val costStr =
                    m.estimatedCostUsd?.let { String.format(Locale.US, "%.6f", it) } ?: "—"
                appendUiMessage(
                    text = "📊 Tokens: user≈${m.estimatedUserTokens}, history≈${m.estimatedHistoryTokens}, prompt≈${m.estimatedPromptTokens} | actual prompt=${m.actualPromptTokens ?: "—"}, completion=${m.actualCompletionTokens ?: "—"} | cost≈$$costStr",
                    isUser = false
                )

                // strategy debug
                appendUiMessage(
                    text = "🧠 Strategy: ${_strategy.value.type} | tail=${_strategy.value.tailN} | branch=${_strategy.value.activeBranchId ?: "—"}",
                    isUser = false
                )
                if (_strategy.value.type == StrategyTypeUi.FACTS) {
                    _factsJson.value = agent.getFactsJson()
                }

                // обновим список веток после сообщений
                if (_strategy.value.type == StrategyTypeUi.BRANCHING) {
                    refreshBranches()
                }

            } catch (e: ContextOverflowException) {
                removeMessageById(typingId)
                appendUiMessage("🚫 ${e.message}", isUser = false)
            } catch (t: Throwable) {
                appendUiMessage(
                    "⚠️ Error: ${t.message ?: t::class.java.simpleName}",
                    isUser = false
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetChat() {
        viewModelScope.launch {
            agent.reset()
            _messages.value = emptyList()
            _strategy.value = StrategyUiState()
            _factsJson.value = ""
            refreshBranches()
        }
    }

    // Branching controls

    fun setCheckpoint() {
        if (_isLoading.value) return
        viewModelScope.launch {
            agent.setCheckpointAtCurrent()
            appendUiMessage("✅ Checkpoint set at current position", isUser = false)
        }
    }

    fun createBranch(branchId: String) {
        if (_isLoading.value) return
        viewModelScope.launch {
            agent.createBranch(branchId)
            _strategy.value = _strategy.value.copy(activeBranchId = branchId)
            refreshBranches()
            appendUiMessage("🌿 Branch created: $branchId", isUser = false)
        }
    }

    fun switchBranch(branchId: String) {
        if (_isLoading.value) return
        viewModelScope.launch {
            agent.switchBranch(branchId)
            _strategy.value = _strategy.value.copy(activeBranchId = branchId)
            refreshBranches()
            appendUiMessage("🔀 Switched to branch: $branchId", isUser = false)
        }
    }

    private suspend fun refreshBranches() {
        val branches = agent.getBranches()
        val active = agent.getActiveBranchId()
        _strategy.value = _strategy.value.copy(
            branches = branches,
            activeBranchId = active ?: _strategy.value.activeBranchId
        )
    }

    private suspend fun ensureDefaultBranch() {
        val branches = agent.getBranches()
        if (branches.isEmpty()) {
            agent.createBranch("A")
        }
        val active = agent.getActiveBranchId()
        if (active == null) {
            agent.switchBranch(branches.firstOrNull() ?: "A")
        }
        refreshBranches()
    }

    private fun restoreUiFromHistory(history: List<AgentMessage>) {
        val ui = history
            .filter { it.role != AgentRole.SYSTEM }
            .map {
                UiMessage(
                    id = newId(),
                    text = it.content,
                    isUser = it.role == AgentRole.USER
                )
            }

        _messages.value = ui
    }

    private fun appendUiMessage(text: String, isUser: Boolean) {
        _messages.value += UiMessage(
            id = newId(),
            text = text,
            isUser = isUser
        )
    }

    private fun addTypingMessage(): Long {
        val id = newId()
        _messages.value += UiMessage(
            id = id,
            text = "Typing…",
            isUser = false,
            isTyping = true
        )
        return id
    }

    private fun removeMessageById(id: Long) {
        _messages.value = _messages.value.filterNot { it.id == id }
    }

    fun toggleExpand(id: Long) {
        _messages.value = _messages.value.map {
            if (it.id == id) it.copy(isExpanded = !it.isExpanded)
            else it
        }
    }
}
