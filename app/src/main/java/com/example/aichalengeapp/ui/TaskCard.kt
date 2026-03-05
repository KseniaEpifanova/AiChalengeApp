package com.example.aichalengeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState

@Composable
fun TaskCard(
    taskState: TaskState,
    isLoading: Boolean,
    onNextStep: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Task Mode", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Goal: ${taskState.goal}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (taskState.paused) "Stage: ${taskState.stage} (Paused)" else "Stage: ${taskState.stage}",
                style = MaterialTheme.typography.bodySmall
            )
            if (taskState.paused) {
                Text(
                    text = "Paused",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = "Progress: ${taskState.currentStep.coerceAtMost(taskState.steps.size)} / ${taskState.steps.size} steps",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(8.dp))
            TaskStageStepper(currentStage = taskState.stage)

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onNextStep,
                    enabled = !isLoading && !taskState.paused && taskState.stage != TaskStage.DONE
                ) {
                    Text("Next Stage")
                }
                OutlinedButton(onClick = if (taskState.paused) onResume else onPause, enabled = !isLoading) {
                    Text(if (taskState.paused) "Resume" else "Pause")
                }
                OutlinedButton(onClick = onStop, enabled = !isLoading) {
                    Text("Stop")
                }
            }
        }
    }
}
