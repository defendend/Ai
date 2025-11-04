package app.routes

import app.database.DatabaseFactory.dbQuery
import app.models.*
import app.services.AIService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.DeleteStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager

private fun ApplicationCall.getUserId(): Int? {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.getClaim("userId")?.asInt()
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
                val userId = call.getUserId()
                val request = call.receive<CreateChatRequest>()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                    return@post
                }

            try {
                val chatId = dbQuery {
                    Chats.insert {
                        it[Chats.userId] = userId
                        it[title] = request.title
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

            if (request.provider == null && request.title == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("At least one field (provider or title) must be provided"))
                return@patch
            }

            try {
                val updated = dbQuery {
                    val existing = Chats.select { (Chats.id eq chatId) and (Chats.userId eq userId) }.singleOrNull()
                    if (existing == null) return@dbQuery false

                    Chats.update({ (Chats.id eq chatId) and (Chats.userId eq userId) }) {
                        request.provider?.let { newProvider -> it[provider] = newProvider }
                        request.title?.let { newTitle -> it[title] = newTitle }
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
                dbQuery {
                    // Check if chat exists and belongs to user
                    val chatExists = Chats.select { (Chats.id eq chatIdParam) and (Chats.userId eq userId) }.count() > 0

                    if (chatExists) {
                        // Delete all messages in the chat
                        TransactionManager.current().exec("DELETE FROM messages WHERE chat_id = $chatIdParam")

                        // Then delete the chat itself
                        TransactionManager.current().exec("DELETE FROM chats WHERE id = $chatIdParam AND user_id = $userId")

                        call.respond(HttpStatusCode.OK, mapOf("success" to true))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Chat not found"))
                    }
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
                // Get chat info and verify ownership
                val chat = dbQuery {
                    Chats.select { (Chats.id eq chatId) and (Chats.userId eq userId) }.singleOrNull()
                }

                if (chat == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Chat not found"))
                    return@post
                }

                // Save user message
                val userMessageId = dbQuery {
                    Messages.insert {
                        it[Messages.chatId] = chatId
                        it[role] = "user"
                        it[content] = request.content
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
                val aiResponse = AIService.sendMessage(provider, allMessages)

                // Save AI response
                val assistantMessageId = dbQuery {
                    val msgId = Messages.insert {
                        it[Messages.chatId] = chatId
                        it[role] = "assistant"
                        it[content] = aiResponse
                    }[Messages.id].value

                    // Update chat updatedAt
                    Chats.update({ Chats.id eq chatId }) {
                        it[updatedAt] = org.jetbrains.exposed.sql.javatime.CurrentTimestamp()
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
        }
    }
}
