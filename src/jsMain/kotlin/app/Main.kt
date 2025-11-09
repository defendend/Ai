package app

import kotlinx.browser.document
import kotlinx.browser.window

fun main() {
    window.onload = {
        console.log("AI Chat application started")

        // Determine which page to show based on URL path
        val path = window.location.pathname

        try {
            when {
                path == "/" || path == "/index.html" -> {
                    console.log("Initializing login page")
                    LoginUI()
                }
                path == "/chats" || path == "/chat.html" -> {
                    console.log("Initializing chat page")
                    ChatUI()
                }
                path == "/admin" || path == "/admin.html" -> {
                    console.log("Initializing admin page")
                    AdminUI()
                }
                path == "/reasoning-compare" || path == "/reasoning-compare.html" -> {
                    console.log("Initializing reasoning comparison page")
                    ReasoningCompareUI()
                }
                else -> {
                    console.log("Unknown path: $path, defaulting to login")
                    window.location.href = "/"
                }
            }
        } catch (e: Exception) {
            console.error("Initialization error", e)
        }
    }
}
