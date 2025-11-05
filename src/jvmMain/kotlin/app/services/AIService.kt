package app.services

import app.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object AIService {
    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = 300_000  // 5 minutes for streaming
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.BODY
            sanitizeHeader { header -> header == "Authorization" || header == "x-api-key" }
        }

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    data class AIParameters(
        val temperature: Double? = null,
        val maxTokens: Int? = null,
        val topP: Double? = null,
        val systemPrompt: String? = null,
        val streaming: Boolean = false,
        val responseFormat: String = "none",
        val responseSchema: String? = null,
        val responseStyle: String = "professional",
        val responseLength: String = "standard",
        val language: String = "auto",
        val includeExamples: Boolean = false,
        val contentFormat: String = "paragraphs"
    )

    suspend fun sendMessage(
        provider: String,
        messages: List<Message>,
        parameters: AIParameters = AIParameters()
    ): String {
        return when (provider) {
            "claude" -> sendClaudeMessage(messages, parameters)
            "deepseek" -> sendDeepSeekMessage(messages, parameters)
            else -> throw IllegalArgumentException("Unknown provider: $provider")
        }
    }

    private suspend fun sendClaudeMessage(messages: List<Message>, parameters: AIParameters): String {
        val apiKey = System.getenv("CLAUDE_API_KEY")
            ?: throw IllegalStateException("CLAUDE_API_KEY environment variable is not set")

        val request = AnthropicRequest(
            model = "claude-3-5-sonnet-20241022",
            messages = messages,
            maxTokens = parameters.maxTokens ?: 8192,
            temperature = parameters.temperature,
            topP = parameters.topP,
            system = parameters.systemPrompt,
            stream = parameters.streaming
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

    private suspend fun sendDeepSeekMessage(messages: List<Message>, parameters: AIParameters): String {
        val apiKey = System.getenv("DEEPSEEK_API_KEY")
            ?: throw IllegalStateException("DEEPSEEK_API_KEY environment variable is not set")

        // Build system prompt with format and style instructions
        val systemPrompt = buildSystemPromptWithFormat(
            basePrompt = parameters.systemPrompt,
            format = parameters.responseFormat,
            schema = parameters.responseSchema,
            style = parameters.responseStyle,
            length = parameters.responseLength,
            language = parameters.language,
            includeExamples = parameters.includeExamples,
            contentFormat = parameters.contentFormat
        )

        // Add system prompt as first message if provided
        val messagesWithSystem = if (systemPrompt != null) {
            listOf(Message(role = "system", content = systemPrompt)) + messages
        } else {
            messages
        }

        // Set response format for JSON
        val responseFormat = if (parameters.responseFormat == "json") {
            ResponseFormat(type = "json_object")
        } else null

        val request = DeepSeekRequest(
            model = "deepseek-chat",
            messages = messagesWithSystem,
            maxTokens = parameters.maxTokens,
            temperature = parameters.temperature ?: 0.7,
            topP = parameters.topP,
            stream = parameters.streaming,
            responseFormat = responseFormat
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

    // Streaming methods
    fun sendMessageStreaming(
        provider: String,
        messages: List<Message>,
        parameters: AIParameters
    ): Flow<String> {
        return when (provider) {
            "claude" -> sendClaudeMessageStreaming(messages, parameters)
            "deepseek" -> sendDeepSeekMessageStreaming(messages, parameters)
            else -> throw IllegalArgumentException("Unknown provider: $provider")
        }
    }

    private fun sendClaudeMessageStreaming(
        messages: List<Message>,
        parameters: AIParameters
    ): Flow<String> = flow {
        val apiKey = System.getenv("CLAUDE_API_KEY")
            ?: throw IllegalStateException("CLAUDE_API_KEY environment variable is not set")

        val request = AnthropicRequest(
            model = "claude-3-5-sonnet-20241022",
            messages = messages,
            maxTokens = parameters.maxTokens ?: 8192,
            temperature = parameters.temperature,
            topP = parameters.topP,
            system = parameters.systemPrompt,
            stream = true
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

            // Parse SSE stream
            val channel = response.bodyAsChannel()
            val buffer = StringBuilder()

            while (!channel.isClosedForRead) {
                val chunk = channel.readUTF8Line() ?: break

                if (chunk.startsWith("data: ")) {
                    val jsonData = chunk.removePrefix("data: ")
                    if (jsonData == "[DONE]") break

                    try {
                        val jsonElement = Json.parseToJsonElement(jsonData)
                        val obj = jsonElement.jsonObject

                        // Handle content_block_delta events
                        if (obj["type"]?.jsonPrimitive?.content == "content_block_delta") {
                            val delta = obj["delta"]?.jsonObject
                            if (delta?.get("type")?.jsonPrimitive?.content == "text_delta") {
                                val text = delta["text"]?.jsonPrimitive?.content
                                if (text != null) {
                                    emit(text)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Skip malformed JSON
                        println("Failed to parse SSE chunk: $jsonData - ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed to call Claude streaming API: ${e.message}", e)
        }
    }

    private fun sendDeepSeekMessageStreaming(
        messages: List<Message>,
        parameters: AIParameters
    ): Flow<String> = flow {
        val apiKey = System.getenv("DEEPSEEK_API_KEY")
            ?: throw IllegalStateException("DEEPSEEK_API_KEY environment variable is not set")

        // Build system prompt with format and style instructions
        val systemPrompt = buildSystemPromptWithFormat(
            basePrompt = parameters.systemPrompt,
            format = parameters.responseFormat,
            schema = parameters.responseSchema,
            style = parameters.responseStyle,
            length = parameters.responseLength,
            language = parameters.language,
            includeExamples = parameters.includeExamples,
            contentFormat = parameters.contentFormat
        )

        // Add system prompt as first message if provided
        val messagesWithSystem = if (systemPrompt != null) {
            listOf(Message(role = "system", content = systemPrompt)) + messages
        } else {
            messages
        }

        // Set response format for JSON
        val responseFormat = if (parameters.responseFormat == "json") {
            ResponseFormat(type = "json_object")
        } else null

        val request = DeepSeekRequest(
            model = "deepseek-chat",
            messages = messagesWithSystem,
            maxTokens = parameters.maxTokens,
            temperature = parameters.temperature ?: 0.7,
            topP = parameters.topP,
            stream = true,
            responseFormat = responseFormat
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

            // Parse SSE stream (OpenAI format)
            val channel = response.bodyAsChannel()

            while (!channel.isClosedForRead) {
                val chunk = channel.readUTF8Line() ?: break
                println("[DeepSeek Stream] Received line: ${chunk.take(100)}")

                if (chunk.startsWith("data: ")) {
                    val jsonData = chunk.removePrefix("data: ")
                    if (jsonData == "[DONE]") {
                        println("[DeepSeek Stream] Received DONE signal")
                        break
                    }

                    try {
                        val jsonElement = Json.parseToJsonElement(jsonData)
                        val obj = jsonElement.jsonObject

                        // Extract content from delta - choices is an array
                        val choices = obj["choices"]?.jsonArray
                        if (choices != null && choices.isNotEmpty()) {
                            val firstChoice = choices[0].jsonObject
                            val delta = firstChoice["delta"]?.jsonObject
                            val content = delta?.get("content")?.jsonPrimitive?.content

                            if (content != null) {
                                println("[DeepSeek Stream] Emitting chunk: '$content'")
                                emit(content)
                            }
                        }
                    } catch (e: Exception) {
                        // Skip malformed JSON
                        println("Failed to parse SSE chunk: $jsonData - ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed to call DeepSeek streaming API: ${e.message}", e)
        }
    }

    /**
     * Builds system prompt with format and style instructions
     */
    private fun buildSystemPromptWithFormat(
        basePrompt: String?,
        format: String,
        schema: String?,
        style: String = "professional",
        length: String = "standard",
        language: String = "auto",
        includeExamples: Boolean = false,
        contentFormat: String = "paragraphs"
    ): String? {
        val instructions = mutableListOf<String>()

        // Add base prompt if provided
        if (basePrompt != null) {
            instructions.add(basePrompt)
        }

        // Response style
        val styleText = when (style) {
            "professional" -> "Maintain a professional and business-appropriate tone."
            "friendly" -> "Use a warm, friendly, and approachable tone."
            "formal" -> "Use formal language and maintain strict professionalism."
            "casual" -> "Use casual, conversational language."
            "academic" -> "Use academic language with proper terminology and citations where appropriate."
            "creative" -> "Be creative and expressive in your responses."
            else -> null
        }
        if (styleText != null) instructions.add(styleText)

        // Response length
        val lengthText = when (length) {
            "brief" -> "Keep responses brief (1-2 sentences)."
            "concise" -> "Keep responses concise (1 paragraph maximum)."
            "standard" -> "Provide standard-length responses (2-3 paragraphs)."
            "detailed" -> "Provide detailed, comprehensive responses."
            "comprehensive" -> "Provide exhaustive, in-depth responses covering all aspects."
            else -> null
        }
        if (lengthText != null) instructions.add(lengthText)

        // Language
        val languageText = when (language) {
            "russian" -> "Always respond in Russian language."
            "english" -> "Always respond in English language."
            "mixed" -> "You may use mixed Russian and English if appropriate."
            else -> null // auto-detect
        }
        if (languageText != null) instructions.add(languageText)

        // Include examples
        if (includeExamples) {
            instructions.add("Include relevant examples to illustrate your points.")
        }

        // Content format
        val formatText = when (contentFormat) {
            "bullets" -> "Format your response as bullet points or lists."
            "paragraphs" -> "Format your response in clear paragraphs."
            "steps" -> "Format your response as step-by-step instructions."
            "qa" -> "Format your response as questions and answers."
            "storytelling" -> "Present information in a narrative, storytelling format."
            else -> null
        }
        if (formatText != null) instructions.add(formatText)

        // Structured format (JSON/XML)
        if (format != "none") {
            val structuredInstructions = when (format) {
                "json" -> {
                    val defaultJsonSchema = """
{
  "content": "основной текст ответа",
  "summary": "краткое резюме (опционально, может быть пустой строкой если не нужно)",
  "key_points": ["важный пункт 1", "важный пункт 2", "..."],
  "metadata": {
    "confidence": "высокая|средняя|низкая",
    "category": "информация|инструкция|объяснение|совет|код|другое"
  }
}
                    """.trim()

                    val schemaText = if (schema != null) {
                        "\n\nYou must respond with JSON that matches this exact schema:\n$schema"
                    } else {
                        "\n\nYou must respond with JSON that matches this exact schema:\n$defaultJsonSchema"
                    }
                    "IMPORTANT: Your response must be in JSON format.$schemaText\nDo not include any text outside the JSON structure."
                }
                "xml" -> {
                    val schemaText = if (schema != null) {
                        "\n\nYou must respond with XML that matches this exact schema:\n$schema"
                    } else {
                        "\n\nYou must respond with valid XML only."
                    }
                    "IMPORTANT: Your response must be in XML format.$schemaText\nDo not include any text outside the XML structure."
                }
                else -> null
            }
            if (structuredInstructions != null) instructions.add(structuredInstructions)
        }

        return if (instructions.isEmpty()) null else instructions.joinToString("\n\n")
    }
}
