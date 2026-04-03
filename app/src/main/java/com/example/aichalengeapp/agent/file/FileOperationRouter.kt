package com.example.aichalengeapp.agent.file

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileOperationRouter @Inject constructor() {

    fun route(message: String): FileOperationIntent? {
        val normalized = message.trim()
        if (normalized.isBlank()) return null

        val lowered = normalized.lowercase(Locale.US)
        return when {
            isGenerationIntent(lowered) -> FileOperationIntent.Generate(
                request = normalized,
                outputPath = resolveOutputPath(lowered)
            )
            isSearchIntent(lowered) -> FileOperationIntent.Search(request = normalized)
            else -> null
        }
    }

    private fun isSearchIntent(message: String): Boolean {
        val hasSearchVerb = listOf("find", "where is", "where", "usage", "used").any { message.contains(it) }
        val hasProjectSignal = listOf("project", "mcp", "support", "file").any { message.contains(it) }
        return hasSearchVerb && hasProjectSignal
    }

    private fun isGenerationIntent(message: String): Boolean {
        val hasGenerationVerb = listOf("generate", "create", "write").any { message.contains(it) }
        val hasArtifactSignal = listOf("readme", "module", "docs").any { message.contains(it) }
        return hasGenerationVerb && hasArtifactSignal
    }

    private fun resolveOutputPath(message: String): String {
        return if (message.contains("readme") && message.contains("support")) {
            "docs/generated-support-readme.md"
        } else {
            "docs/generated-file.md"
        }
    }
}

sealed class FileOperationIntent(open val request: String) {
    data class Search(override val request: String) : FileOperationIntent(request)
    data class Generate(
        override val request: String,
        val outputPath: String
    ) : FileOperationIntent(request)
}
