package app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class AnthropicRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    val system: String? = null,
    val stream: Boolean = false
)

@Serializable
data class ContentBlock(
    val type: String,
    val text: String
)

@Serializable
data class AnthropicResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    @SerialName("stop_reason")
    val stopReason: String? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = 0
)

@Serializable
data class Chat(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val provider: String = "claude",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

// DeepSeek / OpenAI compatible models
@Serializable
data class DeepSeekRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null
)

@Serializable
data class DeepSeekChoice(
    val index: Int,
    val message: Message,
    @SerialName("finish_reason")
    val finishReason: String
)

@Serializable
data class DeepSeekResponse(
    val id: String,
    val choices: List<DeepSeekChoice>,
    val model: String
)
