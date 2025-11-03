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
    val maxTokens: Int,
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

data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = js("Date.now()").unsafeCast<Long>()
)
