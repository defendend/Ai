package app

import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.w3c.dom.*
import org.w3c.dom.events.KeyboardEvent

data class LocalChat(
    val id: Int,
    val title: String,
    val provider: String,
    val messages: List<LocalMessage> = emptyList()
)

data class LocalMessage(
    val role: String,
    val content: String
)

class ChatUI {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val apiClient = BackendApiClient()

    private var chats = mutableListOf<LocalChat>()
    private var currentChatId: Int? = null
    private var currentMessages = mutableListOf<LocalMessage>()
    private var currentProvider: String = "deepseek"

    // DOM elements
    private val messagesContainer: HTMLDivElement
    private val messageInput: HTMLTextAreaElement
    private val sendBtn: HTMLButtonElement
    private val newChatBtn: HTMLButtonElement
    private val chatHistoryList: HTMLDivElement
    private val providerSelect: HTMLSelectElement

    init {
        // Get DOM elements
        messagesContainer = document.getElementById("messagesContainer") as HTMLDivElement
        messageInput = document.getElementById("messageInput") as HTMLTextAreaElement
        sendBtn = document.getElementById("sendBtn") as HTMLButtonElement
        newChatBtn = document.getElementById("newChatBtn") as HTMLButtonElement
        chatHistoryList = document.getElementById("chatHistoryList") as HTMLDivElement
        providerSelect = document.getElementById("providerSelect") as HTMLSelectElement

        setupEventListeners()
        loadChatsFromServer()
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

        providerSelect.onchange = {
            currentProvider = providerSelect.value
            null
        }
    }

    private fun loadChatsFromServer() {
        scope.launch {
            try {
                showLoading("Loading chats...")
                val result = apiClient.getChats()

                result.fold(
                    onSuccess = { chatResponses ->
                        chats = chatResponses.map { response ->
                            LocalChat(
                                id = response.id,
                                title = response.title,
                                provider = response.provider
                            )
                        }.toMutableList()

                        renderChatHistory()

                        if (chats.isNotEmpty()) {
                            switchToChat(chats.first().id)
                        } else {
                            createNewChat()
                        }
                        hideLoading()
                    },
                    onFailure = { error ->
                        console.error("Failed to load chats", error)
                        hideLoading()
                        createNewChat()
                    }
                )
            } catch (e: Exception) {
                console.error("Error loading chats", e)
                hideLoading()
                createNewChat()
            }
        }
    }

    private fun createNewChat() {
        scope.launch {
            try {
                sendBtn.disabled = true
                val result = apiClient.createChat("New Chat", currentProvider)

                result.fold(
                    onSuccess = { chatResponse ->
                        val newChat = LocalChat(
                            id = chatResponse.id,
                            title = chatResponse.title,
                            provider = chatResponse.provider
                        )
                        chats.add(0, newChat)
                        switchToChat(newChat.id)
                        renderChatHistory()
                    },
                    onFailure = { error ->
                        showError("Failed to create chat: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                showError("Error creating chat: ${e.message}")
            } finally {
                sendBtn.disabled = false
            }
        }
    }

    private fun switchToChat(chatId: Int) {
        currentChatId = chatId
        val chat = chats.find { it.id == chatId } ?: return

        currentMessages.clear()
        currentProvider = chat.provider
        providerSelect.value = chat.provider

        messagesContainer.innerHTML = ""
        renderChatHistory()
    }

    private fun handleSendMessage() {
        val content = messageInput.value.trim()
        if (content.isEmpty()) return

        val chatId = currentChatId
        if (chatId == null) {
            showError("Please create a chat first")
            return
        }

        // Display user message immediately
        val userMessage = LocalMessage(role = "user", content = content)
        currentMessages.add(userMessage)
        displayMessage(userMessage)

        messageInput.value = ""
        sendBtn.disabled = true

        scope.launch {
            try {
                val result = apiClient.sendMessage(chatId, content)

                result.fold(
                    onSuccess = { messages ->
                        // Backend returns both user and assistant messages
                        // Find the assistant message (last one)
                        val assistantMsg = messages.lastOrNull { it.role == "assistant" }
                        if (assistantMsg != null) {
                            val localMsg = LocalMessage(
                                role = assistantMsg.role,
                                content = assistantMsg.content
                            )
                            currentMessages.add(localMsg)
                            displayMessage(localMsg)

                            // Update chat title from first message
                            if (currentMessages.size <= 2) {
                                updateChatTitle(chatId, content.take(30))
                            }
                        }
                    },
                    onFailure = { error ->
                        showError("Error: ${error.message}")
                        // Remove the optimistically added user message
                        currentMessages.removeLastOrNull()
                        messagesContainer.removeChild(messagesContainer.lastChild!!)
                    }
                )
            } catch (e: Exception) {
                showError("Error: ${e.message}")
                currentMessages.removeLastOrNull()
                messagesContainer.lastChild?.let { messagesContainer.removeChild(it) }
            } finally {
                sendBtn.disabled = false
            }
        }
    }

    private fun updateChatTitle(chatId: Int, title: String) {
        val chatIndex = chats.indexOfFirst { it.id == chatId }
        if (chatIndex != -1) {
            chats[chatIndex] = chats[chatIndex].copy(title = title)
            renderChatHistory()
        }
    }

    private fun displayMessage(message: LocalMessage) {
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
            preview.textContent = "${chat.provider} • Click to view"

            chatItem.appendChild(title)
            chatItem.appendChild(preview)

            chatItem.onclick = { switchToChat(chat.id); null }

            chatHistoryList.appendChild(chatItem)
        }
    }

    private fun showError(errorMessage: String) {
        val errorDiv = document.createElement("div") as HTMLDivElement
        errorDiv.className = "error-message"
        errorDiv.textContent = errorMessage
        messagesContainer.appendChild(errorDiv)
        messagesContainer.scrollTop = messagesContainer.scrollHeight.toDouble()
    }

    private fun showLoading(message: String) {
        messagesContainer.innerHTML = "<div class='loading-message'>$message</div>"
    }

    private fun hideLoading() {
        val loadingMsg = messagesContainer.querySelector(".loading-message")
        loadingMsg?.let { messagesContainer.removeChild(it) }
    }
}
