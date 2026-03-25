package com.example.aichalengeapp.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.aichalengeapp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private val Context.llmSettingsDataStore by preferencesDataStore(name = "llm_settings")

class DataStoreLlmSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmSettingsStore {

    private val providerKey = stringPreferencesKey("provider")
    private val localBaseUrlKey = stringPreferencesKey("local_base_url")

    override suspend fun load(): LlmSettings {
        val prefs = context.llmSettingsDataStore.data.first()
        val provider = runCatching {
            LlmProvider.valueOf(prefs[providerKey].orEmpty().ifBlank { LlmProvider.REMOTE.name })
        }.getOrDefault(LlmProvider.REMOTE)
        val localBaseUrl = prefs[localBaseUrlKey]
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: BuildConfig.OLLAMA_BASE_URL.trim()

        return LlmSettings(
            provider = provider,
            localBaseUrl = localBaseUrl
        )
    }

    override suspend fun saveProvider(provider: LlmProvider) {
        context.llmSettingsDataStore.edit { prefs ->
            prefs[providerKey] = provider.name
        }
    }

    override suspend fun saveLocalBaseUrl(baseUrl: String) {
        context.llmSettingsDataStore.edit { prefs ->
            prefs[localBaseUrlKey] = baseUrl.trim()
        }
    }

    override suspend fun clear() {
        context.llmSettingsDataStore.edit { prefs ->
            prefs[providerKey] = LlmProvider.REMOTE.name
            prefs[localBaseUrlKey] = BuildConfig.OLLAMA_BASE_URL.trim()
        }
    }
}
