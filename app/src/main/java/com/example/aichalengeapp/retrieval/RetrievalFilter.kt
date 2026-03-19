package com.example.aichalengeapp.retrieval

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrievalFilter @Inject constructor() {
    fun apply(
        chunks: List<RetrievedChunk>,
        threshold: Double?,
        fallbackCount: Int
    ): List<RetrievedChunk> {
        if (threshold == null) return chunks

        val kept = chunks.filter { it.similarity >= threshold }
        if (kept.isNotEmpty()) return kept

        return emptyList()
    }
}
