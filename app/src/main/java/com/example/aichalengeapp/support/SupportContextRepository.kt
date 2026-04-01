package com.example.aichalengeapp.support

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

interface SupportContextRepository {
    suspend fun loadContext(question: String): SupportUserContext?
}

@Singleton
class JsonSupportContextRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SupportContextRepository {

    private val cachedContext by lazy(LazyThreadSafetyMode.NONE) { loadSupportContext() }

    override suspend fun loadContext(question: String): SupportUserContext? = cachedContext

    private fun loadSupportContext(): SupportUserContext? {
        val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val json = JSONObject(raw)
        return SupportUserContext(
            userId = json.optString("userId"),
            userName = json.optString("userName"),
            plan = json.optString("plan"),
            appVersion = json.optString("appVersion"),
            recentTicketId = json.optString("recentTicketId").ifBlank { null },
            recentTicketSummary = json.optString("recentTicketSummary").ifBlank { null },
            recentTicketStatus = json.optString("recentTicketStatus").ifBlank { null }
        )
    }

    private companion object {
        private const val ASSET_NAME = "support_context.json"
    }
}
