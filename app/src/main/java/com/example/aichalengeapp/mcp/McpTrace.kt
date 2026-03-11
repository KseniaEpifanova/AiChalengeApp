package com.example.aichalengeapp.mcp

import android.util.Log
import com.example.aichalengeapp.BuildConfig

object McpTrace {
    private const val TAG = "McpTrace"

    fun d(vararg fields: Pair<String, Any?>) {
        if (!BuildConfig.DEBUG) return
        val rendered = fields.joinToString(" | ") { (k, v) -> "$k=${format(v)}" }
        runCatching { Log.d(TAG, "McpTrace | $rendered") }
    }

    private fun format(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${value.replace('\n', ' ').take(180)}\""
            else -> value.toString()
        }
    }
}
