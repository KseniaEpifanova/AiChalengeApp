package com.example.aichalengeapp.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichalengeapp.mcp.McpTrace
import com.example.aichalengeapp.support.SupportAnswer
import com.example.aichalengeapp.support.SupportAssistant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SupportUiState(
    val question: String = "",
    val answer: SupportAnswer? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SupportViewModel @Inject constructor(
    private val supportAssistant: SupportAssistant
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupportUiState())
    val uiState: StateFlow<SupportUiState> = _uiState.asStateFlow()

    fun updateQuestion(question: String) {
        _uiState.value = _uiState.value.copy(question = question, error = null)
    }

    fun submit() {
        val question = _uiState.value.question.trim()
        if (question.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter a support question.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { supportAssistant.answer(question) }
                .onSuccess { answer ->
                    McpTrace.d("event" to "support_answer_ready", "sources" to answer.sources.joinToString(","))
                    _uiState.value = _uiState.value.copy(answer = answer, isLoading = false, error = null)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Unable to generate support answer."
                    )
                }
        }
    }
}
