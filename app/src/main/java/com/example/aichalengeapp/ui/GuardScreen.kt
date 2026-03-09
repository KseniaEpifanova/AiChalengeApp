package com.example.aichalengeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aichalengeapp.agent.guard.InvariantsProfile

@Composable
fun GuardScreen(
    profile: InvariantsProfile,
    isGuardActive: Boolean,
    dirty: Boolean,
    isLoading: Boolean,
    onTechDecisionsChange: (String) -> Unit,
    onBusinessRulesChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Invariant Guard", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isGuardActive) "Active automatically (invariants are set)" else "Inactive (add invariants to enable)",
            style = MaterialTheme.typography.bodySmall,
            color = if (isGuardActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (dirty) "Unsaved changes" else "Saved",
            style = MaterialTheme.typography.bodySmall,
            color = if (dirty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = profile.techDecisions,
            onValueChange = onTechDecisionsChange,
            label = { Text("Technical decisions") },
            placeholder = { Text("Compose only, Coroutines, no RxJava") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            minLines = 2
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = profile.businessRules,
            onValueChange = onBusinessRulesChange,
            label = { Text("Business rules") },
            placeholder = { Text("No paid APIs, no secrets storage") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            minLines = 2
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, enabled = dirty && !isLoading) {
                Text("Save")
            }
            OutlinedButton(onClick = onClear, enabled = !isLoading) {
                Text("Clear")
            }
        }
    }
}
