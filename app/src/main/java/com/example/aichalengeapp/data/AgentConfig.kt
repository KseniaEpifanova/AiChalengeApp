package com.example.aichalengeapp.data

data class AgentConfig(
    val systemPrompt: String = "You are a helpful assistant.",
    val maxTurnsInContext: Int = 16,
    val maxCharsPerMessage: Int = 4000
)
