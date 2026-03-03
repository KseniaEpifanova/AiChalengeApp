package com.example.aichalengeapp.agent.memory

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.aichalengeapp.agent.context.AgentMemoryState
import com.example.aichalengeapp.agent.context.BranchData
import com.example.aichalengeapp.agent.context.BranchingState
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

private val Context.agentMemoryDataStore by preferencesDataStore(name = "agent_memory")

class DataStoreAgentMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentMemoryStore {

    private val KEY_SHORT_TERM = stringPreferencesKey("short_term_json")

    override suspend fun load(): AgentMemoryState {
        val prefs = context.agentMemoryDataStore.data.first()
        val json = prefs[KEY_SHORT_TERM].orEmpty()
        if (json.isBlank()) return AgentMemoryState()
        return runCatching { decode(json) }.getOrElse { AgentMemoryState() }
    }

    override suspend fun save(state: AgentMemoryState) {
        context.agentMemoryDataStore.edit { prefs ->
            prefs[KEY_SHORT_TERM] = encode(state)
        }
    }

    override suspend fun clear() {
        context.agentMemoryDataStore.edit { prefs ->
            prefs[KEY_SHORT_TERM] = ""
        }
    }

    private fun encode(state: AgentMemoryState): String {
        val root = JSONObject()

        val historyArr = JSONArray()
        state.history.forEach { historyArr.put(encodeMessage(it)) }
        root.put("history", historyArr)

        root.put("factsJson", state.factsJson)

        val br = JSONObject()
        br.put("checkpointIndex", state.branching.checkpointIndex?.toLong() ?: JSONObject.NULL)
        br.put("activeBranchId", state.branching.activeBranchId ?: JSONObject.NULL)

        val branchesObj = JSONObject()
        state.branching.branches.forEach { (branchId, branchData) ->
            val arr = JSONArray()
            branchData.messages.forEach { arr.put(encodeMessage(it)) }
            branchesObj.put(branchId, arr)
        }
        br.put("branches", branchesObj)

        root.put("branching", br)
        return root.toString()
    }

    private fun decode(json: String): AgentMemoryState {
        val root = JSONObject(json)

        val historyArr = root.optJSONArray("history") ?: JSONArray()
        val history = buildList {
            for (i in 0 until historyArr.length()) add(decodeMessage(historyArr.getJSONObject(i)))
        }

        val factsJson = root.optString("factsJson", "")

        val brObj = root.optJSONObject("branching") ?: JSONObject()
        val checkpointIndex =
            if (brObj.isNull("checkpointIndex")) null else brObj.optLong("checkpointIndex").toInt()
        val activeBranchId =
            if (brObj.isNull("activeBranchId")) null else brObj.optString("activeBranchId")

        val branches = mutableMapOf<String, BranchData>()
        val branchesObj = brObj.optJSONObject("branches") ?: JSONObject()
        val keys = branchesObj.keys()
        while (keys.hasNext()) {
            val branchId = keys.next()
            val arr = branchesObj.optJSONArray(branchId) ?: JSONArray()
            val msgs = buildList {
                for (i in 0 until arr.length()) add(decodeMessage(arr.getJSONObject(i)))
            }
            branches[branchId] = BranchData(messages = msgs)
        }

        return AgentMemoryState(
            history = history,
            factsJson = factsJson,
            branching = BranchingState(
                checkpointIndex = checkpointIndex,
                branches = branches,
                activeBranchId = activeBranchId
            )
        )
    }

    private fun encodeMessage(m: AgentMessage): JSONObject =
        JSONObject().apply {
            put("role", m.role.name)
            put("content", m.content)
            put("ts", m.timestampMs)
        }

    private fun decodeMessage(o: JSONObject): AgentMessage {
        val role = AgentRole.valueOf(o.getString("role"))
        val content = o.getString("content")
        val ts = o.optLong("ts", System.currentTimeMillis())
        return AgentMessage(role, content, ts)
    }
}
