package com.example.aichalengeapp.repo

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.data.DsChatRequest
import com.example.aichalengeapp.data.DsMessage
import com.example.aichalengeapp.data.LlmResult
import javax.inject.Inject
import com.example.aichalengeapp.data.LlmUsage

class ChatRepositoryImpl @Inject constructor(
    private val api: ChatApi
) : ChatRepository {

    override suspend fun ask(messages: List<AgentMessage>, maxOutputTokens: Int?): LlmResult {
        val dsMessages = messages.map { it.toDs() }

        val resp = api.chat(
            DsChatRequest(
                model = "deepseek-chat",
                messages = dsMessages,
                temperature = 0.3,
                max_tokens = maxOutputTokens
            )
        )

        val text = resp.choices.firstOrNull()?.message?.content.orEmpty()
        val usage = resp.usage?.let {
            LlmUsage(
                promptTokens = it.prompt_tokens,
                completionTokens = it.completion_tokens,
                totalTokens = it.total_tokens
            )
        }

        return LlmResult(text = text, usage = usage)
    }

    private fun AgentMessage.toDs(): DsMessage {
        val role = when (this.role) {
            AgentRole.SYSTEM -> "system"
            AgentRole.USER -> "user"
            AgentRole.ASSISTANT -> "assistant"
        }
        return DsMessage(role, content)
    }
}
