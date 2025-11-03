package app

import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.w3c.dom.*
import org.w3c.dom.events.KeyboardEvent

class ChatUI {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val messages = mutableListOf<ChatMessage>()

    private var claudeClient: AnthropicClient
    private var deepseekClient: DeepSeekClient
    private var currentProvider: String = "claude"

    private val messagesContainer: HTMLDivElement
    private val messageInput: HTMLTextAreaElement
    private val sendBtn: HTMLButtonElement

    // Claude elements
    private val claudeApiKeyInput: HTMLInputElement
    private val saveClaudeKeyBtn: HTMLButtonElement
    private val claudeKeyContainer: HTMLElement
    private val claudeProviderRadio: HTMLInputElement

    // DeepSeek elements
    private val deepseekApiKeyInput: HTMLInputElement
    private val saveDeepseekKeyBtn: HTMLButtonElement
    private val deepseekKeyContainer: HTMLElement
    private val deepseekProviderRadio: HTMLInputElement

    init {
        // Load API keys from localStorage
        val claudeKey = localStorage.getItem("claude_api_key") ?: ""
        val deepseekKey = localStorage.getItem("deepseek_api_key") ?: ""

        claudeClient = AnthropicClient(claudeKey)
        deepseekClient = DeepSeekClient(deepseekKey)

        // Get DOM elements
        messagesContainer = document.getElementById("messagesContainer") as HTMLDivElement
        messageInput = document.getElementById("messageInput") as HTMLTextAreaElement
        sendBtn = document.getElementById("sendBtn") as HTMLButtonElement

        // Claude elements
        claudeApiKeyInput = document.getElementById("claudeApiKeyInput") as HTMLInputElement
        saveClaudeKeyBtn = document.getElementById("saveClaudeKeyBtn") as HTMLButtonElement
        claudeKeyContainer = document.getElementById("claudeKeyContainer") as HTMLElement
        claudeProviderRadio = document.getElementById("claudeProvider") as HTMLInputElement

        // DeepSeek elements
        deepseekApiKeyInput = document.getElementById("deepseekApiKeyInput") as HTMLInputElement
        saveDeepseekKeyBtn = document.getElementById("saveDeepseekKeyBtn") as HTMLButtonElement
        deepseekKeyContainer = document.getElementById("deepseekKeyContainer") as HTMLElement
        deepseekProviderRadio = document.getElementById("deepseekProvider") as HTMLInputElement

        // Set saved API keys to inputs
        if (claudeKey.isNotEmpty()) {
            claudeApiKeyInput.value = claudeKey
        }
        if (deepseekKey.isNotEmpty()) {
            deepseekApiKeyInput.value = deepseekKey
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

        // Claude provider
        claudeProviderRadio.onclick = {
            switchProvider("claude")
            null
        }

        saveClaudeKeyBtn.onclick = {
            val apiKey = claudeApiKeyInput.value
            localStorage.setItem("claude_api_key", apiKey)
            claudeClient.updateApiKey(apiKey)
            showNotification("Claude API key saved!")
            null
        }

        // DeepSeek provider
        deepseekProviderRadio.onclick = {
            switchProvider("deepseek")
            null
        }

        saveDeepseekKeyBtn.onclick = {
            val apiKey = deepseekApiKeyInput.value
            localStorage.setItem("deepseek_api_key", apiKey)
            deepseekClient.updateApiKey(apiKey)
            showNotification("DeepSeek API key saved!")
            null
        }
    }

    private fun switchProvider(provider: String) {
        currentProvider = provider
        when (provider) {
            "claude" -> {
                claudeKeyContainer.style.display = "flex"
                deepseekKeyContainer.style.display = "none"
            }
            "deepseek" -> {
                claudeKeyContainer.style.display = "none"
                deepseekKeyContainer.style.display = "flex"
            }
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

                val result = when (currentProvider) {
                    "claude" -> claudeClient.sendMessage(apiMessages)
                    "deepseek" -> deepseekClient.sendMessage(apiMessages)
                    else -> Result.failure(Exception("Unknown provider"))
                }

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
        // Simple notification - можно улучшить позже
    }
}
