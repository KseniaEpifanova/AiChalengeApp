package com.example.aichalengeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aichalengeapp.vm.ChatViewModel

@Composable
fun StrategyScreen(
    currentStrategy: ChatViewModel.StrategyTypeUi,
    isLoading: Boolean,
    onSelectStrategy: (ChatViewModel.StrategyTypeUi) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Strategy", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text("Current: $currentStrategy", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onSelectStrategy(ChatViewModel.StrategyTypeUi.SLIDING) },
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Text("Sliding")
            }
            OutlinedButton(
                onClick = { onSelectStrategy(ChatViewModel.StrategyTypeUi.FACTS) },
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Text("Facts")
            }
            OutlinedButton(
                onClick = { onSelectStrategy(ChatViewModel.StrategyTypeUi.BRANCHING) },
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Text("Branching")
            }
        }
    }
}
