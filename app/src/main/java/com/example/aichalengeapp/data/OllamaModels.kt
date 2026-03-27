package com.example.aichalengeapp.data

data class OllamaRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

data class OllamaOptions(
    val temperature: Double? = null,
    val num_predict: Int? = null
)

data class OllamaResponse(
    val response: String = ""
)
