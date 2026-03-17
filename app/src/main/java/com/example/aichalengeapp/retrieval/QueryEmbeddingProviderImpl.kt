package com.example.aichalengeapp.retrieval

import com.example.aichalengeapp.repo.EmbeddingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueryEmbeddingProviderImpl @Inject constructor(
    private val embeddingRepository: EmbeddingRepository
) : QueryEmbeddingProvider {
    override suspend fun embed(text: String, dimensions: Int?): FloatArray? = embeddingRepository.embed(text, dimensions)
}
