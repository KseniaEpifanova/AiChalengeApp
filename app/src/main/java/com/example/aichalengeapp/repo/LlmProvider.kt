package com.example.aichalengeapp.repo

import com.example.aichalengeapp.BuildConfig

enum class LlmProvider {
    REMOTE,
    LOCAL
}

data class LlmSettings(
    val provider: LlmProvider = LlmProvider.REMOTE,
    val localBaseUrl: String = BuildConfig.OLLAMA_BASE_URL.trim(),
    val localModel: String = DEFAULT_LOCAL_MODEL
) {
    companion object {
        const val DEFAULT_LOCAL_MODEL = "llama3.2:3b"
    }
}
