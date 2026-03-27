package com.example.aichalengeapp.repo

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.data.DsChatRequest
import com.example.aichalengeapp.data.DsChatResponse
import com.example.aichalengeapp.data.DsEmbeddingRequest
import com.example.aichalengeapp.data.DsEmbeddingResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ChatRepositoryImplTest {

    @Test
    fun `remote provider routes request to deepseek api`() = runBlocking {
        val chatApi = FakeChatApi()
        val local = FakeLocalLlmRepository()
        val store = FakeLlmSettingsStore(LlmSettings(provider = LlmProvider.REMOTE, localBaseUrl = "http://10.0.2.2:11434"))
        val repository = ChatRepositoryImpl(chatApi, local, store)

        val result = repository.ask(
            messages = listOf(
                AgentMessage(AgentRole.SYSTEM, "sys"),
                AgentMessage(AgentRole.USER, "hello")
            ),
            maxOutputTokens = 120
        )

        assertEquals("remote-answer", result.text)
        assertEquals(1, chatApi.chatCalls)
        assertEquals(0, local.calls)
        assertEquals(120, chatApi.lastRequest?.max_tokens)
        assertEquals("user", chatApi.lastRequest?.messages?.lastOrNull()?.role)
    }

    @Test
    fun `local provider routes request to ollama repository`() = runBlocking {
        val chatApi = FakeChatApi()
        val local = FakeLocalLlmRepository(response = "local-answer")
        val store = FakeLlmSettingsStore(LlmSettings(provider = LlmProvider.LOCAL, localBaseUrl = "http://10.0.2.2:11434"))
        val repository = ChatRepositoryImpl(chatApi, local, store)

        val result = repository.ask(
            messages = listOf(
                AgentMessage(AgentRole.SYSTEM, "system prompt"),
                AgentMessage(AgentRole.USER, "Explain ChatAgent")
            ),
            maxOutputTokens = 200
        )

        assertEquals("local-answer", result.text)
        assertNull(result.usage)
        assertEquals(0, chatApi.chatCalls)
        assertEquals(1, local.calls)
        assertTrue(local.lastPrompt.contains("LOCAL COMPACT MODE"))
        assertTrue(local.lastPrompt.contains("S:\nsystem prompt"))
        assertTrue(local.lastPrompt.contains("U:\nExplain ChatAgent"))
        assertTrue(local.lastPrompt.endsWith("A:\n"))
        assertEquals(0.2, local.lastConfig?.temperature ?: 0.0, 0.0001)
        assertEquals(320, local.lastConfig?.maxOutputTokens)
    }

    @Test
    fun `local grounded rag uses lower temperature`() = runBlocking {
        val chatApi = FakeChatApi()
        val local = FakeLocalLlmRepository(response = "local-answer")
        val store = FakeLlmSettingsStore(LlmSettings(provider = LlmProvider.LOCAL, localBaseUrl = "http://10.0.2.2:11434"))
        val repository = ChatRepositoryImpl(chatApi, local, store)

        repository.ask(
            messages = listOf(
                AgentMessage(AgentRole.SYSTEM, "RETRIEVED PROJECT KNOWLEDGE\nSOURCE: android-app"),
                AgentMessage(AgentRole.USER, "How does ChatAgent process messages?")
            ),
            maxOutputTokens = 320
        )

        assertEquals(0.1, local.lastConfig?.temperature ?: 0.0, 0.0001)
        assertEquals(320, local.lastConfig?.maxOutputTokens)
    }

    @Test
    fun `local unavailable returns controlled error`() = runBlocking {
        val chatApi = FakeChatApi()
        val local = FakeLocalLlmRepository(error = IOException("Ollama unavailable"))
        val store = FakeLlmSettingsStore(LlmSettings(provider = LlmProvider.LOCAL, localBaseUrl = "http://192.168.0.10:11434"))
        val repository = ChatRepositoryImpl(chatApi, local, store)

        val failure = runCatching {
            repository.ask(
                messages = listOf(AgentMessage(AgentRole.USER, "hello")),
                maxOutputTokens = null
            )
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals("Ollama unavailable", failure?.message)
        assertEquals(0, chatApi.chatCalls)
        assertEquals(1, local.calls)
    }

    private class FakeChatApi : ChatApi {
        var chatCalls: Int = 0
        var lastRequest: DsChatRequest? = null

        override suspend fun chat(body: DsChatRequest): DsChatResponse {
            chatCalls += 1
            lastRequest = body
            return DsChatResponse(
                choices = listOf(
                    DsChatResponse.Choice(
                        message = com.example.aichalengeapp.data.DsMessage(
                            role = "assistant",
                            content = "remote-answer"
                        )
                    )
                ),
                usage = DsChatResponse.Usage(
                    prompt_tokens = 10,
                    completion_tokens = 5,
                    total_tokens = 15
                )
            )
        }

        override suspend fun embeddings(body: DsEmbeddingRequest): DsEmbeddingResponse {
            return DsEmbeddingResponse(emptyList())
        }
    }

    private class FakeLocalLlmRepository(
        private val response: String = "",
        private val error: Throwable? = null
    ) : LocalLlmRepository {
        var calls: Int = 0
        var lastPrompt: String = ""
        var lastConfig: LocalGenerationConfig? = null

        override suspend fun send(prompt: String, config: LocalGenerationConfig): String {
            calls += 1
            lastPrompt = prompt
             lastConfig = config
            error?.let { throw it }
            return response
        }
    }

    private class FakeLlmSettingsStore(
        private var settings: LlmSettings
    ) : LlmSettingsStore {
        override suspend fun load(): LlmSettings = settings

        override suspend fun saveProvider(provider: LlmProvider) {
            settings = settings.copy(provider = provider)
        }

        override suspend fun saveLocalBaseUrl(baseUrl: String) {
            settings = settings.copy(localBaseUrl = baseUrl)
        }

        override suspend fun clear() {
            settings = LlmSettings()
        }
    }
}
