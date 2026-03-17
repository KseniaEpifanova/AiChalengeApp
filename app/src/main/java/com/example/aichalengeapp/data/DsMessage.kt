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
    val choices: List<Choice>,
    val usage: Usage? = null
) {
    data class Choice(val message: DsMessage)
    data class Usage(
        val prompt_tokens: Int,
        val completion_tokens: Int,
        val total_tokens: Int
    )
}

data class DsEmbeddingRequest(
    val model: String = "deepseek-embedding",
    val input: String
)

data class DsEmbeddingResponse(
    val data: List<Item>
) {
    data class Item(
        val embedding: List<Float>
    )
}
