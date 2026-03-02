package com.example.aichalengeapp.agent.memory

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.agent.context.AgentMemoryState
import com.example.aichalengeapp.agent.context.BranchData
import com.example.aichalengeapp.agent.context.BranchingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

private val Context.agentMemoryDataStore by preferencesDataStore(name = "agent_memory")

class DataStoreAgentMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) : AgentMemoryStore {

    private val KEY_STATE = stringPreferencesKey("agent_memory_state_json")

    override suspend fun load(): AgentMemoryState {
        val prefs = context.agentMemoryDataStore.data.first()
        val json = prefs[KEY_STATE].orEmpty()
        if (json.isBlank()) return AgentMemoryState()

        return runCatching { decode(json) }.getOrElse { AgentMemoryState() }
    }

    override suspend fun save(state: AgentMemoryState) {
        context.agentMemoryDataStore.edit { prefs ->
            prefs[KEY_STATE] = encode(state)
        }
    }

    override suspend fun clear() {
        context.agentMemoryDataStore.edit { prefs -> prefs.remove(KEY_STATE) }
    }

    private fun encode(state: AgentMemoryState): String {
        val root = JSONObject()

        // history
        val historyArr = JSONArray()
        state.history.forEach { historyArr.put(encodeMessage(it)) }
        root.put("history", historyArr)

        // facts
        root.put("factsJson", state.factsJson)

        // branching
        val br = JSONObject()
        br.put("checkpointIndex", state.branching.checkpointIndex?.toLong() ?: JSONObject.NULL)
        br.put("activeBranchId", state.branching.activeBranchId ?: JSONObject.NULL)
        val baseArr = JSONArray()
        br.put("baseMessages", baseArr)

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
            for (i in 0 until historyArr.length()) {
                add(decodeMessage(historyArr.getJSONObject(i)))
            }
        }

        val factsJson = root.optString("factsJson", "")

        val brObj = root.optJSONObject("branching") ?: JSONObject()

        val checkpointIndex = if (brObj.isNull("checkpointIndex")) null else brObj.optLong("checkpointIndex").toInt()
        val activeBranchId = if (brObj.isNull("activeBranchId")) null else brObj.optString("activeBranchId")
        val baseArr = brObj.optJSONArray("baseMessages") ?: JSONArray()
        val baseMessages = buildList {
            for (i in 0 until baseArr.length()) {
                add(decodeMessage(baseArr.getJSONObject(i)))
            }
        }
        val branches = mutableMapOf<String, BranchData>()
        val branchesObj = brObj.optJSONObject("branches") ?: JSONObject()
        val keys = branchesObj.keys()
        while (keys.hasNext()) {
            val branchId = keys.next()
            val arr = branchesObj.optJSONArray(branchId) ?: JSONArray()
            val msgs = buildList {
                for (i in 0 until arr.length()) {
                    add(decodeMessage(arr.getJSONObject(i)))
                }
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

    private fun encodeMessage(m: AgentMessage): JSONObject {
        val o = JSONObject()
        o.put("role", m.role.name)
        o.put("content", m.content)
        o.put("ts", m.timestampMs)
        return o
    }

    private fun decodeMessage(o: JSONObject): AgentMessage {
        val role = AgentRole.valueOf(o.getString("role"))
        val content = o.getString("content")
        val ts = o.optLong("ts", System.currentTimeMillis())
        return AgentMessage(role, content, ts)
    }
}
