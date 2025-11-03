package app

import kotlinx.browser.localStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

class ChatHistory {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val chatSerializer = ListSerializer(Chat.serializer())

    fun saveChats(chats: List<Chat>) {
        try {
            val jsonString = json.encodeToString(chatSerializer, chats)
            localStorage.setItem("chat_history", jsonString)
        } catch (e: Exception) {
            console.error("Failed to save chats:", e)
        }
    }

    fun loadChats(): List<Chat> {
        return try {
            val jsonString = localStorage.getItem("chat_history") ?: return emptyList()
            json.decodeFromString(chatSerializer, jsonString)
        } catch (e: Exception) {
            console.error("Failed to load chats:", e)
            emptyList()
        }
    }

    fun generateChatId(): String {
        return "chat_${js("Date.now()")}_${js("Math.random()").toString().substring(2, 8)}"
    }

    fun generateChatTitle(firstMessage: String): String {
        return if (firstMessage.length > 30) {
            firstMessage.take(30) + "..."
        } else {
            firstMessage.ifEmpty { "New Chat" }
        }
    }
}
