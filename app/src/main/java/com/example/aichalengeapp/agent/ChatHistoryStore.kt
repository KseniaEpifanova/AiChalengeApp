package com.example.aichalengeapp.agent

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.chatDataStore by preferencesDataStore(name = "chat_agent_store")

class ChatHistoryStore @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val KEY_HISTORY = stringPreferencesKey("history_json")

    suspend fun save(history: List<AgentMessage>) {
        val json = encode(history)
        appContext.chatDataStore.edit { prefs ->
            prefs[KEY_HISTORY] = json
        }
    }

    suspend fun load(): List<AgentMessage> {
        val prefs = appContext.chatDataStore.data.first()
        val json = prefs[KEY_HISTORY].orEmpty()
        if (json.isBlank()) return emptyList()
        return runCatching { decode(json) }.getOrElse { emptyList() }
    }

    suspend fun clear() {
        appContext.chatDataStore.edit { prefs ->
            prefs.remove(KEY_HISTORY)
        }
    }

    private fun encode(history: List<AgentMessage>): String {
        val arr = JSONArray()
        history.forEach { msg ->
            val obj = JSONObject()
            obj.put("role", msg.role.name)
            obj.put("content", msg.content)
            obj.put("ts", msg.timestampMs)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decode(json: String): List<AgentMessage> {
        val arr = JSONArray(json)
        val result = ArrayList<AgentMessage>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val role = AgentRole.valueOf(obj.getString("role"))
            val content = obj.getString("content")
            val ts = obj.optLong("ts", System.currentTimeMillis())
            result.add(AgentMessage(role = role, content = content, timestampMs = ts))
        }
        return result
    }
}