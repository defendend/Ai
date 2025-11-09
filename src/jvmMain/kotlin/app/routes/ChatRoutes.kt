package app.routes

import app.database.DatabaseFactory.dbQuery
import app.models.*
import app.services.AIService
import app.utils.SecurityUtils
import app.security.CsrfProtection
import app.agents.AgentPresets
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.DeleteStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager

private fun ApplicationCall.getUserId(): Int? {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.getClaim("userId")?.asInt()
}

private suspend fun checkAndIncrementRequestLimit(userId: Int): Pair<Boolean, String?> {
    return dbQuery {
        val user = Users.select { Users.id eq userId }.singleOrNull()
            ?: return@dbQuery Pair(false, "User not found")

        val requestLimit = user[Users.requestLimit]
        val requestCount = user[Users.requestCount]
        val lastReset = user[Users.lastRequestReset]

        // If limit is -1, requests are unlimited
        if (requestLimit == -1) {
            return@dbQuery Pair(true, null)
        }

        // Check if we need to reset the counter (once per day)
        val now = java.time.Instant.now()
        val lastResetInstant = lastReset.atZone(java.time.ZoneOffset.UTC).toInstant()
        val hoursSinceReset = java.time.Duration.between(lastResetInstant, now).toHours()

        if (hoursSinceReset >= 24) {
            // Reset the counter
            Users.update({ Users.id eq userId }) {
                it[Users.requestCount] = 1
                it[Users.lastRequestReset] = org.jetbrains.exposed.sql.javatime.CurrentTimestamp()
            }
            return@dbQuery Pair(true, null)
        }

        // Check if limit is reached
        if (requestCount >= requestLimit) {
            return@dbQuery Pair(false, "Request limit exceeded. Limit: $requestLimit, Used: $requestCount")
        }

        // Increment counter
        Users.update({ Users.id eq userId }) {
            it[Users.requestCount] = requestCount + 1
        }

        Pair(true, null)
    }
}

private suspend fun checkProviderAccess(userId: Int, provider: String): Pair<Boolean, String?> {
    return dbQuery {
        val user = Users.select { Users.id eq userId }.singleOrNull()
            ?: return@dbQuery Pair(false, "User not found")

        val allowedProviders = user[Users.allowedProviders].split(",").map { it.trim() }

        if (provider !in allowedProviders) {
            return@dbQuery Pair(false, "Access denied. Provider '$provider' is not allowed for this user. Allowed: ${allowedProviders.joinToString(", ")}")
        }

        Pair(true, null)
    }
}

private fun getEffectiveSystemPrompt(agentType: String, customSystemPrompt: String?): String? {
    val markdownInstructions = """

**IMPORTANT: Always format your responses using Markdown for better readability:**
- Use headers (## ### ####) to structure sections and categories
- Use numbered lists (1. 2. 3.) ONLY for sequential steps within ONE section
- Use bullet points (-) for non-sequential items or multiple unrelated points
- Use **bold** for emphasis and *italic* for secondary emphasis
- Break text into clear paragraphs with empty lines between them
- Use code blocks (```) for code or technical content
- Use checkboxes (- [ ]) for action items or tasks
- NEVER use "1. 1. 1." for different sections - use headers (### Section Name) instead
    """.trimIndent()

    // If agent mode is enabled, use agent's system prompt (already has formatting instructions)
    if (agentType != "none") {
        val preset = AgentPresets.getPreset(agentType)
        return preset?.systemPrompt
    }

    // For regular chat, add markdown instructions to custom prompt
    return if (customSystemPrompt.isNullOrBlank()) {
        markdownInstructions
    } else {
        "$customSystemPrompt\n\n$markdownInstructions"
    }
}

