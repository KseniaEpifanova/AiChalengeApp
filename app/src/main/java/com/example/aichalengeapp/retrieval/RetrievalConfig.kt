package com.example.aichalengeapp.retrieval

data class RetrievalConfig(
    val topKBefore: Int,
    val topKAfter: Int,
    val similarityThreshold: Double?,
    val rewriteEnabled: Boolean,
    val rerankEnabled: Boolean
) {
    companion object {
        fun forMode(mode: RetrievalMode): RetrievalConfig {
            return when (mode) {
                RetrievalMode.BASELINE -> RetrievalConfig(
                    topKBefore = 8,
                    topKAfter = 3,
                    similarityThreshold = null,
                    rewriteEnabled = false,
                    rerankEnabled = false
                )
                RetrievalMode.IMPROVED -> RetrievalConfig(
                    topKBefore = 8,
                    topKAfter = 3,
                    similarityThreshold = 0.28,
                    rewriteEnabled = true,
                    rerankEnabled = true
                )
            }
        }
    }
}
