package com.example.aichalengeapp.repo

interface LocalLlmRepository {
    suspend fun send(prompt: String): String
}
