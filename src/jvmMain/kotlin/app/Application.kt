package app

import app.database.DatabaseFactory
import app.routes.authRoutes
import app.routes.chatRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Initialize database
    DatabaseFactory.init()

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
