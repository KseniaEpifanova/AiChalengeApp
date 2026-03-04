package com.example.aichalengeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.aichalengeapp.vm.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val strategy by viewModel.strategy.collectAsState()

    val profile by viewModel.profile.collectAsState()
    val profileDirty by viewModel.profileDirty.collectAsState()

    var input by remember { mutableStateOf("") }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    var strategyMenuExpanded by remember { mutableStateOf(false) }
    var branchMenuExpanded by remember { mutableStateOf(false) }
    var profileExpanded by rememberSaveable { mutableStateOf(true) }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Assistant") },
                actions = {
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Reset")
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // -------- Profile block --------
            Surface(
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "User profile",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { profileExpanded = !profileExpanded }) {
                            Text(if (profileExpanded) "Hide" else "Edit")
                        }
                    }

                    if (profileExpanded) {
                        OutlinedTextField(
                            value = profile.style,
                            onValueChange = viewModel::updateProfileStyle,
                            label = { Text("Style") },
                            placeholder = { Text("Напр: очень коротко") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = profile.format,
                            onValueChange = viewModel::updateProfileFormat,
                            label = { Text("Format") },
                            placeholder = { Text("Напр: списком") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = profile.constraints,
                            onValueChange = viewModel::updateProfileConstraints,
                            label = { Text("Constraints") },
                            placeholder = { Text("Напр: без английских слов") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = viewModel::saveProfile,
                                enabled = profileDirty && !isLoading // ✅ FIX
                            ) { Text("Save") }

                            OutlinedButton(
                                onClick = viewModel::clearProfile,
                                enabled = !isLoading
                            ) { Text("Clear") }
                        }

                        if (!profileDirty) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Saved",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // -------- Strategy header--------
            /*Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { strategyMenuExpanded = true },
                    enabled = !isLoading
                ) { Text("Strategy: ${strategy.type}") }

                DropdownMenu(
                    expanded = strategyMenuExpanded,
                    onDismissRequest = { strategyMenuExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text("SLIDING") }, onClick = {
                        strategyMenuExpanded = false
                        viewModel.setStrategyType(ChatViewModel.StrategyTypeUi.SLIDING)
                    })
                    DropdownMenuItem(text = { Text("FACTS") }, onClick = {
                        strategyMenuExpanded = false
                        viewModel.setStrategyType(ChatViewModel.StrategyTypeUi.FACTS)
                    })
                    DropdownMenuItem(text = { Text("BRANCHING") }, onClick = {
                        strategyMenuExpanded = false
                        viewModel.setStrategyType(ChatViewModel.StrategyTypeUi.BRANCHING)
                    })
                }

                Spacer(Modifier.width(12.dp))
            }*/

            // -------- Branching controls --------
            if (strategy.type == ChatViewModel.StrategyTypeUi.BRANCHING) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
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

            // -------- Messages --------
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

            // -------- Input --------
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
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
    }
}
