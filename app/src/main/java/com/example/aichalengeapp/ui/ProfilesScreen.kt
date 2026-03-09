package com.example.aichalengeapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.profile.ComplexitySensitivity
import com.example.aichalengeapp.agent.profile.PlanningProfile
import com.example.aichalengeapp.agent.profile.UserProfile

private enum class ProfileScreenMode {
    LIST,
    EDITOR
}

@Composable
fun ProfilesScreen(
    profiles: List<AssistantProfile>,
    profile: UserProfile,
    planningDraft: PlanningProfile,
    profileDirty: Boolean,
    isLoading: Boolean,
    onCreateProfile: (String) -> Unit,
    onStartEditingProfile: (String) -> Unit,
    onStopEditingProfile: () -> Unit,
    onDeleteProfile: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    onFormatChange: (String) -> Unit,
    onConstraintsChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onAutoDetectChange: (Boolean) -> Unit,
    onSensitivityChange: (ComplexitySensitivity) -> Unit,
    onRequirePlanApprovalChange: (Boolean) -> Unit,
    onAllowAutoContinueChange: (Boolean) -> Unit,
    onRequireValidationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var newProfileName by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(ProfileScreenMode.LIST) }
    var profileToDelete by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = mode == ProfileScreenMode.EDITOR) {
        onStopEditingProfile()
        mode = ProfileScreenMode.LIST
    }

    when (mode) {
        ProfileScreenMode.LIST -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text("Profiles", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                Text("Saved profiles", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(profiles, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = item.name, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(6.dp))
                                Text("Style: ${item.responseProfile.style.ifBlank { "-" }}")
                                Text("Format: ${item.responseProfile.format.ifBlank { "-" }}")
                                Text("Constraints: ${item.responseProfile.constraints.ifBlank { "-" }}")

                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            onStartEditingProfile(item.id)
                                            mode = ProfileScreenMode.EDITOR
                                        },
                                        enabled = !isLoading
                                    ) {
                                        Text("View/Edit")
                                    }
                                    OutlinedButton(
                                        onClick = { profileToDelete = item.id },
                                        enabled = !isLoading && !item.isDefault
                                    ) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Profile name") },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )
                    Button(
                        onClick = {
                            onCreateProfile(newProfileName)
                            newProfileName = ""
                            mode = ProfileScreenMode.EDITOR
                        },
                        enabled = !isLoading
                    ) {
                        Text("Add profile")
                    }
                }
            }
        }

        ProfileScreenMode.EDITOR -> {
            ProfileEditorContent(
                profile = profile,
                planningDraft = planningDraft,
                profileDirty = profileDirty,
                isLoading = isLoading,
                onStyleChange = onStyleChange,
                onFormatChange = onFormatChange,
                onConstraintsChange = onConstraintsChange,
                onSave = {
                    onSave()
                    onStopEditingProfile()
                    mode = ProfileScreenMode.LIST
                },
                onClear = onClear,
                onBack = {
                    onStopEditingProfile()
                    mode = ProfileScreenMode.LIST
                },
                onAutoDetectChange = onAutoDetectChange,
                onSensitivityChange = onSensitivityChange,
                onRequirePlanApprovalChange = onRequirePlanApprovalChange,
                onAllowAutoContinueChange = onAllowAutoContinueChange,
                onRequireValidationChange = onRequireValidationChange,
                modifier = modifier
            )
        }
    }

    val deleteId = profileToDelete
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete profile?") },
            text = { Text("This profile will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProfile(deleteId)
                        profileToDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileEditorContent(
    profile: UserProfile,
    planningDraft: PlanningProfile,
    profileDirty: Boolean,
    isLoading: Boolean,
    onStyleChange: (String) -> Unit,
    onFormatChange: (String) -> Unit,
    onConstraintsChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onAutoDetectChange: (Boolean) -> Unit,
    onSensitivityChange: (ComplexitySensitivity) -> Unit,
    onRequirePlanApprovalChange: (Boolean) -> Unit,
    onAllowAutoContinueChange: (Boolean) -> Unit,
    onRequireValidationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Profile editor", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onBack, enabled = !isLoading) {
                    Text("Back")
                }
            }
        }

        item {
            OutlinedTextField(
                value = profile.style,
                onValueChange = onStyleChange,
                label = { Text("Style") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
        }

        item {
            OutlinedTextField(
                value = profile.format,
                onValueChange = onFormatChange,
                label = { Text("Format") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
        }

        item {
            OutlinedTextField(
                value = profile.constraints,
                onValueChange = onConstraintsChange,
                label = { Text("Constraints") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, enabled = profileDirty && !isLoading) { Text("Save") }
                OutlinedButton(onClick = onClear, enabled = !isLoading) { Text("Clear") }
            }
        }

        item {
            Text("Planning settings", style = MaterialTheme.typography.titleMedium)
        }

        item {
            SettingToggleRow(
                label = "Auto detect complexity",
                checked = planningDraft.autoDetectComplexity,
                enabled = !isLoading,
                onCheckedChange = onAutoDetectChange
            )
        }

        item {
            SettingToggleRow(
                label = "Require plan approval",
                checked = planningDraft.requirePlanApproval,
                enabled = !isLoading,
                onCheckedChange = onRequirePlanApprovalChange
            )
        }

        item {
            SettingToggleRow(
                label = "Auto continue execution",
                checked = planningDraft.allowAutoContinueExecution,
                enabled = !isLoading,
                onCheckedChange = onAllowAutoContinueChange
            )
        }

        item {
            SettingToggleRow(
                label = "Require validation before done",
                checked = planningDraft.requireValidationBeforeDone,
                enabled = !isLoading,
                onCheckedChange = onRequireValidationChange
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onSensitivityChange(ComplexitySensitivity.LOW) },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) { Text("LOW") }
                OutlinedButton(
                    onClick = { onSensitivityChange(ComplexitySensitivity.MEDIUM) },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) { Text("MEDIUM") }
                OutlinedButton(
                    onClick = { onSensitivityChange(ComplexitySensitivity.HIGH) },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) { Text("HIGH") }
            }
        }

        item {
            Text(
                text = "Current sensitivity: ${planningDraft.complexitySensitivity}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
