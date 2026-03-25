package com.example.aichalengeapp.repo

import com.example.aichalengeapp.BuildConfig
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.data.DsChatRequest
import com.example.aichalengeapp.data.DsMessage
import com.example.aichalengeapp.data.LlmResult
import com.example.aichalengeapp.data.LlmUsage
import com.example.aichalengeapp.mcp.McpTrace
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val api: ChatApi,
    private val localLlmRepository: LocalLlmRepository,
    private val settingsStore: LlmSettingsStore
) : ChatRepository {

    override suspend fun ask(messages: List<AgentMessage>, maxOutputTokens: Int?): LlmResult {
        val settings = settingsStore.load()
        val provider = settings.provider
        McpTrace.d(
            "event" to "llm_request_start",
            "provider" to provider.name,
            "messages" to messages.size,
            "maxOutputTokens" to maxOutputTokens,
            "localBaseUrl" to if (provider == LlmProvider.LOCAL) settings.localBaseUrl else ""
        )

        return try {
            val result = when (provider) {
                LlmProvider.REMOTE -> askRemote(messages, maxOutputTokens)
                LlmProvider.LOCAL -> askLocal(messages)
            }
            McpTrace.d(
                "event" to "llm_response_received",
                "provider" to provider.name,
                "chars" to result.text.length
            )
            result
        } catch (t: Throwable) {
            McpTrace.d(
                "event" to "llm_error",
                "provider" to provider.name,
                "error" to (t.message ?: t::class.java.simpleName)
            )
            throw t
        }
    }

    private suspend fun askRemote(messages: List<AgentMessage>, maxOutputTokens: Int?): LlmResult {
        if (BuildConfig.DEEPSEEK_API_KEY.isBlank()) {
            throw IllegalStateException(
                "DEEPSEEK_API_KEY is blank. Add it to Gradle properties and rebuild the app."
            )
        }
        val dsMessages = messages.map { it.toDs() }
        val resp = try {
            api.chat(
                DsChatRequest(
                    model = "deepseek-chat",
                    messages = dsMessages,
                    temperature = 0.3,
                    max_tokens = maxOutputTokens
                )
            )
        } catch (e: UnknownHostException) {
            throw IOException(
                "Unable to resolve host. Check internet connection.",
                e
            )
        } catch (e: IllegalArgumentException) {
            throw IOException(
                "Invalid networking configuration.",
                e
            )
        }

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

    private suspend fun askLocal(messages: List<AgentMessage>): LlmResult {
        val prompt = buildOllamaPrompt(messages)
        val text = localLlmRepository.send(prompt)
        return LlmResult(text = text, usage = null)
    }

    private fun AgentMessage.toDs(): DsMessage {
        val role = when (this.role) {
            AgentRole.SYSTEM -> "system"
            AgentRole.USER -> "user"
            AgentRole.ASSISTANT -> "assistant"
        }
        return DsMessage(role, content)
    }

    private fun buildOllamaPrompt(messages: List<AgentMessage>): String {
        val body = messages.joinToString("\n\n") { message ->
            val role = when (message.role) {
                AgentRole.SYSTEM -> "SYSTEM"
                AgentRole.USER -> "USER"
                AgentRole.ASSISTANT -> "ASSISTANT"
            }
            "$role:\n${message.content.trim()}"
        }
        return "$body\n\nASSISTANT:\n"
    }
}
