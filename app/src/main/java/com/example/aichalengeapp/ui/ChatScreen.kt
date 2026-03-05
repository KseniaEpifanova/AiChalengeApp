package com.example.aichalengeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aichalengeapp.vm.ChatViewModel

private enum class MainDestination {
    CHAT,
    PROFILE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val strategy by viewModel.strategy.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val profileDirty by viewModel.profileDirty.collectAsStateWithLifecycle()
    val taskState by viewModel.taskState.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showStrategyDialog by rememberSaveable { mutableStateOf(false) }
    var showTaskDialog by rememberSaveable { mutableStateOf(false) }
    var taskGoalInput by rememberSaveable { mutableStateOf("") }
    var settingsMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var branchMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(MainDestination.CHAT) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (destination == MainDestination.CHAT) "AI Assistant" else "Profile")
                },
                navigationIcon = {
                    if (destination == MainDestination.PROFILE) {
                        IconButton(onClick = { destination = MainDestination.CHAT }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (destination == MainDestination.CHAT) {
                        IconButton(onClick = { settingsMenuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = settingsMenuExpanded,
                            onDismissRequest = { settingsMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("User Profile") },
                                onClick = {
                                    settingsMenuExpanded = false
                                    destination = MainDestination.PROFILE
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Strategy Mode") },
                                onClick = {
                                    settingsMenuExpanded = false
                                    showStrategyDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Start Task Mode") },
                                onClick = {
                                    settingsMenuExpanded = false
                                    taskGoalInput = ""
                                    showTaskDialog = true
                                }
                            )
                        }

                        IconButton(onClick = { showClearDialog = true }, enabled = !isLoading) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Reset")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (destination == MainDestination.PROFILE) {
            ProfileScreen(
                profile = profile,
                profileDirty = profileDirty,
                isLoading = isLoading,
                onStyleChange = viewModel::updateProfileStyle,
                onFormatChange = viewModel::updateProfileFormat,
                onConstraintsChange = viewModel::updateProfileConstraints,
                onSave = viewModel::saveProfile,
                onClear = viewModel::clearProfile,
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (taskState != null) {
                TaskCard(
                    taskState = taskState!!,
                    isLoading = isLoading,
                    onNextStep = viewModel::nextTaskStep,
                    onPause = viewModel::pauseTask,
                    onResume = viewModel::resumeTask,
                    onStop = viewModel::stopTask
                )
            }

            if (strategy.type == ChatViewModel.StrategyTypeUi.BRANCHING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = viewModel::setCheckpoint, enabled = !isLoading) {
                        Text("Set checkpoint")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.createBranch("A") }, enabled = !isLoading) {
                        Text("Create A")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.createBranch("B") }, enabled = !isLoading) {
                        Text("Create B")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { branchMenuExpanded = true },
                        enabled = !isLoading && strategy.branches.isNotEmpty()
                    ) {
                        Text("Branch: ${strategy.activeBranchId ?: "—"}")
                    }
                    DropdownMenu(
                        expanded = branchMenuExpanded,
                        onDismissRequest = { branchMenuExpanded = false }
                    ) {
                        strategy.branches.forEach { id ->
                            DropdownMenuItem(
                                text = { Text(id) },
                                onClick = {
                                    branchMenuExpanded = false
                                    viewModel.switchBranch(id)
                                }
                            )
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        onToggleExpand = viewModel::toggleExpand
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    enabled = !isLoading
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (input.isNotBlank()) {
                            viewModel.send(input)
                            input = ""
                        }
                    },
                    enabled = !isLoading
                ) { Text("Send") }
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Reset?") },
                text = { Text("This will clear chat and memories.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearDialog = false
                            viewModel.resetAll()
                        }
                    ) { Text("Reset all") }
                },
                dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
            )
        }

        if (showStrategyDialog) {
            AlertDialog(
                onDismissRequest = { showStrategyDialog = false },
                title = { Text("Select Strategy Mode") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.setStrategyType(ChatViewModel.StrategyTypeUi.SLIDING)
                                showStrategyDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Sliding") }
                        OutlinedButton(
                            onClick = {
                                viewModel.setStrategyType(ChatViewModel.StrategyTypeUi.FACTS)
                                showStrategyDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Facts") }
                        OutlinedButton(
                            onClick = {
                                viewModel.setStrategyType(ChatViewModel.StrategyTypeUi.BRANCHING)
                                showStrategyDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Branching") }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showStrategyDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showTaskDialog) {
            AlertDialog(
                onDismissRequest = { showTaskDialog = false },
                title = { Text("Start Task Mode") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Task mode is optional and can be stopped anytime.")
                        TextField(
                            value = taskGoalInput,
                            onValueChange = { taskGoalInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Enter task goal") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.startTask(taskGoalInput)
                            showTaskDialog = false
                        }
                    ) { Text("Start") }
                },
                dismissButton = {
                    TextButton(onClick = { showTaskDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
