package com.example.aichalengeapp.data

data class LlmUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

data class LlmResult(
    val text: String,
    val usage: LlmUsage? = null
)