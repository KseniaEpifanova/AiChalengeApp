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
import com.example.aichalengeapp.agent.profile.UserProfile

@Composable
fun ProfileScreen(
    profile: UserProfile,
    profileDirty: Boolean,
    isLoading: Boolean,
    onStyleChange: (String) -> Unit,
    onFormatChange: (String) -> Unit,
    onConstraintsChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("User Profile", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = profile.style,
            onValueChange = onStyleChange,
            label = { Text("Style") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = profile.format,
            onValueChange = onFormatChange,
            label = { Text("Format") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = profile.constraints,
            onValueChange = onConstraintsChange,
            label = { Text("Constraints") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, enabled = profileDirty && !isLoading) {
                Text("Save")
            }
            OutlinedButton(onClick = onClear, enabled = !isLoading) {
                Text("Clear")
            }
        }
    }
}
