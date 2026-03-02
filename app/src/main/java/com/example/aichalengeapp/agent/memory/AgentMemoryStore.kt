package com.example.aichalengeapp.agent.memory

import com.example.aichalengeapp.agent.context.AgentMemoryState

interface AgentMemoryStore {
    suspend fun load(): AgentMemoryState
    suspend fun save(state: AgentMemoryState)
    suspend fun clear()
}
