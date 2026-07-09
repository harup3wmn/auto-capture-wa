package com.baim.autocapture

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

// --- Groq Models ---
data class GroqRequest(
    val model: String = "llama3-8b-8192",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.0
)

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqResponse(
    val choices: List<GroqChoice>
)

data class GroqChoice(
    val message: GroqMessage
)

// --- Webhook Model ---
data class WebhookPayload(
    val pesan: String,
    val type: String
)

data class WebhookResponse(
    val status: String,
    val message: String?
)

// --- API Interfaces ---
interface GroqApiService {
    @POST("v1/chat/completions")
    suspend fun checkMessage(
        @Header("Authorization") authHeader: String,
        @Body request: GroqRequest
    ): retrofit2.Response<GroqResponse>
}

interface WebhookService {
    @POST
    suspend fun sendReport(
        @Url url: String,
        @Body payload: WebhookPayload
    ): retrofit2.Response<WebhookResponse>
}

// --- Network Client ---
object NetworkClient {
    private const val GROQ_BASE_URL = "https://api.groq.com/openai/"

    private val okHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val groqApi: GroqApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GROQ_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApiService::class.java)
    }

    val webhookApi: WebhookService by lazy {
        Retrofit.Builder()
            .baseUrl("https://script.google.com/") // Base URL dummy, URL asli dikirim via @Url
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WebhookService::class.java)
    }
}
