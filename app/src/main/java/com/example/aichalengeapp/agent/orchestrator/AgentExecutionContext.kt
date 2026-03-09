package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.context.StrategyConfig
import com.example.aichalengeapp.agent.guard.InvariantsProfile
import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.task.TaskState

data class AgentExecutionContext(
    val activeProfile: AssistantProfile,
    val invariants: InvariantsProfile,
    val requestKind: RequestKind,
    val classificationReason: String,
    val taskState: TaskState?,
    val strategyConfig: StrategyConfig,
    val longTermJson: String,
    val workingJson: String
)
