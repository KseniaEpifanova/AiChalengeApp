package com.example.aichalengeapp.agent.memory

interface LongTermMemoryStore {
    suspend fun loadJson(): String
    suspend fun saveJson(json: String)
    suspend fun clear()
}
