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
data class ResponseFormat(
    val type: String // "json_object" or "text"
)

@Serializable
data class DeepSeekRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    val stream: Boolean = false,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null
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

// HuggingFace Router API (OpenAI-compatible format)
@Serializable
data class HuggingFaceRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    val stream: Boolean = false
)

@Serializable
data class HuggingFaceChoice(
    val message: Message,
    @SerialName("finish_reason")
    val finishReason: String? = null,
    val index: Int? = null
)

@Serializable
data class HuggingFaceUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null
)

@Serializable
data class HuggingFaceResponse(
    val id: String? = null,
    val choices: List<HuggingFaceChoice>,
    val model: String? = null,
    val created: Long? = null,
    val usage: HuggingFaceUsage? = null
)

// Model comparison
@Serializable
data class ModelMetrics(
    val modelName: String,
    val modelSize: String, // e.g., "82M", "1.7B", "7B"
    val responseTimeMs: Long,
    val tokensGenerated: Int?,
    val estimatedCost: Double?, // USD
    val response: String,
    val error: String? = null
)

@Serializable
data class ModelComparisonRequest(
    val prompt: String,
    val models: List<String> // model IDs from HuggingFace
)

@Serializable
data class ModelComparisonResult(
    val prompt: String,
    val timestamp: Long,
    val results: List<ModelMetrics>
)

@Serializable
data class AvailableModelsResponse(
    val models: List<String>
)

@Serializable
data class TestModelRequest(
    val prompt: String
)

@Serializable
data class ErrorResponseSimple(
    val error: String
)
