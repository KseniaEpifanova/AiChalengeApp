package com.example.aichalengeapp.agent.profile

data class UserProfile(
    val style: String = "",
    val format: String = "",
    val constraints: String = ""
) {
    fun isEmpty(): Boolean = style.isBlank() && format.isBlank() && constraints.isBlank()

    fun withDefaultsIfBlank(): UserProfile = copy(
        style = style.ifBlank { "Friendly, supportive, calm" },
        format = format.ifBlank { "Short and structured. Use bullets when helpful." },
        constraints = constraints.ifBlank { "No special constraints." }
    )
}
