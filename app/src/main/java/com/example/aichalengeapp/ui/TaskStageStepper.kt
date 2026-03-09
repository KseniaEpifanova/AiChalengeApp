package com.example.aichalengeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aichalengeapp.agent.task.TaskStage

@Composable
fun TaskStageStepper(
    currentStage: TaskStage,
    modifier: Modifier = Modifier
) {
    val stageItems = listOf(
        TaskStage.PLANNING to "Planning",
        TaskStage.EXECUTION to "Execution",
        TaskStage.VALIDATION to "Validation",
        TaskStage.DONE to "Done"
    )

    val currentIndex = stageItems.indexOfFirst { it.first == currentStage }.coerceAtLeast(0)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        stageItems.forEachIndexed { index, item ->
            val isCompleted = index < currentIndex
            val isCurrent = index == currentIndex
            val fillColor: Color = when {
                isCompleted || isCurrent -> Color(0xFF2E7D32)
                else -> Color.Transparent
            }
            val borderColor: Color = if (isCompleted || isCurrent) Color(0xFF2E7D32) else Color(0xFF9E9E9E)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(fillColor, CircleShape)
                        .border(2.dp, borderColor, CircleShape)
                )
                Text(
                    text = item.second,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
