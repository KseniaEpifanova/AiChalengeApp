package com.example.aichalengeapp.retrieval

interface QueryEmbeddingProvider {
    suspend fun embed(text: String, dimensions: Int? = null): FloatArray?
}
