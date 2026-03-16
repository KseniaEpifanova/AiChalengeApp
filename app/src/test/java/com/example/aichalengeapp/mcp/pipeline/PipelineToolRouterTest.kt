package com.example.aichalengeapp.mcp.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PipelineToolRouterTest {

    private val router = PipelineToolRouter()

    @Test
    fun `matches russian pipeline request`() {
        val intent = router.route("Найди посты по слову qui, сделай краткую сводку и сохрани в файл latest_summary.txt")
        assertNotNull(intent)
        assertEquals("qui", intent?.query)
        assertEquals("latest_summary.txt", intent?.filename)
    }

    @Test
    fun `matches english pipeline request`() {
        val intent = router.route("Search posts for qui, summarize and save to file demo.txt")
        assertNotNull(intent)
        assertEquals("qui", intent?.query)
        assertEquals("demo.txt", intent?.filename)
    }

    @Test
    fun `does not match unrelated request`() {
        val intent = router.route("Сколько будет 100 EUR в USD?")
        assertNull(intent)
    }
}
