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
    val isAdmin = bool("is_admin").default(false)
    val allowedProviders = text("allowed_providers").default("deepseek,claude") // Comma-separated list
    val requestLimit = integer("request_limit").default(100) // Max requests per day
    val requestCount = integer("request_count").default(0) // Current daily request count
    val lastRequestReset = timestamp("last_request_reset").defaultExpression(CurrentTimestamp())
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

    // Response format settings
    val responseFormat = varchar("response_format", 10).default("none") // "none", "json", "xml"
    val responseSchema = text("response_schema").nullable().default(null) // JSON/XML schema

    // General text response settings
    val responseStyle = varchar("response_style", 20).default("professional") // professional, friendly, formal, casual, academic, creative
    val responseLength = varchar("response_length", 20).default("standard") // brief, concise, standard, detailed, comprehensive
    val language = varchar("language", 20).default("auto") // auto, russian, english, mixed
    val includeExamples = bool("include_examples").default(false)
    val contentFormat = varchar("content_format", 20).default("paragraphs") // bullets, paragraphs, steps, qa, storytelling

    // Agent mode settings
    val agentType = varchar("agent_type", 30).default("none") // none, requirements_collector, interview_conductor, research_assistant, problem_solver
    val agentGoalAchieved = bool("agent_goal_achieved").default(false) // Whether agent has completed the goal
}

object Messages : IntIdTable("messages") {
    val chatId = reference("chat_id", Chats)
    val role = varchar("role", 20) // "user" or "assistant"
    val content = text("content")
    val timestamp = timestamp("timestamp").defaultExpression(CurrentTimestamp())
}

object TokenUsage : IntIdTable("token_usage") {
    val messageId = reference("message_id", Messages).nullable() // null for streaming messages
    val chatId = reference("chat_id", Chats)
    val userId = reference("user_id", Users)
    val provider = varchar("provider", 50) // "claude" or "deepseek"
    val model = varchar("model", 100)
    val promptTokens = integer("prompt_tokens")
    val completionTokens = integer("completion_tokens")
    val totalTokens = integer("total_tokens")
    val timestamp = timestamp("timestamp").defaultExpression(CurrentTimestamp())
}

// DTO classes for API responses
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
data class MessageDTO(
    val id: Int,
    val role: String,
    val content: String,
    val timestamp: String,
    val tokenUsage: TokenUsageDTO? = null
)

@Serializable
data class TokenUsageDTO(
    val id: Int,
    val provider: String,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
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
    val streaming: Boolean? = null,
    val responseFormat: String? = null,
    val responseSchema: String? = null,
    val responseStyle: String? = null,
    val responseLength: String? = null,
    val language: String? = null,
    val includeExamples: Boolean? = null,
    val contentFormat: String? = null,
    val agentType: String? = null,
    val agentGoalAchieved: Boolean? = null
)

@Serializable
data class SendMessageRequest(
    val content: String
)

@Serializable
data class ErrorResponse(
    val error: String
)

// Admin DTOs
@Serializable
data class CreateUserRequest(
    val email: String,
    val password: String,
    val allowedProviders: String = "deepseek,claude",
    val requestLimit: Int = 100
)

@Serializable
data class UpdateUserRequest(
    val isAdmin: Boolean? = null,
    val allowedProviders: String? = null,
    val requestLimit: Int? = null,
    val requestCount: Int? = null
)

// Extension functions to convert database rows to DTOs
fun ResultRow.toUserDTO() = UserDTO(
    id = this[Users.id].value,
    email = this[Users.email],
    isAdmin = this[Users.isAdmin],
    allowedProviders = this[Users.allowedProviders],
    requestLimit = this[Users.requestLimit],
    requestCount = this[Users.requestCount]
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
    streaming = this[Chats.streaming],
    responseFormat = this[Chats.responseFormat],
    responseSchema = this[Chats.responseSchema],
    responseStyle = this[Chats.responseStyle],
    responseLength = this[Chats.responseLength],
    language = this[Chats.language],
    includeExamples = this[Chats.includeExamples],
    contentFormat = this[Chats.contentFormat],
    agentType = this[Chats.agentType],
    agentGoalAchieved = this[Chats.agentGoalAchieved]
)

fun ResultRow.toMessageDTO() = MessageDTO(
    id = this[Messages.id].value,
    role = this[Messages.role],
    content = this[Messages.content],
    timestamp = this[Messages.timestamp].toString(),
    tokenUsage = null // Will be populated separately if needed
)

fun ResultRow.toTokenUsageDTO() = TokenUsageDTO(
    id = this[TokenUsage.id].value,
    provider = this[TokenUsage.provider],
    model = this[TokenUsage.model],
    promptTokens = this[TokenUsage.promptTokens],
    completionTokens = this[TokenUsage.completionTokens],
    totalTokens = this[TokenUsage.totalTokens],
    timestamp = this[TokenUsage.timestamp].toString()
)

// Reasoning comparison DTOs
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
    val approach: String, // "direct", "single", "two", "chain"
    val provider: String = "deepseek",
    val temperature: Double? = null
)

@Serializable
data class SingleApproachResponse(
    val task: String,
    val approach: String,
    val name: String,
    val description: String,
    val answer: String
)
