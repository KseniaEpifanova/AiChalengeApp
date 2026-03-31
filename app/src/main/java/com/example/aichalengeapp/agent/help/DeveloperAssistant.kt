package com.example.aichalengeapp.agent.help

import com.example.aichalengeapp.retrieval.RetrievedChunk
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeveloperAssistant @Inject constructor() {

    data class DocsOnlySelection(
        val chunks: List<RetrievedChunk>,
        val filteredOutCount: Int,
        val sourcesUsed: List<String>
    )

    fun parse(message: String): HelpCommand? {
        val trimmed = message.trim()
        if (!trimmed.startsWith(HELP_PREFIX)) return null
        if (trimmed.length > HELP_PREFIX.length) {
            val nextChar = trimmed[HELP_PREFIX.length]
            if (!nextChar.isWhitespace()) return null
        }
        return HelpCommand(
            rawMessage = trimmed,
            question = trimmed.removePrefix(HELP_PREFIX).trim()
        )
    }

    fun buildGuidanceMessage(): String {
        return """
            Developer assistant mode:
            - `/help what does this project do?`
            - `/help how does RAG work in this app?`
            - `/help what is the difference between LOCAL and REMOTE?`
            - `/help what is the architecture of the chat flow?`
            - `/help what is the current git branch?`
        """.trimIndent()
    }

    fun isGitBranchQuestion(question: String): Boolean {
        val lowered = question.trim().lowercase(Locale.US)
        if (lowered.isBlank()) return false
        return ("branch" in lowered && "git" in lowered) ||
            "current branch" in lowered ||
            "current git branch" in lowered ||
            "git branch" in lowered
    }

    fun isProjectFilesQuestion(question: String): Boolean {
        val lowered = question.trim().lowercase(Locale.US)
        if (lowered.isBlank()) return false
        return "list project files" in lowered ||
            "show project files" in lowered ||
            "project files" in lowered ||
            "files in the project" in lowered
    }

    fun selectDocumentationChunks(chunks: List<RetrievedChunk>): DocsOnlySelection {
        val docsChunks = chunks.filter(::isDocumentationChunk)
        return DocsOnlySelection(
            chunks = docsChunks,
            filteredOutCount = (chunks.size - docsChunks.size).coerceAtLeast(0),
            sourcesUsed = docsChunks.map(::sourceLabel).distinct()
        )
    }

    fun hasOnlyDocumentationChunks(chunks: List<RetrievedChunk>): Boolean {
        return chunks.all(::isDocumentationChunk)
    }

    fun buildHelpSystemPrompt(
        question: String,
        docsPrompt: String,
        currentBranch: String?
    ): String {
        return buildString {
            appendLine("DEVELOPER ASSISTANT MODE")
            appendLine("You are answering a /help question about this Android project.")
            appendLine("Prefer README.md and the docs folder as the authoritative source.")
            appendLine("Do not answer as a general chatbot.")
            if (!currentBranch.isNullOrBlank()) {
                appendLine("Current git branch from MCP: $currentBranch")
            }
            appendLine()
            append(docsPrompt)
        }.trim()
    }

    fun buildBranchReply(branch: String?): String {
        return if (branch.isNullOrBlank()) {
            "Current git branch is unavailable from the connected MCP server."
        } else {
            "Current git branch: `$branch`."
        }
    }

    fun buildProjectFilesReply(files: List<String>?): String {
        if (files.isNullOrEmpty()) {
            return "Project files are unavailable from the connected MCP server."
        }
        return buildString {
            appendLine("Project files:")
            files.take(MAX_PROJECT_FILES).forEach { file ->
                append("- ").appendLine(file)
            }
        }.trim()
    }

    private fun isDocumentationChunk(chunk: RetrievedChunk): Boolean {
        val file = chunk.titleOrFile.lowercase(Locale.US)
        val source = chunk.source.lowercase(Locale.US)
        return file == "readme.md" ||
            file.startsWith("docs/") ||
            file.endsWith(".md") && ("docs" in source || "readme" in source) ||
            "readme" in source ||
            "docs" in source
    }

    private fun sourceLabel(chunk: RetrievedChunk): String {
        return chunk.titleOrFile.ifBlank { chunk.source }
    }

    data class HelpCommand(
        val rawMessage: String,
        val question: String
    )

    private companion object {
        private const val HELP_PREFIX = "/help"
        private const val MAX_PROJECT_FILES = 20
    }
}
