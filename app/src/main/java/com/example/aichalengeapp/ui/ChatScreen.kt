package com.example.aichalengeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    val factsJson by viewModel.factsJson.collectAsState()
    val workingJson by viewModel.workingJson.collectAsState()
    val longTermJson by viewModel.longTermJson.collectAsState()

    var input by remember { mutableStateOf("") }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showMemorySheet by rememberSaveable { mutableStateOf(false) }

    var strategyMenuExpanded by remember { mutableStateOf(false) }
    var branchMenuExpanded by remember { mutableStateOf(false) }

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
                        onClick = { showMemorySheet = true },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Filled.Memory, contentDescription = "Memory")
                    }

                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = !isLoading && messages.isNotEmpty()
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

            // Strategy header
            /*Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
            }*/

            /*// Branching controls
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

            // Optional: show facts in FACTS mode (small + simple)
            if (strategy.type == ChatViewModel.StrategyTypeUi.FACTS && factsJson.isNotBlank()) {
                AssistChip(
                    onClick = { showMemorySheet = true },
                    label = { Text("Facts updated (tap to view memory)") },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }*/

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

            // Input row
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
                    placeholder = { Text("Type a message… or /work key=value") },
                    enabled = !isLoading,
                    maxLines = 4
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
                ) { Text("Send") }
            }
        }

        // Reset dialog
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Reset?") },
                text = { Text("Choose what to clear.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearDialog = false
                            viewModel.resetAll()
                        }
                    ) { Text("Reset ALL") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                showClearDialog = false
                                viewModel.resetShortTermOnly()
                            }
                        ) { Text("Short-term only") }

                        TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
                    }
                }
            )
        }

        // Memory bottom sheet
        if (showMemorySheet) {
            MemoryBottomSheet(
                workingJson = workingJson,
                longTermJson = longTermJson,
                onClose = { showMemorySheet = false },
                onClearWorking = { viewModel.clearWorkingOnly() },
                onClearLong = { viewModel.clearLongTermOnly() },
                onResetAll = { viewModel.resetAll() },
                onInsertCommand = { cmd ->
                    input = cmd
                    showMemorySheet = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryBottomSheet(
    workingJson: String,
    longTermJson: String,
    onClose: () -> Unit,
    onClearWorking: () -> Unit,
    onClearLong: () -> Unit,
    onResetAll: () -> Unit,
    onInsertCommand: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState
    ) {
        var tab by rememberSaveable { mutableIntStateOf(0) } // 0=Short help, 1=Working, 2=Long

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Memory layers", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("How to use") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Working") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Long-term") })
            }

            Spacer(Modifier.height(12.dp))

            when (tab) {
                0 -> {
                    Text(
                        "Use commands прямо в чате — это и есть явный контроль, что куда сохраняется:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    CommandRow("/work key=value") { onInsertCommand("/work key=value") }
                    CommandRow("/profile key=value") { onInsertCommand("/profile key=value") }
                    CommandRow("/showmem") { onInsertCommand("/showmem") }
                    CommandRow("/forget work") { onInsertCommand("/forget work") }
                    CommandRow("/forget profile") { onInsertCommand("/forget profile") }
                    CommandRow("/forget all") { onInsertCommand("/forget all") }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onClearWorking) { Text("Clear Working") }
                        OutlinedButton(onClick = onClearLong) { Text("Clear Long") }
                        Button(onClick = onResetAll) { Text("Reset ALL") }
                    }
                }

                1 -> {
                    MemoryJsonBlock(
                        title = "WORKING (task state)",
                        json = workingJson
                    ) {
                        clipboard.setText(AnnotatedString(workingJson))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onClearWorking) { Text("Clear Working") }
                        OutlinedButton(onClick = { onInsertCommand("/work goal=...") }) { Text("Insert /work") }
                    }
                }

                2 -> {
                    MemoryJsonBlock(
                        title = "LONG-TERM (profile/preferences)",
                        json = longTermJson
                    ) {
                        clipboard.setText(AnnotatedString(longTermJson))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onClearLong) { Text("Clear Long-term") }
                        OutlinedButton(onClick = { onInsertCommand("/profile preference=...") }) { Text("Insert /profile") }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CommandRow(cmd: String, onInsert: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(cmd, modifier = Modifier.weight(1f))
        TextButton(onClick = onInsert) { Text("Insert") }
    }
}

@Composable
private fun MemoryJsonBlock(
    title: String,
    json: String,
    onCopy: () -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))

    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = if (json.isBlank()) "— empty —" else json,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
        }
    }
}
