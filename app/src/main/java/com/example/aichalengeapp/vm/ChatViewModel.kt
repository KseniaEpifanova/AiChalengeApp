package com.example.aichalengeapp.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichalengeapp.data.MessageUi
import com.example.aichalengeapp.repo.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: ChatRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<MessageUi>>(emptyList())
    val messages: StateFlow<List<MessageUi>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        _messages.value = _messages.value + MessageUi(
            id = UUID.randomUUID().toString(),
            text = trimmed,
            isUser = true
        )

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val reply = repo.ask(trimmed)
                _messages.value = _messages.value + MessageUi(
                    id = UUID.randomUUID().toString(),
                    text = reply,
                    isUser = false
                )
            } catch (e: Exception) {
                _messages.value = _messages.value + MessageUi(
                    id = UUID.randomUUID().toString(),
                    text = "Ошибка: ${e.message}",
                    isUser = false
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
}
