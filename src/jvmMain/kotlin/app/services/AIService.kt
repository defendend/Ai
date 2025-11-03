package app.services

import app.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object AIService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun sendMessage(provider: String, messages: List<Message>): String {
        return when (provider) {
            "claude" -> sendClaudeMessage(messages)
            "deepseek" -> sendDeepSeekMessage(messages)
            else -> throw IllegalArgumentException("Unknown provider: $provider")
        }
    }

    private suspend fun sendClaudeMessage(messages: List<Message>): String {
        val apiKey = System.getenv("CLAUDE_API_KEY")
            ?: throw IllegalStateException("CLAUDE_API_KEY environment variable is not set")

        val request = AnthropicRequest(
            model = "claude-3-5-sonnet-20241022",
            messages = messages,
            maxTokens = 4096,
            stream = false
        )

        try {
            val response: HttpResponse = client.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw Exception("Claude API error: ${response.status} - $errorBody")
            }

            val anthropicResponse: AnthropicResponse = response.body()
            return anthropicResponse.content.firstOrNull()?.text
                ?: throw Exception("No content in Claude response")
        } catch (e: Exception) {
            throw Exception("Failed to call Claude API: ${e.message}", e)
        }
    }

    private suspend fun sendDeepSeekMessage(messages: List<Message>): String {
        val apiKey = System.getenv("DEEPSEEK_API_KEY")
            ?: throw IllegalStateException("DEEPSEEK_API_KEY environment variable is not set")

        val request = DeepSeekRequest(
            model = "deepseek-chat",
            messages = messages,
            maxTokens = 4096,
            temperature = 0.7
        )

        try {
            val response: HttpResponse = client.post("https://api.deepseek.com/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw Exception("DeepSeek API error: ${response.status} - $errorBody")
            }

            val deepSeekResponse: DeepSeekResponse = response.body()
            return deepSeekResponse.choices.firstOrNull()?.message?.content
                ?: throw Exception("No content in DeepSeek response")
        } catch (e: Exception) {
            throw Exception("Failed to call DeepSeek API: ${e.message}", e)
        }
    }
}
