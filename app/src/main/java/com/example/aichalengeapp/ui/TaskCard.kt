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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import com.example.aichalengeapp.agent.task.stageToStep
import com.example.aichalengeapp.debug.TaskTrace

@Composable
fun TaskCard(
    taskState: TaskState,
    isLoading: Boolean,
    onNextStep: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(taskState.stage, taskState.paused) {
        TaskTrace.d(
            "event" to "ui_task_card_render",
            "source" to "compose",
            "taskId" to TaskTrace.taskId(taskState),
            "uiStage" to taskState.stage,
            "paused" to taskState.paused,
            "panelVisible" to true
        )
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Task Lifecycle", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Goal: ${taskState.goal}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (taskState.paused) "Stage: ${taskState.stage} (Paused)" else "Stage: ${taskState.stage}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Current step: ${stageToStep(taskState.stage).title}",
                style = MaterialTheme.typography.bodySmall
            )
            if (taskState.paused) {
                Text(
                    text = "Paused",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TaskStageStepper(currentStage = taskState.stage)

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = if (taskState.paused) onResume else onPause, enabled = !isLoading) {
                    Text(if (taskState.paused) "Resume" else "Pause")
                }
                Button(
                    onClick = onNextStep,
                    enabled = !isLoading && !taskState.paused && taskState.stage != TaskStage.DONE && taskState.stage != TaskStage.CANCELLED
                ) {
                    Text("Next Step")
                }
                OutlinedButton(onClick = onCancel, enabled = !isLoading) {
                    Text("Cancel")
                }
            }
        }
    }
}
