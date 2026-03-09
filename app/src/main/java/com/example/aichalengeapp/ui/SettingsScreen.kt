package com.example.aichalengeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import com.example.aichalengeapp.vm.ChatViewModel

@Composable
fun SettingsScreen(
    currentStrategy: ChatViewModel.StrategyTypeUi,
    isLoading: Boolean,
    onSelectStrategy: (ChatViewModel.StrategyTypeUi) -> Unit,
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
