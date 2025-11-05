package app

import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.w3c.dom.*
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.get

data class LocalChat(
    val id: Int,
    val title: String,
    val provider: String,
    val messages: List<LocalMessage> = emptyList(),
    val streaming: Boolean = true
)

data class LocalMessage(
    val role: String,
    var content: String
)

class ChatUI {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val apiClient = BackendApiClient()

    private var chats = mutableListOf<LocalChat>()
    private var currentChatId: Int? = null
    private var currentMessages = mutableListOf<LocalMessage>()
    private var currentProvider: String = "deepseek"
    private var isSending: Boolean = false

    // DOM elements
    private val messagesContainer: HTMLDivElement
    private val messageInput: HTMLTextAreaElement
    private val sendBtn: HTMLButtonElement
    private val newChatBtn: HTMLButtonElement
    private val chatHistoryList: HTMLDivElement
    private val providerSelect: HTMLSelectElement
    private val deleteModal: HTMLDivElement
    private val deleteChatTitle: HTMLSpanElement
    private val confirmDeleteBtn: HTMLButtonElement
    private val cancelDeleteBtn: HTMLButtonElement
    private val renameModal: HTMLDivElement
    private val renameChatInput: HTMLInputElement
    private val confirmRenameBtn: HTMLButtonElement
    private val cancelRenameBtn: HTMLButtonElement
    private val chatHeaderTitle: HTMLHeadingElement
    private val logoutBtn: HTMLButtonElement
    private val settingsBtn: HTMLButtonElement
    private val settingsModal: HTMLDivElement
    private val temperatureInput: HTMLInputElement
    private val maxTokensInput: HTMLInputElement
    private val topPInput: HTMLInputElement
    private val systemPromptInput: HTMLTextAreaElement
    private val streamingCheckbox: HTMLInputElement
    private val responseFormatSelect: HTMLSelectElement
    private val responseSchemaInput: HTMLTextAreaElement
    private val schemaContainer: HTMLDivElement
    private val confirmSettingsBtn: HTMLButtonElement
    private val cancelSettingsBtn: HTMLButtonElement

    private var chatToDelete: Int? = null
    private var chatToRename: Int? = null

