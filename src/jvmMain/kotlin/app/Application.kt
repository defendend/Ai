package app

import app.database.DatabaseFactory
import app.routes.authRoutes
import app.routes.chatRoutes
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Initialize database
    DatabaseFactory.init()

    // Configure request logging
    intercept(ApplicationCallPipeline.Monitoring) {
        val startTime = System.currentTimeMillis()
        try {
            proceed()
        } finally {
            val duration = System.currentTimeMillis() - startTime
            val status = call.response.status()?.value ?: 0
            val method = call.request.httpMethod.value
            val uri = call.request.uri
            val userAgent = call.request.headers["User-Agent"]?.take(50) ?: "Unknown"
            println("[$method] $uri -> $status (${duration}ms) | UA: $userAgent")
        }
    }

    // Configure serialization
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // Configure CORS
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)

        // Allow your frontend domain
        anyHost() // TODO: В продакшене заменить на allowHost("defendend.dev")
    }

    // Configure JWT Authentication
    val jwtSecret = System.getenv("JWT_SECRET") ?: "default-secret-change-in-production"
    val jwtIssuer = "ai-chat"
    val jwtAudience = "ai-chat-users"

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JWT
                .require(Algorithm.HMAC256(jwtSecret))
                .withAudience(jwtAudience)
                .withIssuer(jwtIssuer)
                .build())
            validate { credential ->
                if (credential.payload.getClaim("userId").asInt() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    // Configure routing
    routing {
        get("/") {
            call.respondText("AI Chat Backend API is running!", ContentType.Text.Plain)
        }

        get("/health") {
            call.respond(mapOf("status" to "healthy"))
        }

        authRoutes()
        chatRoutes()
    }
}
