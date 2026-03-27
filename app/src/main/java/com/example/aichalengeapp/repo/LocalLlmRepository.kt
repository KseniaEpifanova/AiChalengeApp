package com.example.aichalengeapp.repo

data class LocalGenerationConfig(
    val temperature: Double,
    val maxOutputTokens: Int
)

interface LocalLlmRepository {
    suspend fun send(prompt: String, config: LocalGenerationConfig): String
}
