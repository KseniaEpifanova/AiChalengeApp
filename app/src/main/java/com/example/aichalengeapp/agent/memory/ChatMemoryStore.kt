package com.example.aichalengeapp.agent.memory

import com.example.aichalengeapp.data.MemorySnapshot

interface ChatMemoryStore {
    suspend fun load(): MemorySnapshot
    suspend fun save(snapshot: MemorySnapshot)
    suspend fun clear()
}