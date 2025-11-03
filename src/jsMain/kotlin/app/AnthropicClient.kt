package app

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Headers

class AnthropicClient(private var apiKey: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val apiUrl = "https://api.anthropic.com/v1/messages"

    fun updateApiKey(newKey: String) {
        apiKey = newKey
    }

    suspend fun sendMessage(messages: List<Message>): Result<String> {
        return try {
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API key is not set"))
            }

            val request = AnthropicRequest(
                model = "claude-3-5-sonnet-20241022",
                messages = messages,
                maxTokens = 4096
            )

            val headers = Headers()
            headers.append("Content-Type", "application/json")
            headers.append("x-api-key", apiKey)
            headers.append("anthropic-version", "2023-06-01")

            val requestInit = RequestInit(
                method = "POST",
                headers = headers,
                body = json.encodeToString(request)
            )

            val response = window.fetch(apiUrl, requestInit).await()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception("API Error: ${response.status} - $errorText"))
            }

            val responseText = response.text().await()
            val anthropicResponse = json.decodeFromString<AnthropicResponse>(responseText)

            val content = anthropicResponse.content.firstOrNull()?.text
                ?: return Result.failure(Exception("No content in response"))

            Result.success(content)
        } catch (e: Exception) {
            console.error("Error sending message:", e)
            Result.failure(e)
        }
    }
}
