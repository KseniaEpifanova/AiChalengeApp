package com.example.aichalengeapp.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichalengeapp.agent.ChatAgent
import com.example.aichalengeapp.agent.ContextOverflowException
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow

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

    init {
        viewModelScope.launch {
            agent.init()
            val history = agent.getHistory()
            restoreUiFromHistory(history)
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true

            appendUiMessage(
                text = trimmed,
                isUser = true
            )

            try {
                try {
                    val reply = agent.handleUserMessage(trimmed)

                    appendUiMessage(reply.text, isUser = false)

                    val m = reply.metrics
                    val cost = m.estimatedCostUsd?.let { String.format(java.util.Locale.US, "%.6f", it) } ?: "—"

                    appendUiMessage(
                        text = "📊 Tokens: user≈${m.estimatedUserTokens}, history≈${m.estimatedHistoryTokens}, prompt≈${m.estimatedPromptTokens} | actual prompt=${m.actualPromptTokens ?: "—"}, completion=${m.actualCompletionTokens ?: "—"} | cost≈$$cost",
                        isUser = false
                    )
                } catch (e: ContextOverflowException) {
                    appendUiMessage("🚫 ${e.message}", isUser = false)
                }
            } catch (t: Throwable) {
                appendUiMessage(
                    text = "⚠️ Error: ${t.message ?: t::class.java.simpleName}",
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
        }
    }

    private fun restoreUiFromHistory(history: List<AgentMessage>) {
        val ui = history
            .filter { it.role != AgentRole.SYSTEM }
            .map { msg ->
                UiMessage(
                    id = msg.timestampMs,
                    text = msg.content,
                    isUser = msg.role == AgentRole.USER
                )
            }

        _messages.value = ui
    }

    private fun appendUiMessage(text: String, isUser: Boolean) {
        val newMessage = UiMessage(
            id = System.currentTimeMillis(),
            text = text,
            isUser = isUser
        )
        _messages.value += newMessage
    }
}
