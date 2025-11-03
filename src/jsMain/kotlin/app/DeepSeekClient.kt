package app

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Headers

class DeepSeekClient(private var apiKey: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val apiUrl = "https://api.deepseek.com/v1/chat/completions"

    fun updateApiKey(newKey: String) {
        apiKey = newKey
    }

    suspend fun sendMessage(messages: List<Message>): Result<String> {
        return try {
            if (apiKey.isBlank()) {
                return Result.failure(Exception("DeepSeek API key is not set"))
            }

            val request = DeepSeekRequest(
                model = "deepseek-chat",
                messages = messages,
                maxTokens = 4096,
                temperature = 0.7
            )

            val headers = Headers()
            headers.append("Content-Type", "application/json")
            headers.append("Authorization", "Bearer $apiKey")

            val requestInit = RequestInit(
                method = "POST",
                headers = headers,
                body = json.encodeToString(request)
            )

            val response = window.fetch(apiUrl, requestInit).await()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception("DeepSeek API Error: ${response.status} - $errorText"))
            }

            val responseText = response.text().await()
            val deepseekResponse = json.decodeFromString<DeepSeekResponse>(responseText)

            val content = deepseekResponse.choices.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("No content in response"))

            Result.success(content)
        } catch (e: Exception) {
            console.error("Error sending message to DeepSeek:", e)
            Result.failure(e)
        }
    }
}
