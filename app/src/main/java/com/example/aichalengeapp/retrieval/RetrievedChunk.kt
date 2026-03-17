package com.example.aichalengeapp.retrieval

data class RetrievedChunk(
    val source: String,
    val titleOrFile: String,
    val section: String?,
    val chunkId: String,
    val strategy: String?,
    val text: String,
    val similarity: Double
)
