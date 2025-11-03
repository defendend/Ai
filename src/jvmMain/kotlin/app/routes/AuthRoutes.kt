package app.routes

import app.database.DatabaseFactory.dbQuery
import app.models.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.mindrot.jbcrypt.BCrypt
import java.util.*

fun Route.authRoutes() {
    route("/api/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()

            // Validate email and password
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
                    }[Users.id].value
                }

                if (userId == null) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("User with this email already exists"))
                    return@post
                }

                // Generate JWT token
                val token = generateToken(userId, request.email)

                call.respond(
                    HttpStatusCode.Created,
                    LoginResponse(
                        token = token,
                        user = UserDTO(id = userId, email = request.email)
                    )
                )
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create user: ${e.message}"))
            }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()

            try {
                val user = dbQuery {
                    Users.select { Users.email eq request.email }.singleOrNull()
                }

                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid email or password"))
                    return@post
                }

                // Verify password
                if (!BCrypt.checkpw(request.password, user[Users.passwordHash])) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid email or password"))
                    return@post
                }

                // Generate JWT token
                val userId = user[Users.id].value
                val token = generateToken(userId, user[Users.email])

                call.respond(
                    LoginResponse(
                        token = token,
                        user = user.toUserDTO()
                    )
                )
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Login failed: ${e.message}"))
            }
        }
    }
}

private fun generateToken(userId: Int, email: String): String {
    val secret = System.getenv("JWT_SECRET") ?: "default-secret-change-in-production"
    val issuer = "ai-chat"
    val audience = "ai-chat-users"
    val validityInMs = 36_000_00 * 24 * 7 // 7 days

    return JWT.create()
        .withAudience(audience)
        .withIssuer(issuer)
        .withClaim("userId", userId)
        .withClaim("email", email)
        .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
        .sign(Algorithm.HMAC256(secret))
}
