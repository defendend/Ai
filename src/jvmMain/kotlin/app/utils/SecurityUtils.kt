package app.utils

/**
 * Security utility functions for input sanitization and validation
 */
object SecurityUtils {

    /**
     * Sanitize user input to prevent XSS attacks
     * Escapes HTML special characters
     */
    fun sanitizeInput(input: String?): String {
        if (input == null) return ""

        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
    }

    /**
     * Validate and sanitize chat title
     * Max length: 255 characters
     */
    fun sanitizeChatTitle(title: String): String {
        val cleaned = sanitizeInput(title.trim())
        return if (cleaned.length > 255) {
            cleaned.take(255)
        } else {
            cleaned
        }
    }

    /**
     * Validate and sanitize message content
     * Max length: 50000 characters
     */
    fun sanitizeMessageContent(content: String): String {
        val cleaned = sanitizeInput(content.trim())
        return if (cleaned.length > 50000) {
            cleaned.take(50000)
        } else {
            cleaned
        }
    }

    /**
     * Validate and sanitize system prompt
     * Max length: 10000 characters
     */
    fun sanitizeSystemPrompt(prompt: String?): String? {
        if (prompt == null) return null
        val cleaned = sanitizeInput(prompt.trim())
        return if (cleaned.length > 10000) {
            cleaned.take(10000)
        } else {
            cleaned
        }.ifEmpty { null }
    }
}
