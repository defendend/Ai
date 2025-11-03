package app.routes

import app.database.DatabaseFactory.dbQuery
import app.models.*
import app.services.AIService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*

fun Route.chatRoutes() {
    route("/api/chats") {
        // Get all chats for user (temporary without auth)
        get {
            // TODO: Add JWT authentication
            val userId = call.request.queryParameters["userId"]?.toIntOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("userId parameter is required"))
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
            val userId = call.request.queryParameters["userId"]?.toIntOrNull()
            val request = call.receive<CreateChatRequest>()

            if (userId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("userId parameter is required"))
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

        // Update chat provider
        patch("/{chatId}") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
            val request = call.receive<UpdateChatRequest>()

            if (chatId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chat ID"))
                return@patch
            }

            try {
                val updated = dbQuery {
                    val existing = Chats.select { Chats.id eq chatId }.singleOrNull()
                    if (existing == null) return@dbQuery false

                    Chats.update({ Chats.id eq chatId }) {
                        it[provider] = request.provider
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

        // Get chat with messages
        get("/{chatId}") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()

            if (chatId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chat ID"))
                return@get
            }

            try {
                val result = dbQuery {
                    val chatRow = Chats.select { Chats.id eq chatId }.singleOrNull()
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
            val chatId = call.parameters["chatId"]?.toIntOrNull()
            val request = call.receive<SendMessageRequest>()

            if (chatId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chat ID"))
                return@post
            }

            try {
                // Get chat info
                val chat = dbQuery {
                    Chats.select { Chats.id eq chatId }.singleOrNull()
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
