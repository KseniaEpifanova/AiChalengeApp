package com.example.aichalengeapp.data

data class DsMessage(
    val role: String,
    val content: String
)

data class DsChatRequest(
    val model: String = "deepseek-chat",
    val messages: List<DsMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int? = null,
    val stop: List<String>? = null
)

data class DsChatResponse(
    val choices: List<Choice>
) {
    data class Choice(val message: DsMessage)
}
