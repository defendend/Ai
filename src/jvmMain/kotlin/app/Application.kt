package app

import app.database.DatabaseFactory
import app.routes.adminRoutes
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
import io.ktor.server.plugins.doublereceive.*
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

    // Enable double receive to read request body multiple times
    install(DoubleReceive)

    // Configure security headers
    intercept(ApplicationCallPipeline.Plugins) {
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("X-Frame-Options", "DENY")
        call.response.headers.append("X-XSS-Protection", "1; mode=block")
        call.response.headers.append("Referrer-Policy", "strict-origin-when-cross-origin")
        call.response.headers.append("Permissions-Policy", "geolocation=(), microphone=(), camera=()")

        // HSTS - only for HTTPS connections
        val scheme = call.request.header("X-Forwarded-Proto") ?: "http"
        if (scheme == "https") {
            call.response.headers.append("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        }

        proceed()
    }

    // Configure request logging
    intercept(ApplicationCallPipeline.Monitoring) {
        val startTime = System.currentTimeMillis()

        // Capture request body for non-streaming requests
        val requestBody = if (call.request.httpMethod in listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch) &&
            !call.request.uri.contains("/stream")) {
            try {
                call.receiveText()
            } catch (e: Exception) {
                null
            }
        } else null

        try {
            proceed()
        } finally {
            val duration = System.currentTimeMillis() - startTime
            val status = call.response.status()?.value ?: 0
            val method = call.request.httpMethod.value
            val uri = call.request.uri
            val queryParams = call.request.queryParameters.entries()
                .joinToString(", ") { "${it.key}=${it.value}" }
                .takeIf { it.isNotEmpty() }
                ?.let { "?$it" } ?: ""
            val userAgent = call.request.headers["User-Agent"]?.take(50) ?: "Unknown"

            val bodyLog = requestBody?.let { body ->
                // Sanitize sensitive fields (passwords, tokens, emails, API keys)
                val sanitized = body
                    .replace(Regex("\"password\"\\s*:\\s*\"[^\"]*\""), "\"password\":\"***\"")
                    .replace(Regex("\"token\"\\s*:\\s*\"[^\"]*\""), "\"token\":\"***\"")
                    .replace(Regex("\"email\"\\s*:\\s*\"[^\"]*\""), "\"email\":\"***@***.***\"")
                    .replace(Regex("\"apiKey\"\\s*:\\s*\"[^\"]*\""), "\"apiKey\":\"***\"")
                    .replace(Regex("sk-[a-zA-Z0-9]{32,}"), "sk-***")
                val truncated = if (sanitized.length > 200) sanitized.take(200) + "..." else sanitized
                " | Body: $truncated"
            } ?: ""

            // Mask IP address for privacy (keep first 2 octets for debugging)
            val clientIP = call.request.header("X-Forwarded-For")?.split(",")?.first()?.trim()
                ?: call.request.local.remoteHost
            val maskedIP = clientIP.split(".").take(2).joinToString(".") + ".***"

            println("[$method] $uri$queryParams -> $status (${duration}ms)$bodyLog | IP: $maskedIP | UA: $userAgent")
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

        // Allow only your frontend domain
        allowHost("defendend.dev", schemes = listOf("https"))
        allowHost("www.defendend.dev", schemes = listOf("https"))

        // For local development
        val isDevelopment = System.getenv("ENVIRONMENT") != "production"
        if (isDevelopment) {
            allowHost("localhost:8080")
            allowHost("127.0.0.1:8080")
        }
    }

    // Configure JWT Authentication
    val jwtSecret = System.getenv("JWT_SECRET")
        ?: throw IllegalStateException("JWT_SECRET environment variable must be set!")
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
        adminRoutes()
    }
}
