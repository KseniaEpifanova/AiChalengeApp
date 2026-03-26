package com.example.aichalengeapp.retrieval

import com.example.aichalengeapp.mcp.McpTrace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrievalPromptBuilder @Inject constructor() {
    data class PromptOptions(
        val compactMode: Boolean = false,
        val maxQuotes: Int = 2,
        val quoteMaxLength: Int = 140,
        val excerptMaxLength: Int? = null
    )

    fun build(
        userQuestion: String,
        chunks: List<RetrievedChunk>,
        options: PromptOptions = PromptOptions()
    ): String {
        val renderedChunks = chunks.joinToString("\n\n") { chunk ->
            buildString {
                append("SOURCE: ").append(chunk.source)
                append("\nFILE: ").append(chunk.titleOrFile)
                if (!chunk.section.isNullOrBlank()) {
                    append("\nSECTION: ").append(chunk.section)
                }
                append("\nCHUNK_ID: ").append(chunk.chunkId)
                append("\nSIMILARITY: ").append(String.format("%.4f", chunk.similarity))
                append("\nQUOTE_SNIPPET: \"").append(toQuoteSnippet(chunk.text, options.quoteMaxLength)).append('"')
                if (options.compactMode) {
                    append("\nEXCERPT:\n").append(toExcerpt(chunk.text, options.excerptMaxLength ?: 280))
                } else {
                    append("\nTEXT:\n").append(chunk.text)
                }
            }
        }

        val prompt = if (options.compactMode) {
            """
                RETRIEVED PROJECT KNOWLEDGE
                Question: "$userQuestion"

                Use only the retrieved context below.
                Do not invent facts, source names, section names, chunk IDs, or quotes.
                Return exactly:

                Answer:
                <grounded answer>

                Sources:
                - source: <actual SOURCE>
                  file: <actual FILE>
                  section: <actual SECTION or n/a>
                  chunk_id: <actual CHUNK_ID>

                Quotes:
                - "<short verbatim quote from retrieved context>"

                Rules:
                - Use at most ${options.maxQuotes} quotes.
                - Keep quotes short.
                - Use only the retrieved context.

                $renderedChunks
            """.trimIndent()
        } else {
            """
                RETRIEVED PROJECT KNOWLEDGE
                The following context was retrieved from the local documentation/project index for the user's question:
                "$userQuestion"

                You MUST answer using only the retrieved context below.
                Do NOT invent facts, source names, section names, chunk IDs, or quotes.
                Every grounded answer MUST use this exact structure:

                Answer:
                <your answer based only on the retrieved context>

                Sources:
                - source: <actual SOURCE value>
                  file: <actual FILE value>
                  section: <actual SECTION value or n/a>
                  chunk_id: <actual CHUNK_ID value>

                Quotes:
                - "<short verbatim quote from the retrieved context>"
                - "<short verbatim quote from the retrieved context>"

                Rules:
                - Sources must reflect actual retrieved metadata only.
                - Quotes must be short and copied verbatim only from retrieved chunks.
                - If the retrieved context is incomplete, state only what is supported by the context.

                $renderedChunks
            """.trimIndent()
        }

        McpTrace.d(
            "event" to "retrieval_prompt_built",
            "chunks" to chunks.size,
            "compactMode" to options.compactMode,
            "maxQuotes" to options.maxQuotes
        )
        return prompt
    }

    fun buildNoKnowledgePrompt(userQuestion: String): String {
        return """
            RETRIEVAL CONFIDENCE GATE
            The retrieved context for the user's question is empty or too weak to support a grounded answer:
            "$userQuestion"

            You MUST NOT guess.
            You MUST respond exactly in this structure:

            Answer:
            I don't know based on the retrieved context.

            Sources:
            - no relevant sources

            Quotes:
            - no relevant quotes

            Clarification:
            Please specify the class, file, or method name.
        """.trimIndent()
    }

    private fun toQuoteSnippet(text: String, maxLength: Int = 140): String {
        val normalized = text
            .replace(Regex("""\s+"""), " ")
            .trim()
            .replace("\"", "'")
        return if (normalized.length <= maxLength) normalized else normalized.take(maxLength).trimEnd() + "..."
    }

    private fun toExcerpt(text: String, maxLength: Int): String {
        val normalized = text
            .replace(Regex("""\s+"""), " ")
            .trim()
            .replace("\"", "'")
        return if (normalized.length <= maxLength) normalized else normalized.take(maxLength).trimEnd() + "..."
    }
}
