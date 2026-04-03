package com.example.aichalengeapp.agent.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileOperationRouterTest {

    private val router = FileOperationRouter()

    @Test
    fun `search request routes to search intent`() {
        val intent = router.route("Find where MCP is used in the project")

        assertEquals(FileOperationIntent.Search("Find where MCP is used in the project"), intent)
    }

    @Test
    fun `generation request routes to generate intent`() {
        val intent = router.route("Generate README for support module")

        assertEquals(
            FileOperationIntent.Generate(
                request = "Generate README for support module",
                outputPath = "docs/generated-support-readme.md"
            ),
            intent
        )
    }

    @Test
    fun `unrelated request is ignored`() {
        assertNull(router.route("Hello there"))
    }
}
