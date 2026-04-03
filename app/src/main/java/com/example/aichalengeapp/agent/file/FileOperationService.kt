package com.example.aichalengeapp.agent.file

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.mcp.McpTrace
import com.example.aichalengeapp.mcp.git.McpGitService
import com.example.aichalengeapp.repo.ChatRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileOperationService @Inject constructor(
    private val mcpGitService: McpGitService,
    private val llmRepository: ChatRepository
) {

    suspend fun execute(intent: FileOperationIntent): FileOperationResult {
        McpTrace.d("event" to "file_operation_started", "mode" to intent.javaClass.simpleName, "request" to intent.request)
        return when (intent) {
            is FileOperationIntent.Search -> executeSearch(intent)
            is FileOperationIntent.Generate -> executeGeneration(intent)
        }
    }

    private suspend fun executeSearch(intent: FileOperationIntent.Search): FileOperationResult {
        val projectFiles = mcpGitService.listProjectFiles().orEmpty()
        val selectedFiles = selectFiles(
            projectFiles = projectFiles,
            request = intent.request,
            generationMode = false
        )
        if (selectedFiles.isEmpty()) {
            return FileOperationResult(
                text = "I couldn't find relevant project files for that request.",
                debugLabel = "file-operation-search"
            )
        }

        val fileContents = selectedFiles.mapNotNull { path ->
            mcpGitService.readProjectFile(path)?.let { content ->
                FileContent(path = path, content = content)
            }
        }
        if (fileContents.isEmpty()) {
            return FileOperationResult(
                text = "I found candidate files, but I couldn't read their contents from MCP.",
                debugLabel = "file-operation-search"
            )
        }

        val queryTerms = extractQueryTerms(intent.request)
        val rendered = fileContents.joinToString("\n") { file ->
            val explanation = buildSearchExplanation(file.content, queryTerms)
            "- ${file.path}: $explanation"
        }
        return FileOperationResult(
            text = """
                Files where this appears relevant:
                $rendered
            """.trimIndent(),
            debugLabel = "file-operation-search"
        )
    }

    private suspend fun executeGeneration(intent: FileOperationIntent.Generate): FileOperationResult {
        val projectFiles = mcpGitService.listProjectFiles().orEmpty()
        val selectedFiles = selectFiles(
            projectFiles = projectFiles,
            request = intent.request,
            generationMode = true
        )
        if (selectedFiles.isEmpty()) {
            return FileOperationResult(
                text = "I couldn't find enough relevant files to generate that document.",
                debugLabel = "file-operation-generate"
            )
        }

        val fileContents = selectedFiles.mapNotNull { path ->
            mcpGitService.readProjectFile(path)?.let { content ->
                FileContent(path = path, content = content.take(MAX_FILE_CHARS))
            }
        }
        if (fileContents.isEmpty()) {
            return FileOperationResult(
                text = "I found candidate files, but I couldn't read their contents from MCP.",
                debugLabel = "file-operation-generate"
            )
        }

        val prompt = buildGenerationPrompt(intent.request, intent.outputPath, fileContents)
        val llmResult = llmRepository.ask(
            messages = listOf(
                AgentMessage(AgentRole.SYSTEM, GENERATION_SYSTEM_PROMPT),
                AgentMessage(AgentRole.USER, prompt)
            ),
            maxOutputTokens = MAX_GENERATION_OUTPUT_TOKENS
        )
        val generatedContent = llmResult.text.trim()
        if (generatedContent.isBlank()) {
            return FileOperationResult(
                text = "The assistant generated an empty file, so nothing was saved.",
                debugLabel = "file-operation-generate"
            )
        }
        McpTrace.d("event" to "file_generated", "outputPath" to intent.outputPath, "chars" to generatedContent.length)

        val saved = mcpGitService.writeProjectFile(intent.outputPath, generatedContent)
        return if (saved) {
            McpTrace.d("event" to "file_saved", "outputPath" to intent.outputPath)
            FileOperationResult(
                text = """
                    Generated file saved to `${intent.outputPath}`.

                    Preview:
                    ${generatedContent.take(PREVIEW_CHARS)}
                """.trimIndent(),
                debugLabel = "file-operation-generate",
                generatedPath = intent.outputPath,
                generatedContent = generatedContent
            )
        } else {
            FileOperationResult(
                text = """
                    I generated content for `${intent.outputPath}`, but saving via MCP failed.

                    Preview:
                    ${generatedContent.take(PREVIEW_CHARS)}
                """.trimIndent(),
                debugLabel = "file-operation-generate",
                generatedPath = intent.outputPath,
                generatedContent = generatedContent
            )
        }
    }

    private fun selectFiles(
        projectFiles: List<String>,
        request: String,
        generationMode: Boolean
    ): List<String> {
        val loweredRequest = request.lowercase()
        val keywords = when {
            generationMode && loweredRequest.contains("support") -> listOf("support", "docs/", "readme", "faq")
            loweredRequest.contains("mcp") -> listOf("mcp", "chatagent", "help")
            else -> extractQueryTerms(request)
        }
        val scored = projectFiles.mapNotNull { path ->
            val loweredPath = path.lowercase()
            val score = keywords.count { loweredPath.contains(it) }
            if (score == 0) null else path to score
        }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.length })
            .map { it.first }
            .take(MAX_FILES_TO_READ)

        McpTrace.d(
            "event" to "files_selected",
            "request" to request,
            "count" to scored.size,
            "files" to scored.joinToString(",")
        )
        return scored
    }

    private fun extractQueryTerms(request: String): List<String> {
        return request.lowercase()
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .distinct()
    }

    private fun buildSearchExplanation(content: String, queryTerms: List<String>): String {
        val normalized = content.lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                queryTerms.any { term -> line.contains(term, ignoreCase = true) }
            }
            ?: content.lineSequence().firstOrNull().orEmpty()
        return normalized.take(EXPLANATION_CHARS).ifBlank { "Relevant content found in this file." }
    }

    private fun buildGenerationPrompt(
        request: String,
        outputPath: String,
        files: List<FileContent>
    ): String {
        val renderedFiles = files.joinToString("\n\n") { file ->
            """
                FILE: ${file.path}
                CONTENT:
                ${file.content}
            """.trimIndent()
        }
        return """
            Generate content for `$outputPath`.
            User request: $request

            Use only the files below.
            Produce concise Markdown that explains the support module and its main files.
            Include:
            - purpose
            - main classes
            - support answer flow
            - notes for future updates

            $renderedFiles
        """.trimIndent()
    }

    data class FileOperationResult(
        val text: String,
        val debugLabel: String,
        val generatedPath: String? = null,
        val generatedContent: String? = null
    )

    private data class FileContent(
        val path: String,
        val content: String
    )

    private companion object {
        private const val MAX_FILES_TO_READ = 5
        private const val MAX_FILE_CHARS = 2_500
        private const val MAX_GENERATION_OUTPUT_TOKENS = 700
        private const val EXPLANATION_CHARS = 180
        private const val PREVIEW_CHARS = 600
        private const val GENERATION_SYSTEM_PROMPT =
            "You are a file-aware Android engineering assistant. Read the provided files and produce grounded markdown only from those files."
        private val STOP_WORDS = setOf("find", "where", "used", "usage", "the", "for", "readme", "generate", "create", "write", "module", "project")
    }
}
