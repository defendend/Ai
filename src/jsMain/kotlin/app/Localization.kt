package app

import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.get
import org.w3c.dom.set

object Localization {
    private var currentLocale: String = initLocale()

    enum class Locale(val code: String) {
        EN("en"),
        RU("ru")
    }

    private fun initLocale(): String {
        // First check if user has saved preference
        val savedLocale = localStorage["locale"]
        if (savedLocale != null) {
            return savedLocale
        }

        // Otherwise, detect from browser language
        val browserLang = window.navigator.language.lowercase()
        return when {
            browserLang.startsWith("en") -> "en"
            else -> "ru"  // Russian by default
        }
    }

    fun setLocale(locale: String) {
        currentLocale = locale
        localStorage["locale"] = locale
    }

    fun getCurrentLocale(): String = currentLocale

    fun t(key: String): String {
        return translations[currentLocale]?.get(key) ?: translations["ru"]?.get(key) ?: key
    }

    private val translations = mapOf(
        "en" to mapOf(
            // Header
            "app.title" to "AI Chat",

            // Sidebar
            "sidebar.newChat" to "+ New Chat",
            "sidebar.recentChats" to "Recent Chats",

            // Chat
            "chat.inputPlaceholder" to "Type your message here...",
            "chat.sendButton" to "Send",
            "chat.clickToView" to "Click to view",

            // Providers
            "provider.deepseek" to "DeepSeek",
            "provider.claude" to "Claude AI",

            // Modal
            "modal.deleteTitle" to "Delete Chat",
            "modal.deleteQuestion" to "Are you sure you want to delete",
            "modal.deleteWarning" to "This action cannot be undone.",
            "modal.cancelButton" to "Cancel",
            "modal.deleteButton" to "Delete",

            // Messages
            "message.loading" to "Loading...",
            "message.loadingChats" to "Loading chats...",
            "message.loadingMessages" to "Loading messages...",

            // Errors
            "error.createChat" to "Please create a chat first",
            "error.failedToCreateChat" to "Failed to create chat",
            "error.failedToLoadMessages" to "Failed to load messages",
            "error.failedToUpdateProvider" to "Failed to update provider",
            "error.failedToUpdateTitle" to "Failed to update title",
            "error.failedToDeleteChat" to "Failed to delete chat",
            "error.failedToSendMessage" to "Error",

            // Settings
            "settings.language" to "Language"
        ),
        "ru" to mapOf(
            // Header
            "app.title" to "AI Чат",

            // Sidebar
            "sidebar.newChat" to "+ Новый чат",
            "sidebar.recentChats" to "Последние чаты",

            // Chat
            "chat.inputPlaceholder" to "Введите ваше сообщение...",
            "chat.sendButton" to "Отправить",
            "chat.clickToView" to "Нажмите для просмотра",

            // Providers
            "provider.deepseek" to "DeepSeek",
            "provider.claude" to "Claude AI",

            // Modal
            "modal.deleteTitle" to "Удалить чат",
            "modal.deleteQuestion" to "Вы уверены, что хотите удалить",
            "modal.deleteWarning" to "Это действие нельзя отменить.",
            "modal.cancelButton" to "Отмена",
            "modal.deleteButton" to "Удалить",

            // Messages
            "message.loading" to "Загрузка...",
            "message.loadingChats" to "Загрузка чатов...",
            "message.loadingMessages" to "Загрузка сообщений...",

            // Errors
            "error.createChat" to "Пожалуйста, сначала создайте чат",
            "error.failedToCreateChat" to "Не удалось создать чат",
            "error.failedToLoadMessages" to "Не удалось загрузить сообщения",
            "error.failedToUpdateProvider" to "Не удалось обновить провайдера",
            "error.failedToUpdateTitle" to "Не удалось обновить название",
            "error.failedToDeleteChat" to "Не удалось удалить чат",
            "error.failedToSendMessage" to "Ошибка",

            // Settings
            "settings.language" to "Язык"
        )
    )
}
