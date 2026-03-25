package com.example.aichalengeapp.repo

import com.example.aichalengeapp.BuildConfig
import com.example.aichalengeapp.data.OllamaRequest
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OllamaLocalLlmRepository @Inject constructor(
    private val moshi: Moshi,
    private val settingsStore: LlmSettingsStore
) : LocalLlmRepository {

    private val okHttpClient: OkHttpClient by lazy {
        val headers = Interceptor { chain ->
            chain.proceed(
                chain.request()
                    .newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .build()
            )
        }
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }

        OkHttpClient.Builder()
            .addInterceptor(headers)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun send(prompt: String): String {
        val settings = settingsStore.load()
        val api = Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(settings.localBaseUrl))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OllamaApi::class.java)

        val response = try {
            api.generate(
                OllamaRequest(
                    model = settings.localModel,
                    prompt = prompt,
                    stream = false
                )
            )
        } catch (e: UnknownHostException) {
            throw IOException(
                "Unable to resolve Ollama host. Check emulator host or local IP.",
                e
            )
        } catch (e: IllegalArgumentException) {
            throw IOException(
                "Invalid Ollama base URL configuration.",
                e
            )
        }

        return response.response.trim()
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val normalized = baseUrl
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .removeSuffix("/")
        require(normalized.isNotBlank()) {
            "OLLAMA_BASE_URL is blank. Set a valid emulator or device host."
        }
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "OLLAMA_BASE_URL must start with http:// or https://"
        }
        return "$normalized/"
    }
}
