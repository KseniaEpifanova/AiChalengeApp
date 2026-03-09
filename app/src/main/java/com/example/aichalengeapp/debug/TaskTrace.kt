package com.example.aichalengeapp.debug

import android.util.Log
import com.example.aichalengeapp.BuildConfig
import com.example.aichalengeapp.agent.task.TaskState
import java.util.Locale

data class TaskDebugSnapshot(
    val taskId: String,
    val stage: String?,
    val planApproved: Boolean?,
    val paused: Boolean?,
    val panelVisible: Boolean?
)

object TaskTrace {
    private const val TAG = "TaskTrace"
    private const val PREVIEW_LIMIT = 96

    fun d(vararg fields: Pair<String, Any?>) {
        if (!BuildConfig.DEBUG) return
        val rendered = fields.joinToString(" | ") { (key, value) ->
            "$key=${format(value)}"
        }
        runCatching {
            Log.d(TAG, "TaskTrace | $rendered")
        }
    }

    fun preview(value: String, limit: Int = PREVIEW_LIMIT): String {
        val compact = value.replace('\n', ' ').trim()
        if (compact.length <= limit) return compact
        return compact.take(limit) + "..."
    }

    fun snapshot(state: TaskState?, panelVisible: Boolean? = null): TaskDebugSnapshot {
        return TaskDebugSnapshot(
            taskId = taskId(state),
            stage = state?.stage?.name,
            planApproved = state?.planApproved,
            paused = state?.paused,
            panelVisible = panelVisible
        )
    }

    fun taskId(state: TaskState?): String {
        if (state == null) return "none"
        val base = "${state.goal}|${state.steps.size}".hashCode().toUInt().toString(16).uppercase(Locale.US)
        return "t_$base"
    }

    private fun format(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${preview(value, 160)}\""
            else -> value.toString()
        }
    }
}
