package com.example.aichalengeapp.mcp.orchestration

import com.example.aichalengeapp.mcp.currency.CurrencyRequestParser
import com.example.aichalengeapp.mcp.currency.CurrencyToolRouter
import com.example.aichalengeapp.mcp.pipeline.PipelineToolRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeRequestRouterTest {

    private val router = CompositeRequestRouter(
        pipelineToolRouter = PipelineToolRouter(),
        currencyToolRouter = CurrencyToolRouter(CurrencyRequestParser())
    )

    @Test
    fun `pipeline-only request is routed to pipeline`() {
        val route = router.route("Найди посты по слову qui, сделай краткую сводку и сохрани в файл latest_summary.txt")
        assertTrue(route is OrchestrationRoute.PipelineOnly)
    }

    @Test
    fun `currency-only request is routed to currency`() {
        val route = router.route("Сколько будет 100 EUR в USD?")
        assertTrue(route is OrchestrationRoute.CurrencyOnly)
    }

    @Test
    fun `combined request is routed to combined`() {
        val route = router.route("Найди посты по слову qui, сделай краткую сводку и сохрани в файл, а потом скажи, сколько будет 100 EUR в USD")
        assertTrue(route is OrchestrationRoute.Combined)
        val combined = route as OrchestrationRoute.Combined
        assertEquals("qui", combined.pipelineIntent.query)
        assertEquals("EUR", combined.currencyIntent.base)
        assertEquals("USD", combined.currencyIntent.target)
    }

    @Test
    fun `unrelated request is not handled`() {
        val route = router.route("Explain how coroutines cancellation works")
        assertTrue(route is OrchestrationRoute.None)
    }
}
