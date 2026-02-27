package com.example.aichalengeapp.data

data class MemorySnapshot(
    val summary: String,
    val messages: List<AgentMessage>,
    val summarizedCount: Int
)
