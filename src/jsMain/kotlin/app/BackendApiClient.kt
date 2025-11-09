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
import org.w3c.fetch.Response
import kotlin.js.json
import kotlin.js.Promise

// External declaration for browser API types
external class Uint8Array

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
    val updatedAt: String,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val systemPrompt: String? = null,
    val streaming: Boolean = true,
    val responseFormat: String = "none",
    val responseSchema: String? = null,
    val responseStyle: String = "professional",
    val responseLength: String = "standard",
    val language: String = "auto",
    val includeExamples: Boolean = false,
    val contentFormat: String = "paragraphs",
    val agentType: String = "none",
    val agentGoalAchieved: Boolean = false
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
    val email: String,
    val isAdmin: Boolean = false,
    val allowedProviders: String = "deepseek,claude",
    val requestLimit: Int = 100,
    val requestCount: Int = 0
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserDTO
)

@Serializable
data class CompareReasoningRequest(
    val task: String,
    val provider: String = "deepseek"
)

@Serializable
data class CompareReasoningResponse(
    val task: String,
    val approaches: List<ReasoningApproachResult>
)

@Serializable
data class ReasoningApproachResult(
    val name: String,
    val description: String,
    val answer: String
)

@Serializable
data class SingleApproachRequest(
    val task: String,
    val approach: String,
    val provider: String = "deepseek"
)

@Serializable
data class SingleApproachResponse(
    val task: String,
    val approach: String,
    val name: String,
    val description: String,
    val answer: String
)

