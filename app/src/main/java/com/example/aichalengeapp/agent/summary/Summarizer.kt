package com.example.aichalengeapp.agent.summary

import com.example.aichalengeapp.data.AgentMessage

interface Summarizer {
    suspend fun summarizeChunk(existingSummary: String, chunk: List<AgentMessage>): String
}
