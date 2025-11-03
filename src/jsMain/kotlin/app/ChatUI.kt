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
    private val chatHistory = ChatHistory()

    private var chats = mutableListOf<Chat>()
    private var currentChatId: String? = null
    private var currentMessages = mutableListOf<ChatMessage>()

    private var claudeClient: AnthropicClient
    private var deepseekClient: DeepSeekClient
    private var currentProvider: String = "claude"

    // DOM elements
    private val messagesContainer: HTMLDivElement
    private val messageInput: HTMLTextAreaElement
    private val sendBtn: HTMLButtonElement
    private val newChatBtn: HTMLButtonElement
    private val chatHistoryList: HTMLDivElement

    private val claudeApiKeyInput: HTMLInputElement
    private val saveClaudeKeyBtn: HTMLButtonElement
    private val claudeKeyContainer: HTMLElement
    private val claudeProviderRadio: HTMLInputElement

    private val deepseekApiKeyInput: HTMLInputElement
    private val saveDeepseekKeyBtn: HTMLButtonElement
    private val deepseekKeyContainer: HTMLElement
    private val deepseekProviderRadio: HTMLInputElement

    init {
        // Load API keys
        val claudeKey = localStorage.getItem("claude_api_key") ?: ""
        val deepseekKey = localStorage.getItem("deepseek_api_key") ?: ""

        claudeClient = AnthropicClient(claudeKey)
        deepseekClient = DeepSeekClient(deepseekKey)

        // Get DOM elements
        messagesContainer = document.getElementById("messagesContainer") as HTMLDivElement
        messageInput = document.getElementById("messageInput") as HTMLTextAreaElement
        sendBtn = document.getElementById("sendBtn") as HTMLButtonElement
        newChatBtn = document.getElementById("newChatBtn") as HTMLButtonElement
        chatHistoryList = document.getElementById("chatHistoryList") as HTMLDivElement

        claudeApiKeyInput = document.getElementById("claudeApiKeyInput") as HTMLInputElement
        saveClaudeKeyBtn = document.getElementById("saveClaudeKeyBtn") as HTMLButtonElement
        claudeKeyContainer = document.getElementById("claudeKeyContainer") as HTMLElement
        claudeProviderRadio = document.getElementById("claudeProvider") as HTMLInputElement

        deepseekApiKeyInput = document.getElementById("deepseekApiKeyInput") as HTMLInputElement
        saveDeepseekKeyBtn = document.getElementById("saveDeepseekKeyBtn") as HTMLButtonElement
        deepseekKeyContainer = document.getElementById("deepseekKeyContainer") as HTMLElement
        deepseekProviderRadio = document.getElementById("deepseekProvider") as HTMLInputElement

        // Set saved API keys
        if (claudeKey.isNotEmpty()) claudeApiKeyInput.value = claudeKey
        if (deepseekKey.isNotEmpty()) deepseekApiKeyInput.value = deepseekKey

        // Load chat history
        loadChatHistory()

        // Create first chat if none exist
        if (chats.isEmpty()) {
            createNewChat()
        } else {
            switchToChat(chats.first().id)
        }

        setupEventListeners()
    }

    private fun setupEventListeners() {
        sendBtn.onclick = { handleSendMessage(); null }

        messageInput.onkeydown = { event ->
            if (event is KeyboardEvent && event.key == "Enter" && !event.shiftKey) {
                event.preventDefault()
                handleSendMessage()
            }
            null
        }

        newChatBtn.onclick = { createNewChat(); null }

        claudeProviderRadio.onclick = { switchProvider("claude"); null }
        deepseekProviderRadio.onclick = { switchProvider("deepseek"); null }

        saveClaudeKeyBtn.onclick = {
            localStorage.setItem("claude_api_key", claudeApiKeyInput.value)
            claudeClient.updateApiKey(claudeApiKeyInput.value)
            console.log("Claude API key saved!")
            null
        }

        saveDeepseekKeyBtn.onclick = {
            localStorage.setItem("deepseek_api_key", deepseekApiKeyInput.value)
            deepseekClient.updateApiKey(deepseekApiKeyInput.value)
            console.log("DeepSeek API key saved!")
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

    private fun createNewChat() {
        val chatId = chatHistory.generateChatId()
        val newChat = Chat(
            id = chatId,
            title = "New Chat",
            messages = emptyList(),
            provider = currentProvider
        )
        chats.add(0, newChat)

        // Keep only last 10 chats
        if (chats.size > 10) {
            chats = chats.take(10).toMutableList()
        }

        switchToChat(chatId)
        saveChats()
        renderChatHistory()
    }

    private fun switchToChat(chatId: String) {
        currentChatId = chatId
        val chat = chats.find { it.id == chatId } ?: return

        currentMessages = chat.messages.toMutableList()
        currentProvider = chat.provider

        // Update provider radio
        when (chat.provider) {
            "claude" -> claudeProviderRadio.checked = true
            "deepseek" -> deepseekProviderRadio.checked = true
        }
        switchProvider(chat.provider)

        renderMessages()
        renderChatHistory()
    }

    private fun handleSendMessage() {
        val content = messageInput.value.trim()
        if (content.isEmpty()) return

        val userMessage = ChatMessage(role = "user", content = content)
        currentMessages.add(userMessage)
        displayMessage(userMessage)

        messageInput.value = ""
        sendBtn.disabled = true

        // Update chat title from first message
        val currentChat = chats.find { it.id == currentChatId }
        if (currentChat != null && currentMessages.size == 1) {
            val updatedChat = currentChat.copy(
                title = chatHistory.generateChatTitle(content),
                messages = currentMessages
            )
            chats[chats.indexOfFirst { it.id == currentChatId }] = updatedChat
            renderChatHistory()
        }

        scope.launch {
            try {
                val apiMessages = currentMessages.map { Message(role = it.role, content = it.content) }

                val result = when (currentProvider) {
                    "claude" -> claudeClient.sendMessage(apiMessages)
                    "deepseek" -> deepseekClient.sendMessage(apiMessages)
                    else -> Result.failure(Exception("Unknown provider"))
                }

                result.fold(
                    onSuccess = { responseContent ->
                        val assistantMessage = ChatMessage(role = "assistant", content = responseContent)
                        currentMessages.add(assistantMessage)
                        displayMessage(assistantMessage)
                        updateCurrentChat()
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

    private fun updateCurrentChat() {
        val chatIndex = chats.indexOfFirst { it.id == currentChatId }
        if (chatIndex != -1) {
            val currentChat = chats[chatIndex]
            chats[chatIndex] = currentChat.copy(
                messages = currentMessages,
                updatedAt = js("Date.now()").unsafeCast<Long>()
            )
            saveChats()
        }
    }

    private fun renderMessages() {
        messagesContainer.innerHTML = ""
        currentMessages.forEach { displayMessage(it) }
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

    private fun renderChatHistory() {
        chatHistoryList.innerHTML = ""
        chats.forEach { chat ->
            val chatItem = document.createElement("div") as HTMLDivElement
            chatItem.className = "chat-history-item" + if (chat.id == currentChatId) " active" else ""

            val title = document.createElement("div") as HTMLDivElement
            title.className = "chat-history-item-title"
            title.textContent = chat.title

            val preview = document.createElement("div") as HTMLDivElement
            preview.className = "chat-history-item-preview"
            preview.textContent = chat.messages.lastOrNull()?.content?.take(50) ?: "Empty chat"

            chatItem.appendChild(title)
            chatItem.appendChild(preview)

            chatItem.onclick = { switchToChat(chat.id); null }

            chatHistoryList.appendChild(chatItem)
        }
    }

    private fun loadChatHistory() {
        chats = chatHistory.loadChats().toMutableList()
    }

    private fun saveChats() {
        chatHistory.saveChats(chats)
    }

    private fun showError(errorMessage: String) {
        val errorDiv = document.createElement("div") as HTMLDivElement
        errorDiv.className = "error-message"
        errorDiv.textContent = errorMessage
        messagesContainer.appendChild(errorDiv)
        messagesContainer.scrollTop = messagesContainer.scrollHeight.toDouble()
    }
}
