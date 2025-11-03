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
            val newProvider = providerSelect.value
            currentProvider = newProvider

            // Update provider in database if chat is open
            val chatId = currentChatId
            if (chatId != null) {
                scope.launch {
                    val result = apiClient.updateChatProvider(chatId, newProvider)
                    result.fold(
                        onSuccess = {
                            // Update local chat data
                            val chatIndex = chats.indexOfFirst { it.id == chatId }
                            if (chatIndex != -1) {
                                chats[chatIndex] = chats[chatIndex].copy(provider = newProvider)
                                renderChatHistory()
                            }
                            console.log("Provider updated to $newProvider")
                        },
                        onFailure = { error ->
                            console.error("Failed to update provider", error)
                            showError("Failed to update provider: ${error.message}")
                            // Revert the selection
                            providerSelect.value = chats.find { it.id == chatId }?.provider ?: newProvider
                        }
                    )
                }
            }
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

        // Load messages from server
        scope.launch {
            try {
                showLoading("Loading messages...")
                val result = apiClient.getChatWithMessages(chatId)

                result.fold(
                    onSuccess = { messageResponses ->
                        currentMessages.clear()
                        messageResponses.forEach { msgResponse ->
                            val localMsg = LocalMessage(
                                role = msgResponse.role,
                                content = msgResponse.content
                            )
                            currentMessages.add(localMsg)
                        }
                        hideLoading()
                        renderMessages()
                    },
                    onFailure = { error ->
                        console.error("Failed to load messages", error)
                        hideLoading()
                        showError("Failed to load messages: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                console.error("Error loading messages", e)
                hideLoading()
                showError("Error loading messages: ${e.message}")
            }
        }
    }

    private fun renderMessages() {
        messagesContainer.innerHTML = ""
        currentMessages.forEach { displayMessage(it) }
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

            val titleContainer = document.createElement("div") as HTMLDivElement
            titleContainer.className = "chat-history-item-title-container"

            val title = document.createElement("div") as HTMLDivElement
            title.className = "chat-history-item-title"
            title.textContent = chat.title

            val editBtn = document.createElement("button") as HTMLButtonElement
            editBtn.className = "chat-edit-btn"
            editBtn.textContent = "✏️"
            editBtn.onclick = { event ->
                event.stopPropagation()
                startEditingChatTitle(chat.id, title)
                null
            }

            val deleteBtn = document.createElement("button") as HTMLButtonElement
            deleteBtn.className = "chat-delete-btn"
            deleteBtn.textContent = "🗑️"
            deleteBtn.onclick = { event ->
                event.stopPropagation()
                confirmDeleteChat(chat.id, chat.title)
                null
            }

            titleContainer.appendChild(title)
            titleContainer.appendChild(editBtn)
            titleContainer.appendChild(deleteBtn)

            val preview = document.createElement("div") as HTMLDivElement
            preview.className = "chat-history-item-preview"
            preview.textContent = "${chat.provider} • Click to view"

            chatItem.appendChild(titleContainer)
            chatItem.appendChild(preview)

            chatItem.onclick = { switchToChat(chat.id); null }

            chatHistoryList.appendChild(chatItem)
        }
    }

    private fun confirmDeleteChat(chatId: Int, chatTitle: String) {
        val confirmed = kotlinx.browser.window.confirm("Are you sure you want to delete \"$chatTitle\"? This action cannot be undone.")

        if (confirmed) {
            scope.launch {
                try {
                    val result = apiClient.deleteChat(chatId)
                    result.fold(
                        onSuccess = {
                            // Remove chat from local list
                            chats.removeAll { it.id == chatId }

                            // If deleted chat was currently selected, switch to another chat
                            if (currentChatId == chatId) {
                                if (chats.isNotEmpty()) {
                                    switchToChat(chats.first().id)
                                } else {
                                    createNewChat()
                                }
                            } else {
                                renderChatHistory()
                            }

                            console.log("Chat $chatId deleted successfully")
                        },
                        onFailure = { error ->
                            console.error("Failed to delete chat", error)
                            showError("Failed to delete chat: ${error.message}")
                        }
                    )
                } catch (e: Exception) {
                    console.error("Error deleting chat", e)
                    showError("Error deleting chat: ${e.message}")
                }
            }
        }
    }

    private fun startEditingChatTitle(chatId: Int, titleElement: HTMLDivElement) {
        val currentTitle = titleElement.textContent ?: ""

        val input = document.createElement("input") as HTMLInputElement
        input.type = "text"
        input.value = currentTitle
        input.className = "chat-title-edit-input"

        val finishEdit = {
            val newTitle = input.value.trim()
            if (newTitle.isNotEmpty() && newTitle != currentTitle) {
                scope.launch {
                    val result = apiClient.updateChatTitle(chatId, newTitle)
                    result.fold(
                        onSuccess = {
                            val chatIndex = chats.indexOfFirst { it.id == chatId }
                            if (chatIndex != -1) {
                                chats[chatIndex] = chats[chatIndex].copy(title = newTitle)
                            }
                            renderChatHistory()
                        },
                        onFailure = { error ->
                            console.error("Failed to update chat title", error)
                            showError("Failed to update title: ${error.message}")
                            renderChatHistory()
                        }
                    )
                }
            } else {
                renderChatHistory()
            }
        }

        input.onblur = { finishEdit(); null }
        input.onkeydown = { event ->
            if (event is org.w3c.dom.events.KeyboardEvent) {
                when (event.key) {
                    "Enter" -> finishEdit()
                    "Escape" -> renderChatHistory()
                }
            }
            null
        }

        titleElement.parentElement?.replaceChild(input, titleElement)
        input.focus()
        input.select()
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
