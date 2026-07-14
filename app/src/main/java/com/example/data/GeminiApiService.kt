package com.example.data

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class Part(
    val text: String?
)

data class Content(
    val parts: List<Part>
)

data class GenerateContentRequest(
    val contents: List<Content>
)

data class Candidate(
    val content: Content
)

data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/"

        val instance: GeminiApiService by lazy {
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GeminiApiService::class.java)
        }
    }
}
