package com.example.aichalengeapp.agent.context

import com.example.aichalengeapp.data.AgentMessage

data class AgentMemoryState(
    val history: List<AgentMessage> = emptyList(),
    val factsJson: String = "",
    val branching: BranchingState = BranchingState()
)

data class BranchingState(
    val checkpointIndex: Int? = null,
    val branches: Map<String, BranchData> = emptyMap(),
    val activeBranchId: String? = null
)

data class BranchData(
    val messages: List<AgentMessage> = emptyList()
)
