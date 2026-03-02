package com.example.aichalengeapp.agent.context

import com.example.aichalengeapp.data.AgentMessage


data class AgentMemoryState(
    val history: List<AgentMessage> = emptyList(),

    // Sticky Facts (Key-Value memory) — пока строкой JSON
    val factsJson: String = "",

    // Branching — base + ветки
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
