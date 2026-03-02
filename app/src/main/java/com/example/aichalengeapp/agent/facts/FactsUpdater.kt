package com.example.aichalengeapp.agent.facts

interface FactsUpdater {
    suspend fun updateFacts(existingFactsJson: String, userMessage: String): String
}
