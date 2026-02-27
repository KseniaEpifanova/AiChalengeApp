package com.example.aichalengeapp.agent.memory

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.data.MemorySnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

private val Context.chatMemoryDataStore by preferencesDataStore(name = "chat_memory_store")

class DataStoreChatMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) : ChatMemoryStore {

    private val KEY_HISTORY = stringPreferencesKey("history_json")
    private val KEY_SUMMARY = stringPreferencesKey("summary_text")
    private val KEY_SUMMARIZED_COUNT = intPreferencesKey("summarized_count")

    override suspend fun load(): MemorySnapshot {
        val prefs = context.chatMemoryDataStore.data.first()
        val historyJson = prefs[KEY_HISTORY].orEmpty()
        val summary = prefs[KEY_SUMMARY].orEmpty()
        val summarizedCount = prefs[KEY_SUMMARIZED_COUNT] ?: 0

        val messages = if (historyJson.isBlank()) emptyList() else runCatching { decode(historyJson) }.getOrElse { emptyList() }

        return MemorySnapshot(
            summary = summary,
            messages = messages,
            summarizedCount = summarizedCount
        )
    }

    override suspend fun save(snapshot: MemorySnapshot) {
        context.chatMemoryDataStore.edit { prefs ->
            prefs[KEY_HISTORY] = encode(snapshot.messages)
            prefs[KEY_SUMMARY] = snapshot.summary
            prefs[KEY_SUMMARIZED_COUNT] = snapshot.summarizedCount
        }
    }

    override suspend fun clear() {
        context.chatMemoryDataStore.edit { prefs ->
            prefs.remove(KEY_HISTORY)
            prefs.remove(KEY_SUMMARY)
            prefs.remove(KEY_SUMMARIZED_COUNT)
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
        val res = ArrayList<AgentMessage>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val role = AgentRole.valueOf(obj.getString("role"))
            val content = obj.getString("content")
            val ts = obj.optLong("ts", System.currentTimeMillis())
            res.add(AgentMessage(role, content, ts))
        }
        return res
    }
}
