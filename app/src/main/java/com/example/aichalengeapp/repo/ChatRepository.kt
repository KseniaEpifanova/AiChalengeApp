package com.example.aichalengeapp.repo

import com.example.aichalengeapp.data.DsChatRequest
import com.example.aichalengeapp.data.DsMessage
import jakarta.inject.Inject

class ChatRepository @Inject constructor(
    private val api: ChatApi
) {
    suspend fun ask(userText: String): String {
        val req = DsChatRequest(
            messages = listOf(
                DsMessage("system", "You are a helpful assistant."),
                DsMessage("user", userText)
            )
        )
        return api.chat(req).choices.first().message.content
    }

    suspend fun askControlled(text: String): String {
        val system = """
Return answer in JSON format:
{
  "summary": "1 sentence",
  "answer": "max 80 words"
}
End with <END>
""".trimIndent()

        val request = DsChatRequest(
            messages = listOf(
                DsMessage("system", system),
                DsMessage("user", text)
            ),
            max_tokens = 150,
            stop = listOf("<END>")
        )

        return api.chat(request).choices.first().message.content
    }
}