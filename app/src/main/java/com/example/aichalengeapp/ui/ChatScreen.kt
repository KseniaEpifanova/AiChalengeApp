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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aichalengeapp.vm.ChatViewModel
import kotlinx.coroutines.launch

private enum class MainDestination {
    CHAT,
    PROFILE,
    STRATEGY,
    INVARIANT_GUARD
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
    val invariantsProfile by viewModel.invariantsProfile.collectAsStateWithLifecycle()
    val invariantsDirty by viewModel.invariantsDirty.collectAsStateWithLifecycle()
    val guardEnabled by viewModel.guardEnabled.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showTaskDialog by rememberSaveable { mutableStateOf(false) }
    var taskGoalInput by rememberSaveable { mutableStateOf("") }
    var branchMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(MainDestination.CHAT) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "Assistant Settings",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Profile") },
                    selected = destination == MainDestination.PROFILE,
                    onClick = {
                        destination = MainDestination.PROFILE
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Strategy") },
                    selected = destination == MainDestination.STRATEGY,
                    onClick = {
                        destination = MainDestination.STRATEGY
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Invariant Guard") },
                    selected = destination == MainDestination.INVARIANT_GUARD,
                    onClick = {
                        destination = MainDestination.INVARIANT_GUARD
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val title = when (destination) {
                            MainDestination.CHAT -> "AI Assistant"
                            MainDestination.PROFILE -> "Profile"
                            MainDestination.STRATEGY -> "Strategy"
                            MainDestination.INVARIANT_GUARD -> "Invariant Guard"
                        }
                        Text(title)
                    },
                    navigationIcon = {
                        if (destination == MainDestination.CHAT) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open settings")
                            }
                        } else {
                            IconButton(onClick = { destination = MainDestination.CHAT }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (destination == MainDestination.CHAT) {
                            TextButton(onClick = {
                                taskGoalInput = ""
                                showTaskDialog = true
                            }) {
                                Text("Task")
                            }
                            IconButton(onClick = { showClearDialog = true }, enabled = !isLoading) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = "Reset")
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            when (destination) {
                MainDestination.PROFILE -> {
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
                }

                MainDestination.STRATEGY -> {
                    StrategyScreen(
                        currentStrategy = strategy.type,
                        isLoading = isLoading,
                        onSelectStrategy = viewModel::setStrategyType,
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                MainDestination.INVARIANT_GUARD -> {
                    GuardScreen(
                        profile = invariantsProfile,
                        guardEnabled = guardEnabled,
                        dirty = invariantsDirty,
                        isLoading = isLoading,
                        onGuardEnabledChange = viewModel::setGuardEnabled,
                        onTechDecisionsChange = viewModel::updateInvariantTechDecisions,
                        onBusinessRulesChange = viewModel::updateInvariantBusinessRules,
                        onSave = viewModel::saveInvariants,
                        onClear = viewModel::clearInvariants,
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                MainDestination.CHAT -> {
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
                                androidx.compose.material3.DropdownMenu(
                                    expanded = branchMenuExpanded,
                                    onDismissRequest = { branchMenuExpanded = false }
                                ) {
                                    strategy.branches.forEach { id ->
                                        androidx.compose.material3.DropdownMenuItem(
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
}
