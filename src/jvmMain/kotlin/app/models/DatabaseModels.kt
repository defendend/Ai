package app.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

// Database Tables
object Users : IntIdTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
}

object Chats : IntIdTable("chats") {
    val userId = reference("user_id", Users)
    val title = varchar("title", 255)
    val provider = varchar("provider", 50) // "claude" or "deepseek"
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())

    // AI Parameters
    val temperature = double("temperature").nullable().default(null)
    val maxTokens = integer("max_tokens").nullable().default(null)
    val topP = double("top_p").nullable().default(null)
    val systemPrompt = text("system_prompt").nullable().default(null)
    val streaming = bool("streaming").default(true) // Enable streaming by default
}

object Messages : IntIdTable("messages") {
    val chatId = reference("chat_id", Chats)
    val role = varchar("role", 20) // "user" or "assistant"
    val content = text("content")
    val timestamp = timestamp("timestamp").defaultExpression(CurrentTimestamp())
}

// DTO classes for API responses
@Serializable
data class UserDTO(
    val id: Int,
    val email: String
)

@Serializable
data class ChatDTO(
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
    val streaming: Boolean = true
)

@Serializable
data class MessageDTO(
    val id: Int,
    val role: String,
    val content: String,
    val timestamp: String
)

@Serializable
data class ChatWithMessagesDTO(
    val chat: ChatDTO,
    val messages: List<MessageDTO>
)

// Request models
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserDTO
)

@Serializable
data class CreateChatRequest(
    val title: String,
    val provider: String
)

@Serializable
data class UpdateChatRequest(
    val provider: String? = null,
    val title: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val systemPrompt: String? = null,
    val streaming: Boolean? = null
)

@Serializable
data class SendMessageRequest(
    val content: String
)

@Serializable
data class ErrorResponse(
    val error: String
)

// Extension functions to convert database rows to DTOs
fun ResultRow.toUserDTO() = UserDTO(
    id = this[Users.id].value,
    email = this[Users.email]
)

fun ResultRow.toChatDTO(messageCount: Int = 0, lastMessage: String? = null) = ChatDTO(
    id = this[Chats.id].value,
    title = this[Chats.title],
    provider = this[Chats.provider],
    messageCount = messageCount,
    lastMessage = lastMessage,
    createdAt = this[Chats.createdAt].toString(),
    updatedAt = this[Chats.updatedAt].toString(),
    temperature = this[Chats.temperature],
    maxTokens = this[Chats.maxTokens],
    topP = this[Chats.topP],
    systemPrompt = this[Chats.systemPrompt],
    streaming = this[Chats.streaming]
)

fun ResultRow.toMessageDTO() = MessageDTO(
    id = this[Messages.id].value,
    role = this[Messages.role],
    content = this[Messages.content],
    timestamp = this[Messages.timestamp].toString()
)
