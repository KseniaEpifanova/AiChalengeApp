package com.example.aichalengeapp.mcp.pipeline

sealed class PipelineToolResponse {
    data class Success(
        val query: String,
        val matchedCount: Int,
        val summary: String,
        val savedPath: String?,
        val saved: Boolean
    ) : PipelineToolResponse()

    data class NoMatches(
        val query: String,
        val message: String
    ) : PipelineToolResponse()

    data class Failure(
        val message: String
    ) : PipelineToolResponse()
}
