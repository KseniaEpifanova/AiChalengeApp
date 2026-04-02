package com.example.aichalengeapp.support

data class SupportKnowledgeChunk(
    val source: String,
    val title: String,
    val text: String,
    val score: Int
)

data class SupportUserContext(
    val userId: String,
    val userName: String,
    val plan: String,
    val appVersion: String,
    val recentTicketId: String?,
    val recentTicketSummary: String?,
    val recentTicketStatus: String?
)

data class SupportAnswer(
    val text: String,
    val sources: List<String>,
    val contextSummary: String?
)
