package com.example.aichalengeapp.repo

interface LlmSettingsStore {
    suspend fun load(): LlmSettings
    suspend fun saveProvider(provider: LlmProvider)
    suspend fun saveLocalBaseUrl(baseUrl: String)
    suspend fun clear()
}
