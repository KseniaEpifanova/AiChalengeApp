package com.example.aichalengeapp.repo

interface EmbeddingRepository {
    suspend fun embed(text: String, dimensions: Int? = null): FloatArray?
}
