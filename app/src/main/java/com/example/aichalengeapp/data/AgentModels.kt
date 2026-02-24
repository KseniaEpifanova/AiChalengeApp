package com.example.aichalengeapp.data

enum class AgentRole { SYSTEM, USER, ASSISTANT }

data class AgentMessage(
    val role: AgentRole,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis()
)