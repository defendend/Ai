package app

import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.KeyboardEvent

class ChatUI {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val messages = mutableListOf<ChatMessage>()
    private var client: AnthropicClient

    private val messagesContainer: HTMLDivElement
    private val messageInput: HTMLTextAreaElement
    private val sendBtn: HTMLButtonElement
    private val apiKeyInput: HTMLInputElement
    private val saveKeyBtn: HTMLButtonElement

    init {
        // Load API key from localStorage
        val savedKey = localStorage.getItem("anthropic_api_key") ?: ""
        client = AnthropicClient(savedKey)

        // Get DOM elements
        messagesContainer = document.getElementById("messagesContainer") as HTMLDivElement
        messageInput = document.getElementById("messageInput") as HTMLTextAreaElement
        sendBtn = document.getElementById("sendBtn") as HTMLButtonElement
        apiKeyInput = document.getElementById("apiKeyInput") as HTMLInputElement
        saveKeyBtn = document.getElementById("saveKeyBtn") as HTMLButtonElement

        // Set saved API key to input (masked)
        if (savedKey.isNotEmpty()) {
            apiKeyInput.value = savedKey
        }

        setupEventListeners()
    }

    private fun setupEventListeners() {
        sendBtn.onclick = {
            handleSendMessage()
            null
        }

        messageInput.onkeydown = { event ->
            if (event is KeyboardEvent && event.key == "Enter" && !event.shiftKey) {
                event.preventDefault()
                handleSendMessage()
            }
            null
        }

        saveKeyBtn.onclick = {
            val apiKey = apiKeyInput.value
            localStorage.setItem("anthropic_api_key", apiKey)
            client.updateApiKey(apiKey)
            showNotification("API key saved successfully!")
            null
        }
    }

    private fun handleSendMessage() {
        val content = messageInput.value.trim()
        if (content.isEmpty()) return

        val userMessage = ChatMessage(role = "user", content = content)
        messages.add(userMessage)
        displayMessage(userMessage)

        messageInput.value = ""
        sendBtn.disabled = true

        scope.launch {
            try {
                // Convert ChatMessage to API Message format
                val apiMessages = messages.map { Message(role = it.role, content = it.content) }

                val result = client.sendMessage(apiMessages)

                result.fold(
                    onSuccess = { responseContent ->
                        val assistantMessage = ChatMessage(role = "assistant", content = responseContent)
                        messages.add(assistantMessage)
                        displayMessage(assistantMessage)
                    },
                    onFailure = { error ->
                        showError("Error: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                showError("Error: ${e.message}")
            } finally {
                sendBtn.disabled = false
            }
        }
    }

    private fun displayMessage(message: ChatMessage) {
        val messageDiv = document.createElement("div") as HTMLDivElement
        messageDiv.className = "message ${message.role}"

        val avatar = document.createElement("div") as HTMLDivElement
        avatar.className = "message-avatar"
        avatar.textContent = if (message.role == "user") "U" else "AI"

        val contentDiv = document.createElement("div") as HTMLDivElement
        contentDiv.className = "message-content"
        contentDiv.textContent = message.content

        messageDiv.appendChild(avatar)
        messageDiv.appendChild(contentDiv)

        messagesContainer.appendChild(messageDiv)
        messagesContainer.scrollTop = messagesContainer.scrollHeight.toDouble()
    }

    private fun showError(errorMessage: String) {
        val errorDiv = document.createElement("div") as HTMLDivElement
        errorDiv.className = "error-message"
        errorDiv.textContent = errorMessage
        messagesContainer.appendChild(errorDiv)
        messagesContainer.scrollTop = messagesContainer.scrollHeight.toDouble()
    }

    private fun showNotification(message: String) {
        console.log(message)
        // You can implement a toast notification here if needed
    }
}
