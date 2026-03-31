package com.example.aichalengeapp.agent.help

import com.example.aichalengeapp.retrieval.RetrievedChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperAssistantTest {

    private val assistant = DeveloperAssistant()

    @Test
    fun `parse returns help command for slash help`() {
        val command = assistant.parse("/help how does RAG work?")

        assertNotNull(command)
        assertEquals("how does RAG work?", command?.question)
    }

    @Test
    fun `parse ignores non help messages`() {
        assertNull(assistant.parse("how does RAG work?"))
        assertNull(assistant.parse("/helper"))
    }

    @Test
    fun `guidance message covers supported developer questions`() {
        val guidance = assistant.buildGuidanceMessage()

        assertTrue(guidance.contains("what does this project do"))
        assertTrue(guidance.contains("current git branch"))
    }

    @Test
    fun `branch question detection matches git branch wording`() {
        assertTrue(assistant.isGitBranchQuestion("what is the current git branch?"))
        assertTrue(assistant.isGitBranchQuestion("show git branch"))
        assertTrue(!assistant.isGitBranchQuestion("how does rag work"))
    }

    @Test
    fun `project files question detection matches developer file requests`() {
        assertTrue(assistant.isProjectFilesQuestion("list project files"))
        assertTrue(assistant.isProjectFilesQuestion("show project files"))
        assertTrue(!assistant.isProjectFilesQuestion("how does rag work"))
    }

    @Test
    fun `documentation filter keeps markdown docs and drops code chunks`() {
        val selection = assistant.selectDocumentationChunks(
            listOf(
                RetrievedChunk(
                    source = "docs",
                    titleOrFile = "architecture.md",
                    section = "overview",
                    chunkId = "doc-1",
                    strategy = "semantic",
                    text = "Architecture docs",
                    similarity = 0.8
                ),
                RetrievedChunk(
                    source = "android-app",
                    titleOrFile = "ChatAgent.kt",
                    section = "handleUserMessage",
                    chunkId = "code-1",
                    strategy = "semantic",
                    text = "Code chunk",
                    similarity = 0.9
                )
            )
        )

        assertEquals(1, selection.chunks.size)
        assertEquals("architecture.md", selection.chunks.first().titleOrFile)
        assertEquals(1, selection.filteredOutCount)
        assertTrue(assistant.hasOnlyDocumentationChunks(selection.chunks))
    }

    @Test
    fun `documentation filter never allows kt chunks into help mode`() {
        val selection = assistant.selectDocumentationChunks(
            listOf(
                RetrievedChunk(
                    source = "android-app",
                    titleOrFile = "README.md",
                    section = "overview",
                    chunkId = "readme-1",
                    strategy = "semantic",
                    text = "Project overview",
                    similarity = 0.8
                ),
                RetrievedChunk(
                    source = "android-app",
                    titleOrFile = "ChatAgent.kt",
                    section = "handleUserMessage",
                    chunkId = "chat-agent-1",
                    strategy = "semantic",
                    text = "Code chunk",
                    similarity = 0.9
                )
            )
        )

        assertEquals(listOf("README.md"), selection.sourcesUsed)
        assertTrue(selection.chunks.none { it.titleOrFile.endsWith(".kt") })
    }

    @Test
    fun `branch fallback message is safe and explicit`() {
        assertEquals(
            "Current git branch is unavailable from the connected MCP server.",
            assistant.buildBranchReply(null)
        )
    }

    @Test
    fun `project files reply renders file list`() {
        val reply = assistant.buildProjectFilesReply(listOf("README.md", "docs/rag.md"))

        assertTrue(reply.contains("Project files:"))
        assertTrue(reply.contains("README.md"))
        assertTrue(reply.contains("docs/rag.md"))
    }
}
