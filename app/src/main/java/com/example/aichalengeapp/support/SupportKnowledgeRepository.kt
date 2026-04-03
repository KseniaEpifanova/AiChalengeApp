package com.example.aichalengeapp.support

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

interface SupportKnowledgeRepository {
    suspend fun retrieve(question: String): List<SupportKnowledgeChunk>
}

@Singleton
class JsonSupportKnowledgeRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SupportKnowledgeRepository {

    private val cachedEntries by lazy(LazyThreadSafetyMode.NONE                                                                       ) { loadEntries() }

    override suspend fun retrieve(question: String): List<SupportKnowledgeChunk> {
        val tokens = tokenize(question)
        if (tokens.isEmpty()) return emptyList()

        return cachedEntries.mapNotNull { entry ->
            val haystack = "${entry.title} ${entry.text}".lowercase()
            val score = tokens.count { token -> haystack.contains(token) }
            if (score == 0) {
                null
            } else {
                SupportKnowledgeChunk(
                    source = entry.source,
                    title = entry.title,
                    text = entry.text,
                    score = score
                )
            }
        }
            .sortedByDescending { it.score }
            .take(MAX_RESULTS)
    }

    private fun loadEntries(): List<KnowledgeEntry> {
        val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val json = JSONArray(raw)
        return buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                add(
                    KnowledgeEntry(
                        source = item.optString("source", "support-faq"),
                        title = item.optString("title"),
                        text = item.optString("text")
                    )
                )
            }
        }
    }

    private fun tokenize(question: String): Set<String> {
        return question.lowercase()
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.length >= 3 }
            .toSet()
    }

    private data class KnowledgeEntry(
        val source: String,
        val title: String,
        val text: String
    )

    private companion object {
        private const val ASSET_NAME = "support_faq.json"
        private const val MAX_RESULTS = 3
    }
}
