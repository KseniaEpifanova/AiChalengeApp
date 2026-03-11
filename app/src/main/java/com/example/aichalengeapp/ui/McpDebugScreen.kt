package com.example.aichalengeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aichalengeapp.mcp.McpConnectionState
import com.example.aichalengeapp.vm.McpUiState

@Composable
fun McpDebugScreen(
    state: McpUiState,
    onConnectAndLoad: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("MCP Debug", style = MaterialTheme.typography.headlineSmall)
        Text("Status: ${state.connectionState.name}", style = MaterialTheme.typography.bodyMedium)

        state.error?.let {
            Text(
                text = "Error: $it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = onConnectAndLoad,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.connectionState != McpConnectionState.CONNECTING &&
                state.connectionState != McpConnectionState.LOADING_TOOLS
        ) {
            Text("Connect and Load Tools")
        }

        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disconnect")
        }

        Text("Tools (${state.tools.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.tools) { tool ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(tool.name, style = MaterialTheme.typography.titleSmall)
                        if (tool.description.isNotBlank()) {
                            Text(tool.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
