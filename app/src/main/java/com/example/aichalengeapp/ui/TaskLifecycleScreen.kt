package com.example.aichalengeapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState

@Composable
fun TaskLifecycleScreen(
    taskState: TaskState?,
    isLoading: Boolean,
    onNextStep: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Task Lifecycle", style = MaterialTheme.typography.titleLarge)

        if (taskState == null || taskState.stage == TaskStage.CANCELLED || taskState.stage == TaskStage.DONE) {
            Text("No active task", style = MaterialTheme.typography.bodyMedium)
            return
        }

        TaskCard(
            taskState = taskState,
            isLoading = isLoading,
            onNextStep = onNextStep,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel
        )
    }
}