fun Route.chatRoutes() {
    authenticate("auth-jwt") {
        route("/api/chats") {
            // Get all chats for user
            get {
                val userId = call.getUserId()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                    return@get
                }

            try {
                val chats = dbQuery {
                    Chats.select { Chats.userId eq userId }
                        .orderBy(Chats.updatedAt to SortOrder.DESC)
                        .limit(10)
                        .map { chatRow ->
                            val chatId = chatRow[Chats.id].value
                            val messageCount = Messages.select { Messages.chatId eq chatId }.count().toInt()
                            val lastMessage = Messages.select { Messages.chatId eq chatId }
                                .orderBy(Messages.timestamp to SortOrder.DESC)
                                .limit(1)
                                .singleOrNull()
                                ?.get(Messages.content)
                                ?.take(50)

                            chatRow.toChatDTO(messageCount, lastMessage)
                        }
                }

                call.respond(chats)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to fetch chats: ${e.message}"))
            }
        }

            // Create new chat
            post {
                // CSRF Protection
                if (!CsrfProtection.isValidRequest(call)) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("CSRF validation failed"))
                    return@post
                }

                val userId = call.getUserId()
                val request = call.receive<CreateChatRequest>()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                    return@post
                }

                // Check if user has access to the requested provider
                val (providerAllowed, providerError) = checkProviderAccess(userId, request.provider)
                if (!providerAllowed) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse(providerError ?: "Provider access denied"))
                    return@post
                }

            try {
                // Sanitize input to prevent XSS
                val sanitizedTitle = SecurityUtils.sanitizeChatTitle(request.title)

                val chatId = dbQuery {
                    Chats.insert {
                        it[Chats.userId] = userId
                        it[title] = sanitizedTitle
                        it[provider] = request.provider
                    }[Chats.id].value
                }

                val chat = dbQuery {
                    Chats.select { Chats.id eq chatId }
                        .single()
                        .toChatDTO()
                }

                call.respond(HttpStatusCode.Created, chat)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create chat: ${e.message}"))
            }
        }

        // Update chat (provider and/or title)
        patch("/{chatId}") {
            // CSRF Protection
            if (!CsrfProtection.isValidRequest(call)) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("CSRF validation failed"))
                return@patch
            }

            val userId = call.getUserId()
            val chatId = call.parameters["chatId"]?.toIntOrNull()
            val request = call.receive<UpdateChatRequest>()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                return@patch
            }

            if (chatId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chat ID"))
                return@patch
            }

            // If provider is being updated, check access
            if (request.provider != null) {
                val (providerAllowed, providerError) = checkProviderAccess(userId, request.provider)
                if (!providerAllowed) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse(providerError ?: "Provider access denied"))
                    return@patch
                }
            }

            // Provider and title are optional updates
            // AI parameters are always updated (can be set to null to reset to defaults)
            // No validation needed - at least one field is implicitly provided

            try {
                // Sanitize input to prevent XSS
                val sanitizedTitle = request.title?.let { SecurityUtils.sanitizeChatTitle(it) }
                val sanitizedSystemPrompt = SecurityUtils.sanitizeSystemPrompt(request.systemPrompt)

                val updated = dbQuery {
                    val existing = Chats.select { (Chats.id eq chatId) and (Chats.userId eq userId) }.singleOrNull()
                    if (existing == null) return@dbQuery false

                    Chats.update({ (Chats.id eq chatId) and (Chats.userId eq userId) }) {
                        request.provider?.let { newProvider -> it[provider] = newProvider }
                        sanitizedTitle?.let { newTitle -> it[title] = newTitle }

                        // For AI parameters, always update even if null (to reset to default)
                        it[temperature] = request.temperature
                        it[maxTokens] = request.maxTokens
                        it[topP] = request.topP
                        it[systemPrompt] = sanitizedSystemPrompt
                        request.streaming?.let { streamingValue -> it[streaming] = streamingValue }

                        // Response format settings
                        request.responseFormat?.let { format -> it[responseFormat] = format }
                        it[responseSchema] = request.responseSchema

                        // General text response settings
                        request.responseStyle?.let { style -> it[responseStyle] = style }
                        request.responseLength?.let { length -> it[responseLength] = length }
                        request.language?.let { lang -> it[language] = lang }
                        request.includeExamples?.let { examples -> it[includeExamples] = examples }
                        request.contentFormat?.let { format -> it[contentFormat] = format }

                        // Agent settings
                        request.agentType?.let { type -> it[agentType] = type }
                        request.agentGoalAchieved?.let { achieved -> it[agentGoalAchieved] = achieved }
                    } > 0
                }

                if (updated) {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Chat not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update chat: ${e.message}"))
            }
        }

        // Delete chat
        delete("/{chatId}") {
            // CSRF Protection
            if (!CsrfProtection.isValidRequest(call)) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("CSRF validation failed"))
                return@delete
            }

            val userId = call.getUserId()
            val chatIdParam = call.parameters["chatId"]?.toIntOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                return@delete
            }

            if (chatIdParam == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chat ID"))
                return@delete
            }

            try {
                val deleted = dbQuery {
                    // Check if chat exists and belongs to user
                    val chatExists = Chats.select { (Chats.id eq chatIdParam) and (Chats.userId eq userId) }.count() > 0

                    if (chatExists) {
                        // Delete all messages in the chat (safe from SQL injection)
                        Messages.deleteWhere { Messages.chatId eq chatIdParam }

                        // Then delete the chat itself (safe from SQL injection)
                        Chats.deleteWhere { (Chats.id eq chatIdParam) and (Chats.userId eq userId) }
                        true
                    } else {
                        false
                    }
                }

                if (deleted) {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Chat not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete chat: ${e.message}"))
            }
        }

        // Get chat with messages
        get("/{chatId}") {
            val userId = call.getUserId()
            val chatId = call.parameters["chatId"]?.toIntOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                return@get
            }

            if (chatId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chat ID"))
                return@get
            }

            try {
                val result = dbQuery {
                    val chatRow = Chats.select { (Chats.id eq chatId) and (Chats.userId eq userId) }.singleOrNull()
                    if (chatRow == null) return@dbQuery null

                    val messages = Messages.select { Messages.chatId eq chatId }
                        .orderBy(Messages.timestamp to SortOrder.ASC)
                        .map { it.toMessageDTO() }

                    ChatWithMessagesDTO(
                        chat = chatRow.toChatDTO(messages.size),
                        messages = messages
                    )
                }

                if (result == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Chat not found"))
                } else {
                    call.respond(result)
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to fetch chat: ${e.message}"))
            }
        }

        // Send message to chat
        post("/{chatId}/messages") {
            // CSRF Protection
            if (!CsrfProtection.isValidRequest(call)) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("CSRF validation failed"))
                return@post
            }

            val userId = call.getUserId()
            val chatId = call.parameters["chatId"]?.toIntOrNull()
            val request = call.receive<SendMessageRequest>()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                return@post
            }

            if (chatId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chat ID"))
                return@post
            }

            try {
                // Check request limit
                val (allowed, errorMessage) = checkAndIncrementRequestLimit(userId)
                if (!allowed) {
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse(errorMessage ?: "Request limit exceeded"))
                    return@post
                }

                // Sanitize message content to prevent XSS
                val sanitizedContent = SecurityUtils.sanitizeMessageContent(request.content)

                // Get chat info and verify ownership
                val chat = dbQuery {
                    Chats.select { (Chats.id eq chatId) and (Chats.userId eq userId) }.singleOrNull()
                }

                if (chat == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Chat not found"))
                    return@post
                }

                // Check if user has access to the chat's provider
                val chatProvider = chat[Chats.provider]
                val (providerAllowed, providerError) = checkProviderAccess(userId, chatProvider)
                if (!providerAllowed) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse(providerError ?: "Provider access denied"))
                    return@post
                }

                // Save user message
                val userMessageId = dbQuery {
                    Messages.insert {
                        it[Messages.chatId] = chatId
                        it[role] = "user"
                        it[content] = sanitizedContent
                    }[Messages.id].value
                }

                // Get all messages for context
                val allMessages = dbQuery {
                    Messages.select { Messages.chatId eq chatId }
                        .orderBy(Messages.timestamp to SortOrder.ASC)
                        .map { app.Message(role = it[Messages.role], content = it[Messages.content]) }
                }

                // Call AI service
                val provider = chat[Chats.provider]
                val agentType = chat[Chats.agentType]
                val effectiveSystemPrompt = getEffectiveSystemPrompt(agentType, chat[Chats.systemPrompt])

                val parameters = AIService.AIParameters(
                    temperature = chat[Chats.temperature],
                    maxTokens = chat[Chats.maxTokens],
                    topP = chat[Chats.topP],
                    systemPrompt = effectiveSystemPrompt,
                    responseFormat = chat[Chats.responseFormat],
                    responseSchema = chat[Chats.responseSchema],
                    responseStyle = chat[Chats.responseStyle],
                    responseLength = chat[Chats.responseLength],
                    language = chat[Chats.language],
                    includeExamples = chat[Chats.includeExamples],
                    contentFormat = chat[Chats.contentFormat]
                )
                val aiResponse = AIService.sendMessage(provider, allMessages, parameters)

                // Check if agent goal was achieved
                val goalAchieved = if (agentType != "none") {
                    val preset = AgentPresets.getPreset(agentType)
                    preset?.completionMarker?.let { marker ->
                        aiResponse.contains(marker)
                    } ?: false
                } else false

                // Save AI response
                val assistantMessageId = dbQuery {
                    val msgId = Messages.insert {
                        it[Messages.chatId] = chatId
                        it[role] = "assistant"
                        it[content] = aiResponse
                    }[Messages.id].value

                    // Update chat updatedAt and agentGoalAchieved if needed
                    Chats.update({ Chats.id eq chatId }) {
                        it[updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentTimestamp()
                        if (goalAchieved) {
                            it[agentGoalAchieved] = true
                        }
                    }

                    msgId
                }

                // Return both messages
                val messages = dbQuery {
                    Messages.select { (Messages.id eq userMessageId) or (Messages.id eq assistantMessageId) }
                        .orderBy(Messages.timestamp to SortOrder.ASC)
                        .map { it.toMessageDTO() }
                }

                call.respond(messages)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to send message: ${e.message}"))
            }
        }

        // Send message with streaming (SSE)
        post("/{chatId}/messages/stream") {
            // CSRF Protection
            if (!CsrfProtection.isValidRequest(call)) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("CSRF validation failed"))
                return@post
            }

            val userId = call.getUserId()
            val chatId = call.parameters["chatId"]?.toIntOrNull()
            val request = call.receive<SendMessageRequest>()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                return@post
            }

            if (chatId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chat ID"))
                return@post
            }

            try {
                // Check request limit
                val (allowed, errorMessage) = checkAndIncrementRequestLimit(userId)
                if (!allowed) {
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse(errorMessage ?: "Request limit exceeded"))
                    return@post
                }

                // Sanitize message content to prevent XSS
                val sanitizedContent = SecurityUtils.sanitizeMessageContent(request.content)

                // Get chat info and verify ownership
                val chat = dbQuery {
                    Chats.select { (Chats.id eq chatId) and (Chats.userId eq userId) }.singleOrNull()
                }

                if (chat == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Chat not found"))
                    return@post
                }

                // Check if user has access to the chat's provider
                val chatProvider = chat[Chats.provider]
                val (providerAllowed, providerError) = checkProviderAccess(userId, chatProvider)
                if (!providerAllowed) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse(providerError ?: "Provider access denied"))
                    return@post
                }

                // Save user message
                val userMessageId = dbQuery {
                    Messages.insert {
                        it[Messages.chatId] = chatId
                        it[role] = "user"
                        it[content] = sanitizedContent
                    }[Messages.id].value
                }

                // Get all messages for context
                val allMessages = dbQuery {
                    Messages.select { Messages.chatId eq chatId }
                        .orderBy(Messages.timestamp to SortOrder.ASC)
                        .map { app.Message(role = it[Messages.role], content = it[Messages.content]) }
                }

                // Call AI service with streaming
                val provider = chat[Chats.provider]
                val agentType = chat[Chats.agentType]
                val effectiveSystemPrompt = getEffectiveSystemPrompt(agentType, chat[Chats.systemPrompt])

                val parameters = AIService.AIParameters(
                    temperature = chat[Chats.temperature],
                    maxTokens = chat[Chats.maxTokens],
                    topP = chat[Chats.topP],
                    systemPrompt = effectiveSystemPrompt,
                    streaming = true,
                    responseFormat = chat[Chats.responseFormat],
                    responseSchema = chat[Chats.responseSchema],
                    responseStyle = chat[Chats.responseStyle],
                    responseLength = chat[Chats.responseLength],
                    language = chat[Chats.language],
                    includeExamples = chat[Chats.includeExamples],
                    contentFormat = chat[Chats.contentFormat]
                )

                // Set SSE headers before responding
                call.response.header(HttpHeaders.CacheControl, "no-cache")
                call.response.header("X-Accel-Buffering", "no")

                // Collect all chunks to save as complete message
                val fullResponse = StringBuilder()

                // Stream the response
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    try {
                        // Send initial comment to establish connection immediately
                        write(": connected\n\n")
                        flush()

                        AIService.sendMessageStreaming(provider, allMessages, parameters)
                            .catch { e ->
                                // Ignore cancellation - connection closed by client
                                if (e is kotlinx.coroutines.CancellationException) {
                                    println("Streaming cancelled (client disconnected)")
                                    return@catch
                                }
                                println("Error in streaming flow: ${e.message}")
                                e.printStackTrace()
                                // Send error event
                                write("event: error\n")
                                write("data: ${e.message}\n\n")
                                flush()
                            }
                            .collect { chunk ->
                                println("[SSE] Sending chunk to client: '$chunk'")
                                fullResponse.append(chunk)
                                // Send text chunk as SSE
                                write("event: message\n")
                                write("data: $chunk\n\n")
                                flush()
                                println("[SSE] Chunk sent and flushed")
                            }

                        // Send done event
                        write("event: done\n")
                        write("data: {\"status\":\"complete\"}\n\n")
                        flush()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Connection closed - this is normal, just log and exit
                        println("Stream writer cancelled (connection closed)")
                    } catch (e: Exception) {
                        println("Exception in streaming writer: ${e.message}")
                        e.printStackTrace()
                        try {
                            write("event: error\n")
                            write("data: ${e.message ?: "Unknown error"}\n\n")
                            flush()
                        } catch (ignored: Exception) {
                            // Connection already closed
                        }
                    }
                }

                // Check if agent goal was achieved
                val finalResponse = fullResponse.toString()
                val goalAchieved = if (agentType != "none") {
                    val preset = AgentPresets.getPreset(agentType)
                    preset?.completionMarker?.let { marker ->
                        finalResponse.contains(marker)
                    } ?: false
                } else false

                // Save AI response to database after streaming is complete
                dbQuery {
                    Messages.insert {
                        it[Messages.chatId] = chatId
                        it[role] = "assistant"
                        it[content] = finalResponse
                    }

                    // Update chat updatedAt and agentGoalAchieved if needed
                    Chats.update({ Chats.id eq chatId }) {
                        it[updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentTimestamp()
                        if (goalAchieved) {
                            it[agentGoalAchieved] = true
                        }
                    }
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to send streaming message: ${e.message}"))
            }
        }

            // Get single reasoning approach
            post("/single-approach") {
                // CSRF Protection
                if (!CsrfProtection.isValidRequest(call)) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("CSRF validation failed"))
                    return@post
                }

                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                    return@post
                }

                try {
                    val request = call.receive<SingleApproachRequest>()

                    // Check provider access
                    val (providerAllowed, providerError) = checkProviderAccess(userId, request.provider)
                    if (!providerAllowed) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse(providerError ?: "Provider access denied"))
                        return@post
                    }

                    // Check request limit
                    val (allowed, error) = checkAndIncrementRequestLimit(userId)
                    if (!allowed) {
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse(error ?: "Request limit exceeded"))
                        return@post
                    }

                    // Get approach metadata
                    val (name, description) = when (request.approach) {
                        "direct" -> "Direct Answer" to "Прямой ответ без дополнительных инструкций"
                        "single" -> "Expert Panel - Single Request" to "Все эксперты + модератор в одном AI запросе (быстро, но менее глубоко)"
                        "two" -> "Expert Panel - Two Requests" to "Эксперты в одном запросе, модератор в отдельном (баланс скорости и качества)"
                        "chain" -> "Expert Panel - Chain" to "Каждый эксперт отдельно + модератор (медленно, но более независимые мнения)"
                        else -> throw IllegalArgumentException("Unknown approach: ${request.approach}")
                    }

                    // Get single approach result
                    val answer = AIService.getSingleApproach(
                        provider = request.provider,
                        task = request.task,
                        approach = request.approach
                    )

                    val response = SingleApproachResponse(
                        task = request.task,
                        approach = request.approach,
                        name = name,
                        description = description,
                        answer = answer
                    )

                    call.respond(response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to get approach result: ${e.message}"))
                }
            }

            // Compare reasoning approaches
            post("/compare-reasoning") {
                // CSRF Protection
                if (!CsrfProtection.isValidRequest(call)) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("CSRF validation failed"))
                    return@post
                }

                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                    return@post
                }

                try {
                    val request = call.receive<CompareReasoningRequest>()

                    // Check provider access
                    val (providerAllowed, providerError) = checkProviderAccess(userId, request.provider)
                    if (!providerAllowed) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse(providerError ?: "Provider access denied"))
                        return@post
                    }

                    // Check request limit
                    val (allowed, error) = checkAndIncrementRequestLimit(userId)
                    if (!allowed) {
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse(error ?: "Request limit exceeded"))
                        return@post
                    }

                    // Compare different reasoning approaches
                    val result = AIService.compareReasoningApproaches(
                        provider = request.provider,
                        task = request.task
                    )

                    // Format response
                    val response = CompareReasoningResponse(
                        task = result.task,
                        approaches = listOf(
                            ReasoningApproachResult(
                                name = "Direct Answer",
                                description = "Прямой ответ без дополнительных инструкций",
                                answer = result.directAnswer
                            ),
                            ReasoningApproachResult(
                                name = "Expert Panel - Single Request",
                                description = "Все эксперты + модератор в одном AI запросе (быстро, но менее глубоко)",
                                answer = result.expertPanelSingleRequest
                            ),
                            ReasoningApproachResult(
                                name = "Expert Panel - Two Requests",
                                description = "Эксперты в одном запросе, модератор в отдельном (баланс скорости и качества)",
                                answer = result.expertPanelTwoRequests
                            ),
                            ReasoningApproachResult(
                                name = "Expert Panel - Chain",
                                description = "Каждый эксперт отдельно + модератор (медленно, но более независимые мнения)",
                                answer = result.expertPanelChain
                            )
                        )
                    )

                    call.respond(response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to compare reasoning approaches: ${e.message}"))
                }
            }
        }
    }
}
