package app

import kotlinx.browser.document
import kotlinx.browser.window

fun main() {
    window.onload = {
        console.log("AI Chat application started")
        ChatUI()
    }
}
