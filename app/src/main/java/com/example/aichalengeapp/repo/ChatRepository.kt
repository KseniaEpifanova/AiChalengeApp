package com.example.aichalengeapp.repo

import com.example.aichalengeapp.data.DsChatRequest
import com.example.aichalengeapp.data.DsMessage
import jakarta.inject.Inject

class ChatRepository @Inject constructor(
    private val api: ChatApi
) {
    suspend fun ask(
        userText: String,
        temperature: Double,
        maxTokens: Int
    ): String {
        val req = DsChatRequest(
            messages = listOf(
                DsMessage("system", "You are a helpful assistant."),
                DsMessage("user", userText)
            ),
            temperature = temperature,
            max_tokens = maxTokens
        )
        return api.chat(req).choices.first().message.content
    }

}