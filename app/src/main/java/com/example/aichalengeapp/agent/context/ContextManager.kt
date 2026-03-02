package com.example.aichalengeapp.agent.context

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.MemorySnapshot

data class OldContextPlan(
    val summary: String,
    val tailMessages: List<AgentMessage>,
    val updatedSnapshot: MemorySnapshot?
)

interface ContextManager {
    suspend fun prepare(snapshot: MemorySnapshot): OldContextPlan
}
