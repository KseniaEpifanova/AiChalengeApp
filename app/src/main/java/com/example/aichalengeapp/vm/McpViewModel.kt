package com.example.aichalengeapp.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichalengeapp.mcp.McpConnectionState
import com.example.aichalengeapp.mcp.McpRepository
import com.example.aichalengeapp.mcp.McpToolUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class McpUiState(
    val connectionState: McpConnectionState = McpConnectionState.IDLE,
    val tools: List<McpToolUiModel> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class McpViewModel @Inject constructor(
    private val repository: McpRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(McpUiState())
    val uiState: StateFlow<McpUiState> = _uiState.asStateFlow()

    fun connectAndLoadTools() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                connectionState = McpConnectionState.CONNECTING,
                error = null
            )

            val connect = repository.connect()
            if (connect.isFailure) {
                _uiState.value = _uiState.value.copy(
                    connectionState = McpConnectionState.ERROR,
                    error = connect.exceptionOrNull()?.message ?: "Connection failed"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(connectionState = McpConnectionState.CONNECTED, error = null)
            _uiState.value = _uiState.value.copy(connectionState = McpConnectionState.LOADING_TOOLS)

            val toolsResult = repository.listTools()
            toolsResult
                .onSuccess { tools ->
                    _uiState.value = _uiState.value.copy(
                        connectionState = McpConnectionState.CONNECTED,
                        tools = tools,
                        error = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        connectionState = McpConnectionState.ERROR,
                        error = error.message ?: "Failed to load tools"
                    )
                }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
            _uiState.value = McpUiState(connectionState = McpConnectionState.IDLE)
        }
    }
}
