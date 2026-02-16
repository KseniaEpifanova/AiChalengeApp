package com.example.aichalengeapp.data

data class MessageUi(
    val id: String,
    val text: String,
    val isUser: Boolean
)

data class ChatRequest(
    val message: String
)

data class ChatResponse(
    val reply: String
)