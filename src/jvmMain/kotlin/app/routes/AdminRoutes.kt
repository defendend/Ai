package app.routes

import app.database.DatabaseFactory.dbQuery
import app.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.mindrot.jbcrypt.BCrypt

// Admin-only route protection
fun Route.requireAdmin(build: Route.() -> Unit): Route {
    return authenticate("auth-jwt") {
        intercept(ApplicationCallPipeline.Call) {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asInt()
            val email = principal?.payload?.getClaim("email")?.asString()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                finish()
                return@intercept
            }

            // Check if user is admin
            val isAdmin = dbQuery {
                Users.select { Users.id eq userId }
                    .singleOrNull()
                    ?.get(Users.isAdmin) ?: false
            }

            if (!isAdmin) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin access required"))
                finish()
                return@intercept
            }

            proceed()
        }
        build()
    }
}

fun Route.adminRoutes() {
    route("/api/admin") {
        requireAdmin {
            // Get all users
            get("/users") {
                try {
                    val users = dbQuery {
                        Users.selectAll().map { it.toUserDTO() }
                    }
                    call.respond(users)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to fetch users: ${e.message}"))
                }
            }

            // Get single user
            get("/users/{id}") {
                val userId = call.parameters["id"]?.toIntOrNull()
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid user ID"))
                    return@get
                }

                try {
                    val user = dbQuery {
                        Users.select { Users.id eq userId }
                            .singleOrNull()
                            ?.toUserDTO()
                    }

                    if (user == null) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                        return@get
                    }

                    call.respond(user)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to fetch user: ${e.message}"))
                }
            }

            // Create new user
            post("/users") {
                val request = call.receive<CreateUserRequest>()

                // Validate input
                if (request.email.isBlank() || request.password.length < 6) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Email and password (min 6 chars) are required"))
                    return@post
                }

                try {
                    val userId = dbQuery {
                        // Check if user already exists
                        val existingUser = Users.select { Users.email eq request.email }.singleOrNull()
                        if (existingUser != null) {
                            return@dbQuery null
                        }

                        // Hash password
                        val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt())

                        // Insert new user
                        Users.insert {
                            it[email] = request.email
                            it[Users.passwordHash] = passwordHash
                            it[allowedProviders] = request.allowedProviders
                            it[requestLimit] = request.requestLimit
                        }[Users.id].value
                    }

                    if (userId == null) {
                        call.respond(HttpStatusCode.Conflict, ErrorResponse("User with this email already exists"))
                        return@post
                    }

                    val user = dbQuery {
                        Users.select { Users.id eq userId }
                            .single()
                            .toUserDTO()
                    }

                    call.respond(HttpStatusCode.Created, user)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create user: ${e.message}"))
                }
            }

            // Update user settings
            put("/users/{id}") {
                val userId = call.parameters["id"]?.toIntOrNull()
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid user ID"))
                    return@put
                }

                val request = call.receive<UpdateUserRequest>()

                try {
                    val updated = dbQuery {
                        // Check if user exists
                        val existingUser = Users.select { Users.id eq userId }.singleOrNull()
                        if (existingUser == null) {
                            return@dbQuery false
                        }

                        // Update user
                        Users.update({ Users.id eq userId }) {
                            request.allowedProviders?.let { providers -> it[allowedProviders] = providers }
                            request.requestLimit?.let { limit -> it[requestLimit] = limit }
                            request.requestCount?.let { count -> it[requestCount] = count }
                        }
                        true
                    }

                    if (!updated) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                        return@put
                    }

                    val user = dbQuery {
                        Users.select { Users.id eq userId }
                            .single()
                            .toUserDTO()
                    }

                    call.respond(user)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update user: ${e.message}"))
                }
            }

            // Delete user
            delete("/users/{id}") {
                val userId = call.parameters["id"]?.toIntOrNull()
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid user ID"))
                    return@delete
                }

                // Prevent admin from deleting themselves
                val principal = call.principal<JWTPrincipal>()
                val currentUserId = principal?.payload?.getClaim("userId")?.asInt()

                if (currentUserId == userId) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cannot delete your own account"))
                    return@delete
                }

                try {
                    val deleted = dbQuery {
                        val existingUser = Users.select { Users.id eq userId }.singleOrNull()
                        if (existingUser == null) {
                            return@dbQuery false
                        }

                        // Delete user's chats and messages first (cascade)
                        val userChatIds = Chats.select { Chats.userId eq userId }.map { it[Chats.id].value }
                        userChatIds.forEach { chatId ->
                            Messages.deleteWhere { Messages.chatId eq chatId }
                        }
                        Chats.deleteWhere { Chats.userId eq userId }

                        // Delete user
                        Users.deleteWhere { Users.id eq userId } > 0
                    }

                    if (!deleted) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                        return@delete
                    }

                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete user: ${e.message}"))
                }
            }

            // Reset user request count
            post("/users/{id}/reset-requests") {
                val userId = call.parameters["id"]?.toIntOrNull()
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid user ID"))
                    return@post
                }

                try {
                    val updated = dbQuery {
                        val existingUser = Users.select { Users.id eq userId }.singleOrNull()
                        if (existingUser == null) {
                            return@dbQuery false
                        }

                        Users.update({ Users.id eq userId }) {
                            it[requestCount] = 0
                            it[lastRequestReset] = org.jetbrains.exposed.sql.javatime.CurrentTimestamp()
                        }
                        true
                    }

                    if (!updated) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                        return@post
                    }

                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to reset request count: ${e.message}"))
                }
            }
        }
    }
}
