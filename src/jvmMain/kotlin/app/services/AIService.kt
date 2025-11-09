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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object AIService {
    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = 300_000  // 5 minutes for streaming

            // Connection pool settings for parallel requests
            endpoint {
                connectTimeout = 60_000  // 60 seconds to establish connection
                socketTimeout = 300_000  // 5 minutes for socket read
                connectAttempts = 3      // Retry connection 3 times
                keepAliveTime = 60_000   // Keep connections alive for 60 seconds
                maxConnectionsCount = 10 // Allow up to 10 parallel connections
            }
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
        val contentFormat: String = "paragraphs",
        val reasoningMode: String = "direct" // direct, step_by_step, meta_prompt, expert_panel
    )

    data class ComparisonResult(
        val task: String,
        val directAnswer: String,
        val expertPanelSingleRequest: String,
        val expertPanelTwoRequests: String,
        val expertPanelChain: String
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
            contentFormat = parameters.contentFormat,
            reasoningMode = parameters.reasoningMode
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
            contentFormat = parameters.contentFormat,
            reasoningMode = parameters.reasoningMode
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
     * Get single reasoning approach result
     */
    suspend fun getSingleApproach(
        provider: String,
        task: String,
        approach: String,
        parameters: AIParameters = AIParameters()
    ): String {
        val baseMessages = listOf(Message(role = "user", content = task))

        return when (approach) {
            "direct" -> sendMessage(provider, baseMessages, parameters.copy(reasoningMode = "direct"))
            "single" -> sendMessageWithExpertPanelSingleRequest(provider, task, parameters)
            "two" -> sendMessageWithExpertPanelTwoRequests(provider, task, parameters)
            "chain" -> sendMessageWithExpertPanelChain(provider, task, parameters)
            else -> throw IllegalArgumentException("Unknown approach: $approach")
        }
    }

    /**
     * Compare different reasoning approaches for a given task
     */
    suspend fun compareReasoningApproaches(
        provider: String,
        task: String,
        parameters: AIParameters = AIParameters()
    ): ComparisonResult {
        val baseMessages = listOf(Message(role = "user", content = task))

        // Run all approaches in parallel for speed
        val results = coroutineScope {
            val deferreds = listOf(
                // 1. Direct answer
                async {
                    sendMessage(provider, baseMessages, parameters.copy(reasoningMode = "direct"))
                },
                // 2. Expert panel - single request (all experts + moderator in one call)
                async {
                    sendMessageWithExpertPanelSingleRequest(provider, task, parameters)
                },
                // 3. Expert panel - two requests (experts in one, moderator in another)
                async {
                    sendMessageWithExpertPanelTwoRequests(provider, task, parameters)
                },
                // 4. Expert panel - chain (each expert separate + validation + moderator)
                async {
                    sendMessageWithExpertPanelChain(provider, task, parameters)
                }
            )
            deferreds.map { it.await() }
        }

        return ComparisonResult(
            task = task,
            directAnswer = results[0],
            expertPanelSingleRequest = results[1],
            expertPanelTwoRequests = results[2],
            expertPanelChain = results[3]
        )
    }

    /**
     * Send message with meta-prompting: AI creates optimized prompt first
     */
    private suspend fun sendMessageWithMetaPrompt(
        provider: String,
        task: String,
        parameters: AIParameters
    ): String {
        // Step 1: Ask AI to create an optimized prompt
        val metaPromptRequest = """
Ты эксперт по созданию промптов для AI.

Задача пользователя: "$task"

Создай оптимальный промпт для решения этой задачи. Промпт должен:
- Быть чётким и структурированным
- Включать необходимый контекст
- Направлять AI к лучшему решению
- Использовать эффективные техники промптинга

Ответь только текстом промпта, без дополнительных пояснений.
        """.trimIndent()

        val optimizedPrompt = sendMessage(
            provider,
            listOf(Message(role = "user", content = metaPromptRequest)),
            parameters.copy(temperature = 0.3)
        )

        // Step 2: Use optimized prompt to solve the task
        return sendMessage(
            provider,
            listOf(Message(role = "user", content = optimizedPrompt)),
            parameters
        )
    }

    /**
     * Approach 2: Expert panel - single request
     * All experts and moderator in one AI call
     */
    private suspend fun sendMessageWithExpertPanelSingleRequest(
        provider: String,
        task: String,
        parameters: AIParameters
    ): String {
        val expertPanelPrompt = """
Ты модератор панели экспертов. Перед тобой задача, которую нужно решить коллективно.

Задача: "$task"

Создай панель из 3-4 экспертов с разными специализациями, подходящими для этой задачи.
Каждый эксперт должен:
1. Указать свою специализацию
2. Дать своё решение задачи
3. Объяснить свой подход

После того как все эксперты выскажутся, ты как модератор должен:
- Проанализировать все предложенные решения
- Выделить сильные стороны каждого подхода
- Синтезировать финальное решение, объединяющее лучшие идеи

Формат ответа:
---
### Эксперт 1: [специализация]
[Решение эксперта 1]

### Эксперт 2: [специализация]
[Решение эксперта 2]

### Эксперт 3: [специализация]
[Решение эксперта 3]

---
### Итоговое решение (Модератор)
[Синтез лучших идей от всех экспертов]
        """.trimIndent()

        return sendMessage(
            provider,
            listOf(Message(role = "user", content = expertPanelPrompt)),
            parameters.copy(maxTokens = parameters.maxTokens ?: 8192)
        )
    }

    /**
     * Approach 3: Expert panel - two requests
     * First request: create experts and their answers
     * Second request: moderator synthesizes the answers
     */
    private suspend fun sendMessageWithExpertPanelTwoRequests(
        provider: String,
        task: String,
        parameters: AIParameters
    ): String {
        // Step 1: Create experts and get their answers
        val expertsPrompt = """
Ты AI-система, которая создаёт панель экспертов для решения задачи.

Задача: "$task"

Создай панель из 3-4 экспертов с разными специализациями, подходящими для этой задачи.
Каждый эксперт должен:
1. Указать свою специализацию
2. Дать своё решение задачи
3. Объяснить свой подход

Формат ответа:
### Эксперт 1: [специализация]
[Решение эксперта 1]

### Эксперт 2: [специализация]
[Решение эксперта 2]

### Эксперт 3: [специализация]
[Решение эксперта 3]
        """.trimIndent()

        val expertsAnswers = sendMessage(
            provider,
            listOf(Message(role = "user", content = expertsPrompt)),
            parameters.copy(maxTokens = parameters.maxTokens ?: 8192)
        )

        // Step 2: Moderator synthesizes the answers
        val moderatorPrompt = """
Ты модератор панели экспертов. Эксперты уже высказались по задаче.

Задача: "$task"

Ответы экспертов:
$expertsAnswers

Твоя задача как модератора:
- Проанализировать все предложенные решения
- Выделить сильные стороны каждого подхода
- Синтезировать финальное решение, объединяющее лучшие идеи

Дай только итоговое решение модератора. Формат:
### Итоговое решение (Модератор)
[Синтез лучших идей от всех экспертов]
        """.trimIndent()

        val moderatorAnswer = sendMessage(
            provider,
            listOf(Message(role = "user", content = moderatorPrompt)),
            parameters.copy(maxTokens = parameters.maxTokens ?: 8192)
        )

        // Combine expert answers with moderator synthesis
        return """
$expertsAnswers

---
$moderatorAnswer
        """.trimIndent()
    }

    /**
     * Approach 4: Expert panel - chain
     * Each expert as separate request + validation + moderator synthesis
     */
    private suspend fun sendMessageWithExpertPanelChain(
        provider: String,
        task: String,
        parameters: AIParameters
    ): String {
        try {
            // Step 1: Determine expert specializations
            val specializationsPrompt = """
Для решения следующей задачи определи 3-4 подходящие специализации экспертов.

Задача: "$task"

Ответь списком специализаций, по одной на строку. Только специализации, без номеров и дополнительного текста.
            """.trimIndent()

            println("[Chain] Step 1: Getting specializations...")
            val specializationsResponse = sendMessage(
                provider,
                listOf(Message(role = "user", content = specializationsPrompt)),
                parameters.copy(maxTokens = 512, temperature = 0.5)
            )

            val specializations = specializationsResponse
                .lines()
                .filter { it.isNotBlank() }
                .take(4)

            println("[Chain] Found ${specializations.size} specializations: $specializations")

            // Step 2: Get answer from each expert separately with validation
            val expertAnswers = mutableListOf<String>()

            for ((index, specialization) in specializations.withIndex()) {
                val expertNumber = index + 1

                try {
                    // Get expert's answer
                    val expertPrompt = """
Ты эксперт со специализацией: $specialization

Задача: "$task"

Дай своё решение задачи, используя свой профессиональный опыт и специализацию.
Объясни свой подход и почему он эффективен для этой задачи.

Формат ответа:
### Эксперт $expertNumber: $specialization
[Твоё решение и объяснение подхода]
                    """.trimIndent()

                    println("[Chain] Step 2.$expertNumber: Getting expert answer for '$specialization'...")
                    val expertAnswer = sendMessage(
                        provider,
                        listOf(Message(role = "user", content = expertPrompt)),
                        parameters.copy(maxTokens = 8192, temperature = 0.7)
                    )

                    // Validate expert's answer
                    val validationPrompt = """
Проверь следующий ответ эксперта на качество и полноту:

$expertAnswer

Задача была: "$task"

Оцени:
1. Отвечает ли решение на задачу?
2. Использует ли эксперт свою специализацию?
3. Достаточно ли полное решение?

Если ответ качественный, просто верни его как есть.
Если есть недостатки, верни улучшенную версию с сохранением специализации эксперта.
                    """.trimIndent()

                    println("[Chain] Step 2.$expertNumber: Validating expert answer...")
                    val validatedAnswer = sendMessage(
                        provider,
                        listOf(Message(role = "user", content = validationPrompt)),
                        parameters.copy(maxTokens = 8192, temperature = 0.3)
                    )

                    expertAnswers.add(validatedAnswer)
                    println("[Chain] Step 2.$expertNumber: Expert answer validated and added")
                } catch (e: Exception) {
                    println("[Chain] ERROR in expert $expertNumber: ${e.message}")
                    throw Exception("Failed to process expert $expertNumber ($specialization): ${e.message}", e)
                }
            }

            // Step 3: Moderator synthesizes all answers
            val allExpertsText = expertAnswers.joinToString("\n\n")

            val moderatorPrompt = """
Ты модератор панели экспертов. Все эксперты высказались по задаче.

Задача: "$task"

Ответы экспертов:
$allExpertsText

Твоя задача как модератора:
- Проанализировать все предложенные решения
- Выделить сильные стороны каждого подхода
- Выявить общие паттерны и различия
- Синтезировать финальное решение, объединяющее лучшие идеи
- Дать конкретные рекомендации

Дай только итоговое решение модератора. Формат:
### Итоговое решение (Модератор)

**Анализ подходов:**
[Анализ сильных сторон каждого подхода]

**Финальное решение:**
[Синтез лучших идей от всех экспертов]

**Рекомендации:**
[Конкретные шаги к реализации]
            """.trimIndent()

            println("[Chain] Step 3: Getting moderator synthesis...")
            val moderatorAnswer = sendMessage(
                provider,
                listOf(Message(role = "user", content = moderatorPrompt)),
                parameters.copy(maxTokens = parameters.maxTokens ?: 8192)
            )

            println("[Chain] Step 3: Moderator synthesis complete")

            // Combine all expert answers with moderator synthesis
            return """
$allExpertsText

---
$moderatorAnswer
            """.trimIndent()
        } catch (e: Exception) {
            println("[Chain] FATAL ERROR: ${e.message}")
            throw Exception("Expert Panel Chain failed: ${e.message}", e)
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
        contentFormat: String = "paragraphs",
        reasoningMode: String = "direct"
    ): String? {
        val instructions = mutableListOf<String>()

        // Add base prompt if provided
        if (basePrompt != null) {
            instructions.add(basePrompt)
        }

        // Reasoning mode instructions
        val reasoningText = when (reasoningMode) {
            "step_by_step" -> """
ВАЖНО: Решай задачу пошагово:
1. Разбей задачу на логические шаги
2. Рассуждай по каждому шагу
3. Покажи промежуточные выводы
4. Дай финальный ответ

Используй формат:
**Шаг 1:** [описание]
**Рассуждение:** [твои мысли]
**Вывод:** [промежуточный результат]

**Шаг 2:** ...

**Финальный ответ:** [итоговое решение]
            """.trimIndent()
            "direct" -> null
            else -> null
        }
        if (reasoningText != null) instructions.add(reasoningText)

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
  "title": "краткий заголовок ответа",
  "content": "основной текст ответа",
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
                    val defaultXmlSchema = """
<response>
  <title>краткий заголовок ответа</title>
  <content>основной текст ответа</content>
  <metadata>
    <confidence>высокая|средняя|низкая</confidence>
    <category>информация|инструкция|объяснение|совет|код|другое</category>
  </metadata>
</response>
                    """.trim()

                    val schemaText = if (schema != null) {
                        "\n\nYou must respond with XML that matches this exact schema:\n$schema"
                    } else {
                        "\n\nYou must respond with XML that matches this exact schema:\n$defaultXmlSchema"
                    }
                    "IMPORTANT: Your response must be in XML format.$schemaText\nDo not include any text outside the XML structure."
                }
                "html" -> {
                    val defaultHtmlSchema = """
<div class="ai-response">
  <h3 class="response-title">краткий заголовок ответа</h3>
  <div class="response-content">
    <p>основной текст ответа</p>
  </div>
  <div class="response-metadata">
    <span class="confidence">высокая|средняя|низкая</span>
    <span class="category">информация|инструкция|объяснение|совет|код|другое</span>
  </div>
</div>
                    """.trim()

                    val schemaText = if (schema != null) {
                        "\n\nYou must respond with HTML that matches this exact structure:\n$schema"
                    } else {
                        "\n\nYou must respond with HTML that matches this exact structure:\n$defaultHtmlSchema"
                    }
                    "IMPORTANT: Your response must be in HTML format.$schemaText\nUse semantic HTML tags and appropriate CSS classes. Do not include <!DOCTYPE>, <html>, <head>, or <body> tags - only the content fragment."
                }
                else -> null
            }
            if (structuredInstructions != null) instructions.add(structuredInstructions)
        }

        return if (instructions.isEmpty()) null else instructions.joinToString("\n\n")
    }
}
