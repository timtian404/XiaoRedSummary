package com.xiaoredsummary.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClaudeApiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun summarize(subtitleText: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", buildPrompt(subtitleText))
                }
            )

            val body = JSONObject().apply {
                put("model", "claude-sonnet-4-20250514")
                put("max_tokens", 2048)
                put("messages", messages)
            }

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("API error ${response.code}: $responseBody")
                )
            }

            val json = JSONObject(responseBody)
            val content = json.getJSONArray("content")
            val text = content.getJSONObject(0).getString("text")
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildPrompt(subtitles: String): String {
        return """Below are subtitles captured from a video. Please:
1. Generate a short title for this video (one line).
2. Provide a clear, concise summary of the video content.

Respond in the SAME LANGUAGE as the subtitles. Use this exact format:

TITLE: <title here>

SUMMARY:
<summary here>

---
SUBTITLES:
$subtitles"""
    }
}
