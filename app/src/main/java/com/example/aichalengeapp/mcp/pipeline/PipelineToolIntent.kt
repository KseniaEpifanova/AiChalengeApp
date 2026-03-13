package com.example.aichalengeapp.mcp.pipeline

data class PipelineToolIntent(
    val query: String,
    val filename: String?,
    val limit: Int?
)
