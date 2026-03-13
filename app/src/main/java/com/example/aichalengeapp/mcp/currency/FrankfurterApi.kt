package com.example.aichalengeapp.mcp.currency

import com.example.aichalengeapp.mcp.McpTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

interface FrankfurterApi {
    suspend fun latest(
        base: String,
        target: String,
        amount: Double? = null
    ): Result<FrankfurterQuote>
}

@Singleton
class FrankfurterHttpApi @Inject constructor() : FrankfurterApi {
    private val http = OkHttpClient()

    override suspend fun latest(
        base: String,
        target: String,
        amount: Double?
    ): Result<FrankfurterQuote> = runCatching {
        val urlBuilder = "https://api.frankfurter.dev/latest".toHttpUrl().newBuilder()
            .addQueryParameter("from", base)
            .addQueryParameter("to", target)
        if (amount != null) {
            urlBuilder.addQueryParameter("amount", amount.toString())
        }
        val url = urlBuilder.build()
        McpTrace.d("event" to "frankfurter_request_start", "url" to url.toString(), "base" to base, "target" to target, "amount" to amount)

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val body = withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Frankfurter HTTP ${response.code}: $payload")
                }
                payload
            }
        }

        McpTrace.d("event" to "frankfurter_request_success", "body" to body)
        val json = JSONObject(body)
        val quoteAmount = json.optDouble("amount").takeUnless { it.isNaN() } ?: (amount ?: 1.0)
        val quoteBase = json.optString("base", base)
        val quoteDate = json.optString("date", "")
        val rates = json.optJSONObject("rates") ?: error("Frankfurter response missing rates")
        if (!rates.has(target)) {
            error("Frankfurter response missing rate for $target")
        }
        val converted = rates.optDouble(target).takeUnless { it.isNaN() } ?: error("Invalid conversion amount")

        FrankfurterQuote(
            amount = quoteAmount,
            base = quoteBase,
            date = quoteDate,
            target = target,
            convertedAmount = converted
        )
    }.onFailure {
        McpTrace.d("event" to "frankfurter_request_failure", "base" to base, "target" to target, "amount" to amount, "error" to (it.message ?: it::class.java.simpleName))
    }
}
