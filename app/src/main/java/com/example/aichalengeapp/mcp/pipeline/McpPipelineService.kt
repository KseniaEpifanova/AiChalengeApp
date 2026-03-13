package com.example.aichalengeapp.mcp.pipeline

import com.example.aichalengeapp.mcp.McpRepository
import javax.inject.Inject
import javax.inject.Singleton

interface McpPipelineService {
    suspend fun runSearchSummaryPipeline(
        query: String,
        filename: String?,
        limit: Int?
    ): PipelineToolResponse
}

@Singleton
class McpPipelineServiceImpl @Inject constructor(
    private val mcpRepository: McpRepository
) : McpPipelineService {
    override suspend fun runSearchSummaryPipeline(
        query: String,
        filename: String?,
        limit: Int?
    ): PipelineToolResponse {
        return mcpRepository.callSearchSummaryPipelineTool(
            query = query,
            filename = filename,
            limit = limit
        )
    }
}
