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

private fun isValidEmail(email: String): Boolean {
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    return email.matches(emailRegex)
}

fun Route.authRoutes() {
    route("/api/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()

            // Validate email format
            if (!isValidEmail(request.email)) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid email format"))
                return@post
            }

            // Validate password strength
            if (request.password.length < 6) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Password must be at least 6 characters"))
                return@post
            }

            try {
                val userId = dbQuery {
                    // Check if user already exists
                    val existingUser = Users.select { Users.email eq request.email }.singleOrNull()
                    if (existingUser != null) {
                        return@dbQuery null
                    }

                    // Hash password with 12 rounds for better security
                    val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt(12))

                    // Insert new user
                    val isAdminUser = (request.email == "alexseera@yandex.ru")
                    val newUserId = Users.insert {
                        it[email] = request.email
                        it[Users.passwordHash] = passwordHash
                        // Make alexseera@yandex.ru an admin
                        it[isAdmin] = isAdminUser
                        // Admins get unlimited requests
                        it[requestLimit] = if (isAdminUser) -1 else 100
                    }[Users.id].value

                    Pair(newUserId, isAdminUser)
                }

                if (userId == null) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("User with this email already exists"))
                    return@post
                }

                // Generate JWT token
                val token = generateToken(userId.first, request.email, userId.second)

                call.respond(
                    HttpStatusCode.Created,
                    LoginResponse(
                        token = token,
                        user = UserDTO(id = userId.first, email = request.email, isAdmin = userId.second)
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
                val userEmail = user[Users.email]
                val isAdmin = user[Users.isAdmin]
                val token = generateToken(userId, userEmail, isAdmin)

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

private fun generateToken(userId: Int, email: String, isAdmin: Boolean): String {
    val secret = System.getenv("JWT_SECRET")
        ?: throw IllegalStateException("JWT_SECRET environment variable must be set!")
    val issuer = "ai-chat"
    val audience = "ai-chat-users"
    val validityInMs = 36_000_00 * 24 * 7 // 7 days

    return JWT.create()
        .withAudience(audience)
        .withIssuer(issuer)
        .withClaim("userId", userId)
        .withClaim("email", email)
        .withClaim("isAdmin", isAdmin)
        .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
        .sign(Algorithm.HMAC256(secret))
}
