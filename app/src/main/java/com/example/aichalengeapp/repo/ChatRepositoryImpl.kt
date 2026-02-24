package com.example.aichalengeapp.repo

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.data.DsChatRequest
import com.example.aichalengeapp.data.DsMessage
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val api: ChatApi
) : ChatRepository {

    override suspend fun ask(messages: List<AgentMessage>): String {
        val dsMessages = messages.map { it.toDsMessage() }

        val resp = api.chat(
            DsChatRequest(
                model = "deepseek-chat",
                messages = dsMessages,
                temperature = 0.3
            )
        )

        return resp.choices.firstOrNull()?.message?.content.orEmpty()
    }

    private fun AgentMessage.toDsMessage(): DsMessage {
        val role = when (this.role) {
            AgentRole.SYSTEM -> "system"
            AgentRole.USER -> "user"
            AgentRole.ASSISTANT -> "assistant"
        }
        return DsMessage(role, this.content)
    }
}