    init {
        // Check authentication
        val token = localStorage["jwt_token"]
        if (token == null) {
            window.location.href = "/"
            throw RuntimeException("Not authenticated, redirecting...")
        }

        // Get DOM elements
        messagesContainer = document.getElementById("messagesContainer") as HTMLDivElement
        messageInput = document.getElementById("messageInput") as HTMLTextAreaElement
        sendBtn = document.getElementById("sendBtn") as HTMLButtonElement
        newChatBtn = document.getElementById("newChatBtn") as HTMLButtonElement
        chatHistoryList = document.getElementById("chatHistoryList") as HTMLDivElement
        providerSelect = document.getElementById("providerSelect") as HTMLSelectElement
        deleteModal = document.getElementById("deleteModal") as HTMLDivElement
        deleteChatTitle = document.getElementById("deleteChatTitle") as HTMLSpanElement
        confirmDeleteBtn = document.getElementById("confirmDeleteBtn") as HTMLButtonElement
        cancelDeleteBtn = document.getElementById("cancelDeleteBtn") as HTMLButtonElement
        renameModal = document.getElementById("renameModal") as HTMLDivElement
        renameChatInput = document.getElementById("renameChatInput") as HTMLInputElement
        confirmRenameBtn = document.getElementById("confirmRenameBtn") as HTMLButtonElement
        cancelRenameBtn = document.getElementById("cancelRenameBtn") as HTMLButtonElement
        chatHeaderTitle = document.querySelector(".chat-header h1") as HTMLHeadingElement
        logoutBtn = document.getElementById("logoutBtn") as HTMLButtonElement
        settingsBtn = document.getElementById("settingsBtn") as HTMLButtonElement
        settingsModal = document.getElementById("settingsModal") as HTMLDivElement
        temperatureInput = document.getElementById("temperatureInput") as HTMLInputElement
        maxTokensInput = document.getElementById("maxTokensInput") as HTMLInputElement
        topPInput = document.getElementById("topPInput") as HTMLInputElement
        systemPromptInput = document.getElementById("systemPromptInput") as HTMLTextAreaElement
        streamingCheckbox = document.getElementById("streamingCheckbox") as HTMLInputElement
        responseFormatSelect = document.getElementById("responseFormatSelect") as HTMLSelectElement
        responseSchemaInput = document.getElementById("responseSchemaInput") as HTMLTextAreaElement
        schemaContainer = document.getElementById("schemaContainer") as HTMLDivElement
        confirmSettingsBtn = document.getElementById("confirmSettingsBtn") as HTMLButtonElement
        cancelSettingsBtn = document.getElementById("cancelSettingsBtn") as HTMLButtonElement

        setupEventListeners()
        updateUITexts()
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

        logoutBtn.onclick = { handleLogout(); null }

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
                            showError("${Localization.t("error.failedToUpdateProvider")}: ${error.message}")
                            // Revert the selection
                            providerSelect.value = chats.find { it.id == chatId }?.provider ?: newProvider
                        }
                    )
                }
            }
            null
        }

        // Modal event listeners
        confirmDeleteBtn.onclick = {
            handleConfirmDelete()
            null
        }

        cancelDeleteBtn.onclick = {
            hideDeleteModal()
            null
        }

        deleteModal.onclick = { event ->
            // Close modal if clicked outside the modal content
            if (event.target == deleteModal) {
                hideDeleteModal()
            }
            null
        }

        // Rename modal event listeners
        confirmRenameBtn.onclick = {
            handleConfirmRename()
            null
        }

        cancelRenameBtn.onclick = {
            hideRenameModal()
            null
        }

        renameModal.onclick = { event ->
            // Close modal if clicked outside the modal content
            if (event.target == renameModal) {
                hideRenameModal()
            }
            null
        }

        // Handle Enter key in rename input
        renameChatInput.onkeydown = { event ->
            if (event is org.w3c.dom.events.KeyboardEvent) {
                when (event.key) {
                    "Enter" -> {
                        event.preventDefault()
                        handleConfirmRename()
                    }
                    "Escape" -> {
                        event.preventDefault()
                        hideRenameModal()
                    }
                }
            }
            null
        }

        // Settings modal event listeners
        settingsBtn.onclick = {
            showSettingsModal()
            null
        }

        confirmSettingsBtn.onclick = {
            handleSaveSettings()
            null
        }

        cancelSettingsBtn.onclick = {
            hideSettingsModal()
            null
        }

        settingsModal.onclick = { event ->
            // Close modal if clicked outside the modal content
            if (event.target == settingsModal) {
                hideSettingsModal()
            }
            null
        }

        // Response format select event listener
        responseFormatSelect.onchange = {
            val format = responseFormatSelect.value
            schemaContainer.style.display = if (format == "none") "none" else "block"
            null
        }
    }

    private fun updateUITexts() {
        // Update sidebar
        newChatBtn.textContent = Localization.t("sidebar.newChat")
        (document.getElementById("recentChatsTitle") as? HTMLElement)?.textContent = Localization.t("sidebar.recentChats")

        // Update input placeholder and button
        messageInput.placeholder = Localization.t("chat.inputPlaceholder")
        sendBtn.textContent = Localization.t("chat.sendButton")

        // Update provider select options
        val deepseekOption = providerSelect.querySelector("option[value='deepseek']") as? HTMLOptionElement
        val claudeOption = providerSelect.querySelector("option[value='claude']") as? HTMLOptionElement
        deepseekOption?.textContent = Localization.t("provider.deepseek")
        claudeOption?.textContent = Localization.t("provider.claude")

        // Update delete modal
        (deleteModal.querySelector(".modal-header h2") as? HTMLElement)?.textContent = Localization.t("modal.deleteTitle")
        (deleteModal.querySelector(".modal-warning") as? HTMLElement)?.textContent = Localization.t("modal.deleteWarning")
        cancelDeleteBtn.textContent = Localization.t("modal.cancelButton")
        confirmDeleteBtn.textContent = Localization.t("modal.deleteButton")

        // Update rename modal
        (renameModal.querySelector(".modal-header h2") as? HTMLElement)?.textContent = Localization.t("modal.renameTitle")
        (renameModal.querySelector(".modal-label") as? HTMLElement)?.textContent = Localization.t("modal.renameLabel")
        renameChatInput.placeholder = Localization.t("modal.renamePlaceholder")
        cancelRenameBtn.textContent = Localization.t("modal.cancelButton")
        confirmRenameBtn.textContent = Localization.t("modal.renameButton")

        // Update settings modal
        (settingsModal.querySelector(".modal-header h2") as? HTMLElement)?.textContent = Localization.t("modal.settingsTitle")
        val settingsLabels = settingsModal.querySelectorAll(".modal-label")
        (settingsLabels[0] as? HTMLElement)?.textContent = Localization.t("modal.temperatureLabel")
        (settingsLabels[1] as? HTMLElement)?.textContent = Localization.t("modal.maxTokensLabel")
        (settingsLabels[2] as? HTMLElement)?.textContent = Localization.t("modal.topPLabel")
        (settingsLabels[3] as? HTMLElement)?.textContent = Localization.t("modal.systemPromptLabel")
        temperatureInput.placeholder = Localization.t("modal.temperaturePlaceholder")
        maxTokensInput.placeholder = Localization.t("modal.maxTokensPlaceholder")
        topPInput.placeholder = Localization.t("modal.topPPlaceholder")
        systemPromptInput.placeholder = Localization.t("modal.systemPromptPlaceholder")
        (settingsModal.querySelector(".settings-hint") as? HTMLElement)?.textContent = Localization.t("modal.settingsHint")
        confirmSettingsBtn.textContent = Localization.t("modal.saveButton")
        cancelSettingsBtn.textContent = Localization.t("modal.cancelButton")

        // Update tooltips
        val tooltipIcons = settingsModal.querySelectorAll(".tooltip-icon")
        tooltipIcons.asList().forEach { icon ->
            val key = (icon as HTMLElement).getAttribute("data-tooltip-key") ?: return@forEach
            icon.setAttribute("data-tooltip", Localization.t("tooltip.$key"))
        }

        // Update settings button
        settingsBtn.textContent = "⚙️ ${Localization.t("settings.button")}"

        // Update header title
        updateChatHeaderTitle()
    }

    private fun updateChatHeaderTitle() {
        val currentChat = currentChatId?.let { id -> chats.find { it.id == id } }
        chatHeaderTitle.textContent = currentChat?.title ?: Localization.t("app.title")
    }

    private fun loadChatsFromServer() {
        scope.launch {
            try {
                showLoading(Localization.t("message.loadingChats"))
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
                        showError("${Localization.t("error.failedToCreateChat")}: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                showError("${Localization.t("error.failedToCreateChat")}: ${e.message}")
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
        updateChatHeaderTitle()

        // Load messages from server
        scope.launch {
            try {
                showLoading(Localization.t("message.loadingMessages"))
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
                        showError("${Localization.t("error.failedToLoadMessages")}: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                console.error("Error loading messages", e)
                hideLoading()
                showError("${Localization.t("error.failedToLoadMessages")}: ${e.message}")
            }
        }
    }

    private fun renderMessages() {
        messagesContainer.innerHTML = ""
        currentMessages.forEach { displayMessage(it) }
    }

    private fun handleSendMessage() {
        // Prevent double-sending
        if (isSending) {
            console.log("Already sending a message, ignoring duplicate request")
            return
        }

        val content = messageInput.value.trim()
        if (content.isEmpty()) return

        val chatId = currentChatId
        if (chatId == null) {
            showError(Localization.t("error.createChat"))
            return
        }

        // Get current chat to check streaming setting
        val currentChat = chats.find { it.id == chatId }
        val useStreaming = currentChat?.streaming ?: true

        // Display user message immediately
        val userMessage = LocalMessage(role = "user", content = content)
        currentMessages.add(userMessage)
        displayMessage(userMessage)

        // Show typing indicator
        showTypingIndicator()

        messageInput.value = ""
        sendBtn.disabled = true
        isSending = true

        scope.launch {
            try {
                if (useStreaming) {
                    // Streaming mode
                    val assistantMessage = LocalMessage(role = "assistant", content = "")
                    currentMessages.add(assistantMessage)

                    val messageDiv = displayMessageForStreaming(assistantMessage)
                    val contentDiv = messageDiv.querySelector(".message-content") as? HTMLDivElement

                    apiClient.sendMessageStreaming(
                        chatId = chatId,
                        content = content,
                        onChunk = { chunk ->
                            console.log("Received chunk:", chunk)
                            assistantMessage.content += chunk
                            // Update DOM directly on each chunk
                            contentDiv?.textContent = assistantMessage.content
                            scrollToBottom()
                        },
                        onComplete = {
                            hideTypingIndicator()
                            sendBtn.disabled = false
                            isSending = false

                            // Update chat title from first message
                            if (currentMessages.size <= 2) {
                                updateChatTitle(chatId, content.take(30))
                            }
                        },
                        onError = { error ->
                            hideTypingIndicator()
                            sendBtn.disabled = false
                            isSending = false

                            contentDiv?.textContent = Localization.t("error.tryAgain")
                            console.error("Streaming error:", error)
                        }
                    )
                } else {
                    // Non-streaming mode (original behavior)
                    val result = apiClient.sendMessage(chatId, content)

                    result.fold(
                        onSuccess = { messages ->
                            console.log("Received messages:", messages)

                            // Remove typing indicator
                            hideTypingIndicator()

                            // Backend returns both user and assistant messages
                            // Find the assistant message (last one)
                            val assistantMsg = messages.lastOrNull { it.role == "assistant" }
                            console.log("Assistant message:", assistantMsg)

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
                            } else {
                                console.warn("No assistant message found in response")
                            }
                        },
                        onFailure = { error ->
                            // Remove typing indicator
                            hideTypingIndicator()

                            // Show error as AI message
                            val errorMsg = LocalMessage(
                                role = "assistant",
                                content = Localization.t("error.tryAgain")
                            )
                            currentMessages.add(errorMsg)
                            displayMessage(errorMsg)

                            console.error("Failed to send message", error)
                        }
                    )

                    sendBtn.disabled = false
                    isSending = false
                }
            } catch (e: Exception) {
                // Remove typing indicator
                hideTypingIndicator()

                // Show error as AI message
                val errorMsg = LocalMessage(
                    role = "assistant",
                    content = Localization.t("error.tryAgain")
                )
                currentMessages.add(errorMsg)
                displayMessage(errorMsg)

                console.error("Error sending message", e)
                sendBtn.disabled = false
                isSending = false
            }
        }
    }

    private fun updateChatTitle(chatId: Int, title: String) {
        val chatIndex = chats.indexOfFirst { it.id == chatId }
        if (chatIndex != -1) {
            chats[chatIndex] = chats[chatIndex].copy(title = title)
            renderChatHistory()
            updateChatHeaderTitle()
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

    private fun scrollToBottom() {
        messagesContainer.scrollTop = messagesContainer.scrollHeight.toDouble()
    }

    private fun displayMessageForStreaming(message: LocalMessage): HTMLDivElement {
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
        scrollToBottom()

        return messageDiv
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
                showRenameChatModal(chat.id, chat.title)
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
            preview.textContent = "${chat.provider} • ${Localization.t("chat.clickToView")}"

            chatItem.appendChild(titleContainer)
            chatItem.appendChild(preview)

            chatItem.onclick = { switchToChat(chat.id); null }

            chatHistoryList.appendChild(chatItem)
        }
    }

    private fun confirmDeleteChat(chatId: Int, chatTitle: String) {
        chatToDelete = chatId

        // Update modal question text with localized string
        val questionText = deleteModal.querySelector(".modal-body p:first-child") as? HTMLParagraphElement
        questionText?.innerHTML = "${Localization.t("modal.deleteQuestion")} \"<span id='deleteChatTitle'>$chatTitle</span>\"?"

        showDeleteModal()
    }

    private fun showDeleteModal() {
        deleteModal.classList.add("show")
    }

    private fun hideDeleteModal() {
        deleteModal.classList.remove("show")
        chatToDelete = null
    }

    private fun handleConfirmDelete() {
        val chatId = chatToDelete ?: return
        hideDeleteModal()

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
                        showError("${Localization.t("error.failedToDeleteChat")}: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                console.error("Error deleting chat", e)
                showError("${Localization.t("error.failedToDeleteChat")}: ${e.message}")
            }
        }
    }

    private fun showRenameChatModal(chatId: Int, currentTitle: String) {
        chatToRename = chatId
        renameChatInput.value = currentTitle
        showRenameModal()

        // Focus and select input after modal is shown
        kotlinx.browser.window.setTimeout({
            renameChatInput.focus()
            renameChatInput.select()
        }, 100)
    }

    private fun showRenameModal() {
        renameModal.classList.add("show")
    }

    private fun hideRenameModal() {
        renameModal.classList.remove("show")
        chatToRename = null
        renameChatInput.value = ""
    }

    private fun handleConfirmRename() {
        val chatId = chatToRename ?: return
        val newTitle = renameChatInput.value.trim()

        if (newTitle.isEmpty()) {
            hideRenameModal()
            return
        }

        hideRenameModal()

        scope.launch {
            try {
                val result = apiClient.updateChatTitle(chatId, newTitle)
                result.fold(
                    onSuccess = {
                        val chatIndex = chats.indexOfFirst { it.id == chatId }
                        if (chatIndex != -1) {
                            chats[chatIndex] = chats[chatIndex].copy(title = newTitle)
                        }
                        renderChatHistory()
                        updateChatHeaderTitle()
                        console.log("Chat $chatId renamed successfully")
                    },
                    onFailure = { error ->
                        console.error("Failed to update chat title", error)
                        showError("${Localization.t("error.failedToUpdateTitle")}: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                console.error("Error updating chat title", e)
                showError("${Localization.t("error.failedToUpdateTitle")}: ${e.message}")
            }
        }
    }

    private fun showTypingIndicator() {
        val messageDiv = document.createElement("div") as HTMLDivElement
        messageDiv.className = "message assistant typing-message"
        messageDiv.setAttribute("data-typing-indicator", "true")

        val avatar = document.createElement("div") as HTMLDivElement
        avatar.className = "message-avatar"
        avatar.textContent = "AI"

        val contentDiv = document.createElement("div") as HTMLDivElement
        contentDiv.className = "message-content"

        val typingIndicator = document.createElement("div") as HTMLDivElement
        typingIndicator.className = "typing-indicator"

        // Create three animated dots
        for (i in 1..3) {
            val dot = document.createElement("span") as HTMLSpanElement
            dot.className = "typing-dot"
            typingIndicator.appendChild(dot)
        }

        contentDiv.appendChild(typingIndicator)
        messageDiv.appendChild(avatar)
        messageDiv.appendChild(contentDiv)

        messagesContainer.appendChild(messageDiv)
        messagesContainer.scrollTop = messagesContainer.scrollHeight.toDouble()
    }

    private fun hideTypingIndicator() {
        val typingMessage = messagesContainer.querySelector("[data-typing-indicator='true']")
        typingMessage?.let { messagesContainer.removeChild(it) }
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

    private fun showSettingsModal() {
        val chatId = currentChatId
        if (chatId == null) {
            showError(Localization.t("error.pleaseSelectChat"))
            return
        }

        // Load current chat to get settings
        scope.launch {
            try {
                val result = apiClient.getChat(chatId)
                result.fold(
                    onSuccess = { chat ->
                        // Populate inputs with current settings
                        temperatureInput.value = chat.temperature?.toString() ?: ""
                        maxTokensInput.value = chat.maxTokens?.toString() ?: ""
                        topPInput.value = chat.topP?.toString() ?: ""
                        systemPromptInput.value = chat.systemPrompt ?: ""
                        streamingCheckbox.checked = chat.streaming
                        responseFormatSelect.value = chat.responseFormat
                        responseSchemaInput.value = chat.responseSchema ?: ""

                        // Show/hide schema container based on format
                        schemaContainer.style.display = if (chat.responseFormat == "none") "none" else "block"

                        // Update default value displays based on provider
                        val provider = chat.provider
                        (document.getElementById("temperatureDefault") as? HTMLElement)?.textContent =
                            Localization.t("default.temperature.$provider")
                        (document.getElementById("maxTokensDefault") as? HTMLElement)?.textContent =
                            Localization.t("default.maxTokens.$provider")
                        (document.getElementById("topPDefault") as? HTMLElement)?.textContent =
                            Localization.t("default.topP.$provider")
                        (document.getElementById("systemPromptDefault") as? HTMLElement)?.textContent =
                            Localization.t("default.systemPrompt")

                        // Show modal
                        settingsModal.classList.add("show")

                        // Focus first input after modal is shown
                        kotlinx.browser.window.setTimeout({
                            temperatureInput.focus()
                        }, 100)
                    },
                    onFailure = { error ->
                        console.error("Failed to load chat settings", error)
                        showError("${Localization.t("error.failedToLoadSettings")}: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                console.error("Error loading chat settings", e)
                showError("${Localization.t("error.failedToLoadSettings")}: ${e.message}")
            }
        }
    }

    private fun hideSettingsModal() {
        settingsModal.classList.remove("show")
    }

    private fun handleSaveSettings() {
        val chatId = currentChatId
        if (chatId == null) {
            hideSettingsModal()
            return
        }

        // Parse input values
        val temperature = temperatureInput.value.trim().toDoubleOrNull()
        val maxTokens = maxTokensInput.value.trim().toIntOrNull()
        val topP = topPInput.value.trim().toDoubleOrNull()
        val systemPrompt = systemPromptInput.value.trim().ifEmpty { null }
        val streaming = streamingCheckbox.checked
        val responseFormat = responseFormatSelect.value
        val responseSchema = responseSchemaInput.value.trim().ifEmpty { null }

        hideSettingsModal()

        scope.launch {
            try {
                val result = apiClient.updateChatSettings(
                    chatId = chatId,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    topP = topP,
                    systemPrompt = systemPrompt,
                    streaming = streaming,
                    responseFormat = responseFormat,
                    responseSchema = responseSchema
                )

                result.fold(
                    onSuccess = {
                        console.log("Chat settings updated successfully")
                    },
                    onFailure = { error ->
                        console.error("Failed to update chat settings", error)
                        showError("${Localization.t("error.failedToSaveSettings")}: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                console.error("Error updating chat settings", e)
                showError("${Localization.t("error.failedToSaveSettings")}: ${e.message}")
            }
        }
    }

    private fun handleLogout() {
        // Clear JWT token and user data from localStorage
        localStorage.removeItem("jwt_token")
        localStorage.removeItem("user_id")
        localStorage.removeItem("user_email")

        // Redirect to login page
        window.location.href = "/"
    }
}
