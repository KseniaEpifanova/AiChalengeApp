package com.example.aichalengeapp.agent.memory

interface WorkingMemoryStore {
    suspend fun loadJson(): String
    suspend fun saveJson(json: String)
    suspend fun clear()
}
