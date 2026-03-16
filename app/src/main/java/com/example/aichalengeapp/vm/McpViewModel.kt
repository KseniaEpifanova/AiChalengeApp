package com.example.aichalengeapp.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichalengeapp.mcp.McpConnectionState
import com.example.aichalengeapp.mcp.McpMultiServerRepository
import com.example.aichalengeapp.mcp.McpServerRegistry
import com.example.aichalengeapp.mcp.McpServerTarget
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
    val serverUrls: Map<McpServerTarget, String> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class McpViewModel @Inject constructor(
    private val repository: McpMultiServerRepository,
    private val serverRegistry: McpServerRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        McpUiState(
            serverUrls = serverRegistry.describe().associate { it.target to it.endpoint }
        )
    )
    val uiState: StateFlow<McpUiState> = _uiState.asStateFlow()

    fun connectAndLoadTools() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                connectionState = McpConnectionState.CONNECTING,
                error = null
            )

            val targets = serverRegistry.allTargets()
            val connectFailures = mutableListOf<String>()
            targets.forEach { target ->
                val connect = repository.connect(target)
                if (connect.isFailure) {
                    connectFailures += "${target.serverId}: ${connect.exceptionOrNull()?.message ?: "Connection failed"}"
                }
            }
            if (connectFailures.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    connectionState = McpConnectionState.ERROR,
                    error = connectFailures.joinToString("\n")
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(connectionState = McpConnectionState.CONNECTED, error = null)
            _uiState.value = _uiState.value.copy(connectionState = McpConnectionState.LOADING_TOOLS)

            val loadedTools = mutableListOf<McpToolUiModel>()
            val toolErrors = mutableListOf<String>()
            targets.forEach { target ->
                repository.listTools(target)
                    .onSuccess { tools ->
                        loadedTools += tools.map { tool ->
                            tool.copy(name = "${target.serverId}:${tool.name}")
                        }
                    }
                    .onFailure { error ->
                        toolErrors += "${target.serverId}: ${error.message ?: "Failed to load tools"}"
                    }
            }
            if (toolErrors.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    connectionState = McpConnectionState.ERROR,
                    error = toolErrors.joinToString("\n")
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    connectionState = McpConnectionState.CONNECTED,
                    tools = loadedTools,
                    error = null
                )
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnectAll()
            _uiState.value = McpUiState(
                connectionState = McpConnectionState.IDLE,
                serverUrls = serverRegistry.describe().associate { it.target to it.endpoint }
            )
        }
    }
}
