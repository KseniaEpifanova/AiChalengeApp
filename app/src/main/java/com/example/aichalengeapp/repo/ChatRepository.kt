package com.example.aichalengeapp.repo

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.LlmResult

interface ChatRepository{
    suspend fun ask(messages: List<AgentMessage>, maxOutputTokens: Int? = null): LlmResult
}
