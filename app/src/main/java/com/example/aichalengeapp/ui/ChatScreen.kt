package com.example.aichalengeapp.ui

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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

    var input by remember { mutableStateOf("") }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    var strategyMenuExpanded by remember { mutableStateOf(false) }
    var branchMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val factsJson by viewModel.factsJson.collectAsState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Assistant") },
                actions = {
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = !isLoading && messages.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "New chat")
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

            // Strategy header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { strategyMenuExpanded = true },
                    enabled = !isLoading
                ) {
                    Text("Strategy: ${strategy.type}")
                }

                DropdownMenu(
                    expanded = strategyMenuExpanded,
                    onDismissRequest = { strategyMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("SLIDING") },
                        onClick = {
                            strategyMenuExpanded = false
                            viewModel.setStrategyType(ChatViewModel.StrategyTypeUi.SLIDING)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("FACTS") },
                        onClick = {
                            strategyMenuExpanded = false
                            viewModel.setStrategyType(ChatViewModel.StrategyTypeUi.FACTS)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("BRANCHING") },
                        onClick = {
                            strategyMenuExpanded = false
                            viewModel.setStrategyType(ChatViewModel.StrategyTypeUi.BRANCHING)
                        }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text("Tail: ${strategy.tailN}")

                Spacer(modifier = Modifier.width(8.dp))

                Slider(
                    value = strategy.tailN.toFloat(),
                    onValueChange = { viewModel.setTailN(it.toInt()) },
                    valueRange = 4f..60f,
                    steps = 30,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                )
            }

            // Branching controls (only visible in branching mode)
            if (strategy.type == ChatViewModel.StrategyTypeUi.BRANCHING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { viewModel.setCheckpoint() },
                        enabled = !isLoading
                    ) { Text("Set checkpoint") }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.createBranch("A") },
                        enabled = !isLoading
                    ) { Text("Create A") }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.createBranch("B") },
                        enabled = !isLoading
                    ) { Text("Create B") }

                    Spacer(modifier = Modifier.width(8.dp))

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

            if (strategy.type == ChatViewModel.StrategyTypeUi.FACTS) {
                FactsPanel(
                    factsJson = factsJson,
                    enabled = !isLoading
                )
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

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (input.isNotBlank()) {
                            viewModel.send(input)
                            input = ""
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text("Send")
                }
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Start a new chat?") },
                text = { Text("This will clear the conversation history on this device.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearDialog = false
                            viewModel.resetChat()
                        }
                    ) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}
