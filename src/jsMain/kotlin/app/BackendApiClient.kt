package app

import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.w3c.dom.get
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit
import kotlin.js.json

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserInfo
)

@Serializable
data class UserInfo(
    val id: Int,
    val email: String
)

@Serializable
data class ChatResponse(
    val id: Int,
    val title: String,
    val provider: String,
    val messageCount: Int,
    val lastMessage: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class MessageResponse(
    val id: Int,
    val role: String,
    val content: String,
    val timestamp: String
)

@Serializable
data class CreateChatRequest(
    val title: String,
    val provider: String
)

@Serializable
data class SendMessageRequest(
    val content: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class UserDTO(
    val id: Int,
    val email: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserDTO
)

class BackendApiClient {
    private val baseUrl = window.location.origin
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun getAuthHeaders(): Headers {
        return Headers().apply {
            append("Content-Type", "application/json")
            val token = localStorage["jwt_token"]
            if (token != null) {
                append("Authorization", "Bearer $token")
            }
        }
    }

    suspend fun getChats(): Result<List<ChatResponse>> {
        return try {
            val response = window.fetch(
                "$baseUrl/api/chats",
                RequestInit(
                    method = "GET",
                    headers = getAuthHeaders()
                )
            ).await()

            if (!response.ok) {
                return Result.failure(Exception("Failed to fetch chats: ${response.statusText}"))
            }

            val text = response.text().await()
            val chats = json.decodeFromString<List<ChatResponse>>(text)
            Result.success(chats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createChat(title: String, provider: String): Result<ChatResponse> {
        return try {
            val requestBody = json.encodeToString(
                CreateChatRequest.serializer(),
                CreateChatRequest(title, provider)
            )

            val response = window.fetch(
                "$baseUrl/api/chats",
                RequestInit(
                    method = "POST",
                    headers = getAuthHeaders(),
                    body = requestBody
                )
            ).await()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception("Failed to create chat: $errorText"))
            }

            val text = response.text().await()
            val chat = json.decodeFromString<ChatResponse>(text)
            Result.success(chat)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChatWithMessages(chatId: Int): Result<List<MessageResponse>> {
        return try {
            val response = window.fetch(
                "$baseUrl/api/chats/$chatId",
                RequestInit(
                    method = "GET",
                    headers = getAuthHeaders()
                )
            ).await()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception("Failed to fetch chat messages: $errorText"))
            }

            val text = response.text().await()

            // Backend returns ChatWithMessagesDTO, extract messages array
            val chatData = json.parseToJsonElement(text).jsonObject
            val messagesJson = chatData["messages"]?.toString() ?: "[]"
            val messages = json.decodeFromString<List<MessageResponse>>(messagesJson)

            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateChatProvider(chatId: Int, provider: String): Result<Unit> {
        return try {
            val requestBody = """{"provider":"$provider"}"""

            val response = window.fetch(
                "$baseUrl/api/chats/$chatId",
                RequestInit(
                    method = "PATCH",
                    headers = getAuthHeaders(),
                    body = requestBody
                )
            ).await()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception("Failed to update chat provider: $errorText"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChat(chatId: Int): Result<Unit> {
        return try {
            val response = window.fetch(
                "$baseUrl/api/chats/$chatId",
                RequestInit(
                    method = "DELETE",
                    headers = getAuthHeaders()
                )
            ).await()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception("Failed to delete chat: $errorText"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateChatTitle(chatId: Int, title: String): Result<Unit> {
        return try {
            val escapedTitle = title.replace("\"", "\\\"")
            val requestBody = """{"title":"$escapedTitle"}"""

            val response = window.fetch(
                "$baseUrl/api/chats/$chatId",
                RequestInit(
                    method = "PATCH",
                    headers = getAuthHeaders(),
                    body = requestBody
                )
            ).await()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception("Failed to update chat title: $errorText"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(chatId: Int, content: String): Result<List<MessageResponse>> {
        return try {
            val requestBody = json.encodeToString(
                SendMessageRequest.serializer(),
                SendMessageRequest(content)
            )

            val response = window.fetch(
                "$baseUrl/api/chats/$chatId/messages",
                RequestInit(
                    method = "POST",
                    headers = getAuthHeaders(),
                    body = requestBody
                )
            ).await()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception("Failed to send message: $errorText"))
            }

            val text = response.text().await()
            val messages = json.decodeFromString<List<MessageResponse>>(text)
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<LoginResponse> {
        return try {
            val requestBody = json.encodeToString(
                LoginRequest.serializer(),
                LoginRequest(email, password)
            )

            val response = window.fetch(
                "$baseUrl/api/auth/login",
                RequestInit(
                    method = "POST",
                    headers = Headers().apply {
                        append("Content-Type", "application/json")
                    },
                    body = requestBody
                )
            ).await()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception("Invalid email or password"))
            }

            val text = response.text().await()
            val loginResponse = json.decodeFromString<LoginResponse>(text)
            Result.success(loginResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
