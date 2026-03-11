package com.example.aichalengeapp.mcp.currency

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyMcpToolHandlerTest {

    @Test
    fun `successful conversion returns success`() = runBlocking {
        val api = object : FrankfurterApi {
            override suspend fun latest(base: String, target: String, amount: Double?): Result<FrankfurterQuote> {
                return Result.success(
                    FrankfurterQuote(
                        amount = amount ?: 1.0,
                        base = base,
                        date = "2026-03-10",
                        target = target,
                        convertedAmount = 108.34
                    )
                )
            }
        }

        val handler = CurrencyMcpToolHandler(api)
        val result = handler.getExchangeRate(base = "EUR", target = "USD", amount = 100.0)

        assertTrue(result is CurrencyToolResponse.Success)
        val success = result as CurrencyToolResponse.Success
        assertEquals("EUR", success.base)
        assertEquals("USD", success.target)
        assertEquals(108.34, success.convertedAmount ?: 0.0, 0.0001)
    }

    @Test
    fun `invalid currency returns invalid response`() = runBlocking {
        val api = object : FrankfurterApi {
            override suspend fun latest(base: String, target: String, amount: Double?): Result<FrankfurterQuote> {
                return Result.failure(IllegalStateException("must not be called"))
            }
        }

        val handler = CurrencyMcpToolHandler(api)
        val result = handler.getExchangeRate(base = "ABC1", target = "USD", amount = 100.0)

        assertTrue(result is CurrencyToolResponse.InvalidCurrency)
    }

    @Test
    fun `api error returns failure`() = runBlocking {
        val api = object : FrankfurterApi {
            override suspend fun latest(base: String, target: String, amount: Double?): Result<FrankfurterQuote> {
                return Result.failure(RuntimeException("network down"))
            }
        }

        val handler = CurrencyMcpToolHandler(api)
        val result = handler.getExchangeRate(base = "EUR", target = "USD", amount = null)

        assertTrue(result is CurrencyToolResponse.Failure)
        assertTrue((result as CurrencyToolResponse.Failure).message.contains("network down"))
    }
}
