package com.example.aichalengeapp.ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aichalengeapp.repo.LlmProvider
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import com.example.aichalengeapp.retrieval.RetrievalMode
import com.example.aichalengeapp.vm.ChatViewModel

@Composable
fun SettingsScreen(
    currentStrategy: ChatViewModel.StrategyTypeUi,
    ragEnabled: Boolean,
    retrievalMode: RetrievalMode,
    llmProvider: LlmProvider,
    localLlmBaseUrl: String,
    isLoading: Boolean,
    onSelectStrategy: (ChatViewModel.StrategyTypeUi) -> Unit,
    onRagEnabledChange: (Boolean) -> Unit,
    onRetrievalModeChange: (RetrievalMode) -> Unit,
    onLlmProviderChange: (LlmProvider) -> Unit,
    onLocalLlmBaseUrlChange: (String) -> Unit,
    taskState: TaskState?,
    onNextStep: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Text("Strategy", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onSelectStrategy(ChatViewModel.StrategyTypeUi.SLIDING) },
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) { Text("Sliding") }

            OutlinedButton(
                onClick = { onSelectStrategy(ChatViewModel.StrategyTypeUi.FACTS) },
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) { Text("Facts") }

            OutlinedButton(
                onClick = { onSelectStrategy(ChatViewModel.StrategyTypeUi.BRANCHING) },
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) { Text("Branching") }
        }
        Text("Current strategy: $currentStrategy", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(8.dp))
        Text("LLM Provider", style = MaterialTheme.typography.titleMedium)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SegmentedOptionButton(
                    label = "REMOTE",
                    selected = llmProvider == LlmProvider.REMOTE,
                    enabled = !isLoading,
                    onClick = { onLlmProviderChange(LlmProvider.REMOTE) },
                    modifier = Modifier.weight(1f)
                )
                SegmentedOptionButton(
                    label = "LOCAL",
                    selected = llmProvider == LlmProvider.LOCAL,
                    enabled = !isLoading,
                    onClick = { onLlmProviderChange(LlmProvider.LOCAL) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        OutlinedTextField(
            value = localLlmBaseUrl,
            onValueChange = onLocalLlmBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            label = { Text("Ollama base URL") },
            supportingText = { Text("Emulator default: http://10.0.2.2:11434") }
        )

        Spacer(Modifier.height(8.dp))
        Text("Retrieval Mode", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("RAG", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (ragEnabled) "Enabled: retrieve chunks before LLM" else "Disabled: direct LLM only",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = ragEnabled,
                onCheckedChange = onRagEnabledChange,
                enabled = !isLoading
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SegmentedOptionButton(
                    label = "BASELINE",
                    selected = retrievalMode == RetrievalMode.BASELINE,
                    enabled = !isLoading,
                    onClick = { onRetrievalModeChange(RetrievalMode.BASELINE) },
                    modifier = Modifier.weight(1f)
                )
                SegmentedOptionButton(
                    label = "FILTERED",
                    selected = retrievalMode == RetrievalMode.IMPROVED,
                    enabled = !isLoading,
                    onClick = { onRetrievalModeChange(RetrievalMode.IMPROVED) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Task Lifecycle", style = MaterialTheme.typography.titleMedium)
        if (taskState == null || taskState.stage == TaskStage.CANCELLED || taskState.stage == TaskStage.DONE) {
            Text("No active task", style = MaterialTheme.typography.bodyMedium)
        } else {
            TaskCard(
                taskState = taskState,
                isLoading = isLoading,
                onNextStep = onNextStep,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("Danger zone", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = { showResetDialog = true },
            enabled = !isLoading
        ) {
            Text("Reset all")
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all data?") },
            text = { Text("This will remove profiles, invariants, chat state, and assistant configuration.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetAll()
                    }
                ) { Text("Reset all") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SegmentedOptionButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.6f),
            disabledContentColor = contentColor.copy(alpha = 0.8f)
        )
    ) {
        Text(label)
    }
}
