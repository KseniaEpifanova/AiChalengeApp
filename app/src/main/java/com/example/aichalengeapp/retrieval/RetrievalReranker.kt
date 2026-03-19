package com.example.aichalengeapp.retrieval

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrievalReranker @Inject constructor() {
    fun rerank(query: String, chunks: List<RetrievedChunk>, enabled: Boolean): List<RetrievedChunk> {
        if (!enabled) return chunks

        val queryTerms = tokenize(query)
        val codeTerms = extractCodeTerms(query)
        return chunks.map { chunk ->
            val reasons = mutableListOf<String>()
            var boost = 0.0
            val title = chunk.titleOrFile.lowercase()
            val fileBase = title.substringBeforeLast(".")
            val section = chunk.section.orEmpty().lowercase()
            val text = chunk.text.lowercase()
            var structuralMatch = false

            queryTerms.forEach { term ->
                if (term.length < 3) return@forEach
                if (title.contains(term)) {
                    boost += 0.16
                    reasons += "title:$term"
                    structuralMatch = true
                }
                if (section.contains(term)) {
                    boost += 0.10
                    reasons += "section:$term"
                    structuralMatch = true
                }
                if (text.contains(term)) {
                    boost += 0.04
                    reasons += "text:$term"
                }
            }

            codeTerms.forEach { term ->
                if (fileBase == term) {
                    boost += 0.55
                    reasons += "file_exact:$term"
                    structuralMatch = true
                } else if (title.contains(term)) {
                    boost += 0.24
                    reasons += "file_contains:$term"
                    structuralMatch = true
                }

                if (containsTypeDeclaration(text, term)) {
                    boost += 0.30
                    reasons += "type_decl:$term"
                    structuralMatch = true
                }
                if (containsMethodDeclaration(text, term)) {
                    boost += 0.24
                    reasons += "method_decl:$term"
                    structuralMatch = true
                }
            }

            if (codeTerms.isNotEmpty() && !structuralMatch) {
                boost -= 0.10
                reasons += "penalty:no_structural_match"
            }

            chunk.copy(finalScore = chunk.similarity + boost, boostReasons = reasons)
        }.sortedByDescending { it.finalScore }
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .split(Regex("""[^\p{L}\p{N}_]+"""))
            .filter { it.isNotBlank() }
    }

    private fun extractCodeTerms(text: String): List<String> {
        return CODE_TERM_REGEX.findAll(text)
            .map { it.value.lowercase() }
            .filter { it.length >= 3 }
            .distinct()
            .toList()
    }

    private fun containsTypeDeclaration(text: String, term: String): Boolean {
        val escaped = Regex.escape(term)
        return Regex("""\b(class|object|interface|data\s+class)\s+$escaped\b""").containsMatchIn(text)
    }

    private fun containsMethodDeclaration(text: String, term: String): Boolean {
        val escaped = Regex.escape(term)
        return Regex("""\bfun\s+$escaped\b""").containsMatchIn(text) || text.contains("$term(")
    }

    private companion object {
        val CODE_TERM_REGEX = Regex("""[A-Za-z_][A-Za-z0-9_]{2,}""")
    }
}
