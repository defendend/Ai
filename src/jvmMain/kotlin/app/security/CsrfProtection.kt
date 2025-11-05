package app.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

/**
 * CSRF Protection middleware
 * Validates Origin and Referer headers for state-changing requests
 */
object CsrfProtection {

    private val allowedOrigins = setOf(
        "https://defendend.dev",
        "https://www.defendend.dev"
    )

    // For development
    private val developmentOrigins = setOf(
        "http://localhost:8080",
        "http://127.0.0.1:8080"
    )

    /**
     * Check if request passes CSRF validation
     */
    fun isValidRequest(call: ApplicationCall): Boolean {
        val method = call.request.httpMethod

        // Only check state-changing methods
        if (method !in listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)) {
            return true
        }

        // Get Origin header (preferred for CORS requests)
        val origin = call.request.header("Origin")

        // Get Referer header (fallback)
        val referer = call.request.header("Referer")

        // Check if development mode
        val isDevelopment = System.getenv("ENVIRONMENT") != "production"
        val validOrigins = if (isDevelopment) {
            allowedOrigins + developmentOrigins
        } else {
            allowedOrigins
        }

        // Validate Origin header
        if (origin != null) {
            return origin in validOrigins
        }

        // Validate Referer header as fallback
        if (referer != null) {
            val refererHost = try {
                val url = java.net.URL(referer)
                "${url.protocol}://${url.host}${if (url.port != -1 && url.port != url.defaultPort) ":${url.port}" else ""}"
            } catch (e: Exception) {
                null
            }

            if (refererHost != null && refererHost in validOrigins) {
                return true
            }
        }

        // If no Origin or Referer header, reject for safety
        return false
    }

    /**
     * Get client IP address (considering proxies)
     */
    fun getClientIP(call: ApplicationCall): String {
        return call.request.header("X-Forwarded-For")?.split(",")?.first()?.trim()
            ?: call.request.header("X-Real-IP")
            ?: call.request.local.remoteHost
    }
}