// Extension function to check auth and redirect if needed
private fun Response.checkAuthAndRedirect(): Response {
    if (this.status == 401.toShort()) {
        console.log("JWT token expired or invalid, redirecting to login...")
        localStorage.removeItem("jwt_token")
        window.location.href = "/"
    }
    return this
}

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
            ).await().checkAuthAndRedirect()

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
            ).await().checkAuthAndRedirect()

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

    suspend fun getChat(chatId: Int): Result<ChatResponse> {
        return try {
            val response = window.fetch(
                "$baseUrl/api/chats/$chatId",
                RequestInit(
                    method = "GET",
                    headers = getAuthHeaders()
                )
            ).await().checkAuthAndRedirect()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception("Failed to fetch chat: $errorText"))
            }

            val text = response.text().await()

            // Backend returns ChatWithMessagesDTO, extract chat object
            val chatData = json.parseToJsonElement(text).jsonObject
            val chatJson = chatData["chat"]?.toString() ?: "{}"
            val chat = json.decodeFromString<ChatResponse>(chatJson)

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
            ).await().checkAuthAndRedirect()

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
            ).await().checkAuthAndRedirect()

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
            ).await().checkAuthAndRedirect()

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
            ).await().checkAuthAndRedirect()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception("Failed to update chat title: $errorText"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateChatSettings(
        chatId: Int,
        temperature: Double?,
        maxTokens: Int?,
        topP: Double?,
        systemPrompt: String?,
        streaming: Boolean,
        responseFormat: String?,
        responseSchema: String?,
        responseStyle: String?,
        responseLength: String?,
        language: String?,
        includeExamples: Boolean?,
        contentFormat: String?,
        agentType: String?
    ): Result<Unit> {
        return try {
            // Always send all AI parameters to allow resetting to null
            val parts = mutableListOf<String>()

            parts.add(if (temperature != null) """"temperature":$temperature""" else """"temperature":null""")
            parts.add(if (maxTokens != null) """"maxTokens":$maxTokens""" else """"maxTokens":null""")
            parts.add(if (topP != null) """"topP":$topP""" else """"topP":null""")

            if (systemPrompt != null) {
                val escaped = systemPrompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                parts.add(""""systemPrompt":"$escaped"""")
            } else {
                parts.add(""""systemPrompt":null""")
            }

            parts.add(""""streaming":$streaming""")

            if (responseFormat != null) {
                parts.add(""""responseFormat":"$responseFormat"""")
            }

            if (responseSchema != null) {
                val escaped = responseSchema.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                parts.add(""""responseSchema":"$escaped"""")
            } else {
                parts.add(""""responseSchema":null""")
            }

            // General text settings
            if (responseStyle != null) {
                parts.add(""""responseStyle":"$responseStyle"""")
            }

            if (responseLength != null) {
                parts.add(""""responseLength":"$responseLength"""")
            }

            if (language != null) {
                parts.add(""""language":"$language"""")
            }

            if (includeExamples != null) {
                parts.add(""""includeExamples":$includeExamples""")
            }

            if (contentFormat != null) {
                parts.add(""""contentFormat":"$contentFormat"""")
            }

            if (agentType != null) {
                parts.add(""""agentType":"$agentType"""")
            }

            val requestBody = "{${parts.joinToString(",")}}"

            val response = window.fetch(
                "$baseUrl/api/chats/$chatId",
                RequestInit(
                    method = "PATCH",
                    headers = getAuthHeaders(),
                    body = requestBody
                )
            ).await().checkAuthAndRedirect()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception("Failed to update chat settings: $errorText"))
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
            ).await().checkAuthAndRedirect()

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

    suspend fun sendMessageStreaming(
        chatId: Int,
        content: String,
        onChunk: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val requestBody = json.encodeToString(
                SendMessageRequest.serializer(),
                SendMessageRequest(content)
            )

            val response = window.fetch(
                "$baseUrl/api/chats/$chatId/messages/stream",
                RequestInit(
                    method = "POST",
                    headers = getAuthHeaders(),
                    body = requestBody
                )
            ).await().checkAuthAndRedirect()

            if (!response.ok) {
                val errorText = response.text().await()
                onError("Failed to send message: $errorText")
                return
            }

            // Read the SSE stream
            val reader = response.body?.getReader()
            if (reader == null) {
                onError("No response body")
                return
            }

            val decoder = js("new TextDecoder()")
            var buffer = ""

            val readerDynamic: dynamic = reader
            while (true) {
                @Suppress("UNCHECKED_CAST")
                val readResult = readerDynamic.read() as Promise<Any?>
                val result: dynamic = readResult.await()
                val done = result.done as Boolean

                if (done) {
                    break
                }

                val chunk = result.value.unsafeCast<Uint8Array>()
                val text = decoder.decode(chunk, js("{stream: true}")) as String
                console.log("[SSE] Received raw chunk, length:", text.length, "content:", text.take(100))
                buffer += text

                // Process complete SSE events
                val lines = buffer.split("\n")
                buffer = lines.last() // Keep incomplete line in buffer

                var i = 0
                while (i < lines.size - 1) {
                    val line = lines[i]

                    if (line.startsWith("event: message") && i + 1 < lines.size) {
                        // Next line should be data
                        val dataLine = lines[i + 1]
                        if (dataLine.startsWith("data: ")) {
                            val data = dataLine.substring(6)
                            console.log("[SSE] Calling onChunk with:", data)
                            onChunk(data)
                            // Add small delay to allow UI to render incrementally
                            kotlinx.coroutines.delay(15)
                            i += 2  // Skip both event and data lines
                            continue
                        }
                    } else if (line.startsWith("event: done")) {
                        console.log("[SSE] Stream done")
                        onComplete()
                        return
                    } else if (line.startsWith("event: error") && i + 1 < lines.size) {
                        val dataLine = lines[i + 1]
                        if (dataLine.startsWith("data: ")) {
                            val error = dataLine.substring(6)
                            console.log("[SSE] Error:", error)
                            onError(error)
                            return
                        }
                    }
                    i++
                }
            }

            onComplete()
        } catch (e: Exception) {
            onError("Error: ${e.message}")
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
            ).await().checkAuthAndRedirect()

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

    // Admin API methods
    suspend fun getUsers(): Result<List<AdminUser>> {
        return try {
            val response = window.fetch(
                "$baseUrl/api/admin/users",
                RequestInit(
                    method = "GET",
                    headers = getAuthHeaders()
                )
            ).await().checkAuthAndRedirect()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception("Failed to fetch users: $errorText"))
            }

            val text = response.text().await()
            val users = json.decodeFromString<List<UserDTO>>(text)
            val adminUsers = users.map {
                AdminUser(it.id, it.email, it.isAdmin, it.allowedProviders, it.requestLimit, it.requestCount)
            }
            Result.success(adminUsers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createUser(email: String, password: String, allowedProviders: String, requestLimit: Int): Result<AdminUser> {
        return try {
            val requestBody = """{"email":"$email","password":"$password","allowedProviders":"$allowedProviders","requestLimit":$requestLimit}"""

            val response = window.fetch(
                "$baseUrl/api/admin/users",
                RequestInit(
                    method = "POST",
                    headers = getAuthHeaders(),
                    body = requestBody
                )
            ).await().checkAuthAndRedirect()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception(errorText))
            }

            val text = response.text().await()
            val user = json.decodeFromString<UserDTO>(text)
            Result.success(AdminUser(user.id, user.email, user.isAdmin, user.allowedProviders, user.requestLimit, user.requestCount))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUser(userId: Int, isAdmin: Boolean, allowedProviders: String, requestLimit: Int): Result<AdminUser> {
        return try {
            val requestBody = """{"isAdmin":$isAdmin,"allowedProviders":"$allowedProviders","requestLimit":$requestLimit}"""

            val response = window.fetch(
                "$baseUrl/api/admin/users/$userId",
                RequestInit(
                    method = "PUT",
                    headers = getAuthHeaders(),
                    body = requestBody
                )
            ).await().checkAuthAndRedirect()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception(errorText))
            }

            val text = response.text().await()
            val user = json.decodeFromString<UserDTO>(text)
            Result.success(AdminUser(user.id, user.email, user.isAdmin, user.allowedProviders, user.requestLimit, user.requestCount))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUser(userId: Int): Result<Unit> {
        return try {
            val response = window.fetch(
                "$baseUrl/api/admin/users/$userId",
                RequestInit(
                    method = "DELETE",
                    headers = getAuthHeaders()
                )
            ).await().checkAuthAndRedirect()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception(errorText))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetUserRequests(userId: Int): Result<Unit> {
        return try {
            val response = window.fetch(
                "$baseUrl/api/admin/users/$userId/reset-requests",
                RequestInit(
                    method = "POST",
                    headers = getAuthHeaders()
                )
            ).await().checkAuthAndRedirect()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception(errorText))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSingleApproach(task: String, approach: String, provider: String = "deepseek"): Result<SingleApproachResponse> {
        return try {
            val requestBody = json.encodeToString(
                SingleApproachRequest.serializer(),
                SingleApproachRequest(task, approach, provider)
            )

            val response = window.fetch(
                "$baseUrl/api/chats/single-approach",
                RequestInit(
                    method = "POST",
                    headers = getAuthHeaders(),
                    body = requestBody
                )
            ).await().checkAuthAndRedirect()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception(errorText))
            }

            val responseText = response.text().await()
            val result = json.decodeFromString<SingleApproachResponse>(responseText)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun compareReasoningApproaches(task: String, provider: String = "deepseek"): Result<CompareReasoningResponse> {
        return try {
            val requestBody = json.encodeToString(
                CompareReasoningRequest.serializer(),
                CompareReasoningRequest(task, provider)
            )

            val response = window.fetch(
                "$baseUrl/api/chats/compare-reasoning",
                RequestInit(
                    method = "POST",
                    headers = getAuthHeaders(),
                    body = requestBody
                )
            ).await().checkAuthAndRedirect()

            if (!response.ok) {
                val errorText = response.text().await()
                return Result.failure(Exception(errorText))
            }

            val responseText = response.text().await()
            val result = json.decodeFromString<CompareReasoningResponse>(responseText)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
