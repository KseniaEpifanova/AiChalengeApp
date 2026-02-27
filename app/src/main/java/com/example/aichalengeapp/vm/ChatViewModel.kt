package com.example.aichalengeapp.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.agent.ChatAgent
import com.example.aichalengeapp.agent.ContextOverflowException
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
        val isUser: Boolean
    )

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _compressionEnabled = MutableStateFlow(true)
    val compressionEnabled: StateFlow<Boolean> = _compressionEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            agent.init()
            restoreUiFromHistory(agent.getHistory())
        }
    }

    fun setCompressionEnabled(enabled: Boolean) {
        _compressionEnabled.value = enabled
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true

            appendUiMessage(trimmed, isUser = true)

            try {
                val reply = agent.handleUserMessage(
                    userText = trimmed,
                    compressionEnabled = _compressionEnabled.value
                )

                appendUiMessage(reply.text, isUser = false)

                val m = reply.metrics
                val costStr = m.estimatedCostUsd?.let { String.format(Locale.US, "%.6f", it) } ?: "—"
                appendUiMessage(
                    text = "📊 Tokens: user≈${m.estimatedUserTokens}, history≈${m.estimatedHistoryTokens}, prompt≈${m.estimatedPromptTokens} | actual prompt=${m.actualPromptTokens ?: "—"}, completion=${m.actualCompletionTokens ?: "—"} | cost≈$$costStr",
                    isUser = false
                )
            } catch (e: ContextOverflowException) {
                appendUiMessage("🚫 ${e.message}", isUser = false)
            } catch (t: Throwable) {
                appendUiMessage("⚠️ Error: ${t.message ?: t::class.java.simpleName}", isUser = false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetChat() {
        viewModelScope.launch {
            agent.reset()
            _messages.value = emptyList()
        }
    }

    private fun restoreUiFromHistory(history: List<AgentMessage>) {
        val ui = history
            .filter { it.role != AgentRole.SYSTEM }
            .map {
                UiMessage(
                    id = it.timestampMs,
                    text = it.content,
                    isUser = it.role == AgentRole.USER
                )
            }
        _messages.value = ui
    }

    private fun appendUiMessage(text: String, isUser: Boolean) {
        val msg = UiMessage(
            id = System.currentTimeMillis(),
            text = text,
            isUser = isUser
        )
        _messages.value += msg
    }
}
