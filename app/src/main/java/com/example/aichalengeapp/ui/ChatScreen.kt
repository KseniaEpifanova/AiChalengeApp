package com.example.aichalengeapp.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aichalengeapp.debug.TaskTrace
import com.example.aichalengeapp.vm.ChatViewModel
import com.example.aichalengeapp.vm.McpViewModel
import kotlinx.coroutines.launch

private enum class MainDestination {
    CHAT,
    PROFILES,
    INVARIANT_GUARD,
    SETTINGS,
    MCP_DEBUG
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    mcpViewModel: McpViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val strategy by viewModel.strategy.collectAsStateWithLifecycle()
    val ragEnabled by viewModel.ragEnabled.collectAsStateWithLifecycle()
    val retrievalMode by viewModel.retrievalMode.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val planningDraft by viewModel.planningDraft.collectAsStateWithLifecycle()
    val profileDirty by viewModel.profileDirty.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val taskState by viewModel.taskState.collectAsStateWithLifecycle()
    val invariantsProfile by viewModel.invariantsProfile.collectAsStateWithLifecycle()
    val invariantsDirty by viewModel.invariantsDirty.collectAsStateWithLifecycle()
    val guardActive by viewModel.guardActive.collectAsStateWithLifecycle()
    val mcpUiState by mcpViewModel.uiState.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showTaskDialog by rememberSaveable { mutableStateOf(false) }
    var taskGoalInput by rememberSaveable { mutableStateOf("") }
    var branchMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(MainDestination.CHAT) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val inputFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = destination != MainDestination.CHAT || drawerState.isOpen) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            destination = MainDestination.CHAT
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(taskState?.stage, taskState?.paused) {
        val visible = isTaskPanelVisible(taskState)
        TaskTrace.d(
            "event" to "ui_panel_render",
            "source" to "compose",
            "taskId" to TaskTrace.taskId(taskState),
            "uiStage" to taskState?.stage,
            "paused" to taskState?.paused,
            "panelVisible" to visible
        )
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
                    label = { Text("Chat") },
                    selected = destination == MainDestination.CHAT,
                    onClick = {
                        destination = MainDestination.CHAT
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Profiles") },
                    selected = destination == MainDestination.PROFILES,
                    onClick = {
                        destination = MainDestination.PROFILES
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
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = destination == MainDestination.SETTINGS,
                    onClick = {
                        destination = MainDestination.SETTINGS
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("MCP Debug") },
                    selected = destination == MainDestination.MCP_DEBUG,
                    onClick = {
                        destination = MainDestination.MCP_DEBUG
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
                            MainDestination.PROFILES -> "Profiles"
                            MainDestination.INVARIANT_GUARD -> "Invariant Guard"
                            MainDestination.SETTINGS -> "Settings"
                            MainDestination.MCP_DEBUG -> "MCP Debug"
                        }
                        Text(title)
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open menu")
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
                            TextButton(onClick = { showClearDialog = true }, enabled = !isLoading) {
                                Text("Clear chat")
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            when (destination) {
                MainDestination.PROFILES -> {
                    ProfilesScreen(
                        profiles = profiles,
                        profile = profile,
                        planningDraft = planningDraft,
                        profileDirty = profileDirty,
                        isLoading = isLoading,
                        onCreateProfile = viewModel::createProfile,
                        onStartEditingProfile = viewModel::startEditingProfile,
                        onStopEditingProfile = viewModel::stopEditingProfile,
                        onDeleteProfile = viewModel::deleteProfile,
                        onStyleChange = viewModel::updateProfileStyle,
                        onFormatChange = viewModel::updateProfileFormat,
                        onConstraintsChange = viewModel::updateProfileConstraints,
                        onSave = viewModel::saveProfile,
                        onClear = viewModel::clearProfile,
                        onAutoDetectChange = viewModel::updatePlanningAutoDetect,
                        onSensitivityChange = viewModel::updatePlanningSensitivity,
                        onRequirePlanApprovalChange = viewModel::updatePlanningRequirePlanApproval,
                        onAllowAutoContinueChange = viewModel::updatePlanningAutoContinueExecution,
                        onRequireValidationChange = viewModel::updatePlanningRequireValidation,
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                MainDestination.SETTINGS -> {
                    SettingsScreen(
                        currentStrategy = strategy.type,
                        ragEnabled = ragEnabled,
                        retrievalMode = retrievalMode,
                        isLoading = isLoading,
                        onSelectStrategy = viewModel::setStrategyType,
                        onRagEnabledChange = viewModel::setRagEnabled,
                        onRetrievalModeChange = viewModel::setRetrievalMode,
                        taskState = taskState,
                        onNextStep = viewModel::nextTaskStep,
                        onPause = viewModel::pauseTask,
                        onResume = viewModel::resumeTask,
                        onCancel = viewModel::cancelTask,
                        onResetAll = viewModel::resetAll,
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                MainDestination.INVARIANT_GUARD -> {
                    GuardScreen(
                        profile = invariantsProfile,
                        isGuardActive = guardActive,
                        dirty = invariantsDirty,
                        isLoading = isLoading,
                        onTechDecisionsChange = viewModel::updateInvariantTechDecisions,
                        onBusinessRulesChange = viewModel::updateInvariantBusinessRules,
                        onSave = viewModel::saveInvariants,
                        onClear = viewModel::clearInvariants,
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                MainDestination.MCP_DEBUG -> {
                    McpDebugScreen(
                        state = mcpUiState,
                        onConnectAndLoad = mcpViewModel::connectAndLoadTools,
                        onDisconnect = mcpViewModel::disconnect,
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                MainDestination.CHAT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isTaskPanelVisible(taskState)) {
                            TaskCard(
                                taskState = taskState!!,
                                isLoading = isLoading,
                                onNextStep = viewModel::nextTaskStep,
                                onPause = viewModel::pauseTask,
                                onResume = viewModel::resumeTask,
                                onCancel = viewModel::cancelTask
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

                        if (messages.isEmpty()) {
                            WelcomeView(
                                onStartChat = {
                                    focusManager.clearFocus(force = true)
                                    inputFocusRequester.requestFocus()
                                },
                                onPromptSelected = { prompt ->
                                    viewModel.send(prompt)
                                    input = ""
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
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
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(inputFocusRequester),
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
                    title = { Text("Clear chat?") },
                    text = { Text("This will clear only current chat messages and active task panel.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showClearDialog = false
                                viewModel.clearChatSession()
                            }
                        ) { Text("Clear chat") }
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
