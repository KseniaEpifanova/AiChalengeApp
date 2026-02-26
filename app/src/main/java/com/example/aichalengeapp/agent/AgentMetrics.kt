package com.example.aichalengeapp.agent

data class TokenMetrics(
    val estimatedUserTokens: Int,
    val estimatedHistoryTokens: Int,
    val estimatedPromptTokens: Int,
    val actualPromptTokens: Int?,
    val actualCompletionTokens: Int?,
    val actualTotalTokens: Int?,
    val estimatedCostUsd: Double?
)

data class AgentReply(
    val text: String,
    val metrics: TokenMetrics
)

class ContextOverflowException(message: String) : IllegalStateException(message)
