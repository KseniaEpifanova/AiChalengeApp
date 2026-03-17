package com.example.aichalengeapp.retrieval

import com.example.aichalengeapp.mcp.McpTrace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrievalPromptBuilder @Inject constructor() {
    fun build(userQuestion: String, chunks: List<RetrievedChunk>): String {
        val renderedChunks = chunks.joinToString("\n\n") { chunk ->
            buildString {
                append("SOURCE: ").append(chunk.source)
                append("\nFILE: ").append(chunk.titleOrFile)
                if (!chunk.section.isNullOrBlank()) {
                    append("\nSECTION: ").append(chunk.section)
                }
                append("\nCHUNK_ID: ").append(chunk.chunkId)
                append("\nSIMILARITY: ").append(String.format("%.4f", chunk.similarity))
                append("\nTEXT:\n").append(chunk.text)
            }
        }

        val prompt = """
            RETRIEVED PROJECT KNOWLEDGE
            The following context was retrieved from the local documentation/project index for the user's question:
            "$userQuestion"

            Use this context when answering. Prefer retrieved facts over guesses.
            If the retrieved context is incomplete, say what is known and what is missing.

            $renderedChunks
        """.trimIndent()

        McpTrace.d("event" to "retrieval_prompt_built", "chunks" to chunks.size)
        return prompt
    }
}
