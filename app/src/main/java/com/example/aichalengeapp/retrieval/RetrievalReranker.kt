package com.example.aichalengeapp.retrieval

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrievalReranker @Inject constructor() {
    fun rerank(query: String, chunks: List<RetrievedChunk>, enabled: Boolean): List<RetrievedChunk> {
        if (!enabled) return chunks

        return chunks.map { chunk ->
            val evidence = CodeEntityMatchScorer.evaluate(
                query = query,
                titleOrFile = chunk.titleOrFile,
                section = chunk.section,
                text = chunk.text
            )
            chunk.copy(
                finalScore = chunk.similarity + evidence.boost,
                boostReasons = evidence.reasons
            )
        }.sortedByDescending { it.finalScore }
    }
}
