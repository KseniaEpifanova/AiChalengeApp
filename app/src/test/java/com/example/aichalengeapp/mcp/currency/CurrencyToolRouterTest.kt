package com.example.aichalengeapp.mcp.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyToolRouterTest {

    private val router = CurrencyToolRouter(CurrencyRequestParser())

    @Test
    fun `matches russian amount conversion request`() {
        val intent = router.route("Сколько будет 100 EUR в USD?")
        assertNotNull(intent)
        assertEquals("EUR", intent?.base)
        assertEquals("USD", intent?.target)
        assertEquals(100.0, intent?.amount ?: 0.0, 0.0001)
    }

    @Test
    fun `matches course request without amount`() {
        val intent = router.route("Какой курс GBP к EUR?")
        assertNotNull(intent)
        assertEquals("GBP", intent?.base)
        assertEquals("EUR", intent?.target)
        assertNull(intent?.amount)
    }

    @Test
    fun `matches english conversion request`() {
        val intent = router.route("Convert 250 USD to JPY")
        assertNotNull(intent)
        assertEquals("USD", intent?.base)
        assertEquals("JPY", intent?.target)
        assertEquals(250.0, intent?.amount ?: 0.0, 0.0001)
    }

    @Test
    fun `does not match unrelated technical request`() {
        val intent = router.route("Спроектируй экран списка фильмов")
        assertNull(intent)
    }

    @Test
    fun `matches russian summary request`() {
        val intent = router.route("Покажи сводку по EUR USD")
        assertNotNull(intent)
        assertEquals("EUR", intent?.base)
        assertEquals("USD", intent?.target)
        assertTrue(intent?.isSummary ?: false)
    }

    @Test
    fun `matches russian summary request with different phrasing`() {
        val intent = router.route("Сводка по EUR USD")
        assertNotNull(intent)
        assertEquals("EUR", intent?.base)
        assertEquals("USD", intent?.target)
        assertTrue(intent?.isSummary ?: false)
    }

    @Test
    fun `matches english summary request`() {
        val intent = router.route("EUR USD summary")
        assertNotNull(intent)
        assertEquals("EUR", intent?.base)
        assertEquals("USD", intent?.target)
        assertTrue(intent?.isSummary ?: false)
    }

    @Test
    fun `matches russian statistics request`() {
        val intent = router.route("Какая статистика по EUR USD?")
        assertNotNull(intent)
        assertEquals("EUR", intent?.base)
        assertEquals("USD", intent?.target)
        assertTrue(intent?.isSummary ?: false)
    }

    @Test
    fun `summary request should not have amount`() {
        val intent = router.route("Сводка по EUR USD")
        assertNotNull(intent)
        assertNull(intent?.amount)
    }

    @Test
    fun `regular rate request should not be summary`() {
        val intent = router.route("Сколько будет 100 EUR в USD?")
        assertNotNull(intent)
        assertFalse(intent?.isSummary ?: true)
    }

    @Test
    fun `handles currency codes in summary request`() {
        val intent = router.route("Сводка по EUR USD")
        assertNotNull(intent)
        assertEquals("EUR", intent?.base)
        assertEquals("USD", intent?.target)
        assertTrue(intent?.isSummary ?: false)
    }
}
