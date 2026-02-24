package com.example.aichalengeapp.repo

import com.example.aichalengeapp.data.AgentMessage

interface ChatRepository{
    suspend fun ask(messages: List<AgentMessage>): String
}
