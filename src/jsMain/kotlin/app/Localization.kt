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
            "modal.renameTitle" to "Rename Chat",
            "modal.renameLabel" to "New chat name:",
            "modal.renamePlaceholder" to "Enter chat name...",
            "modal.renameButton" to "Rename",
            "modal.settingsTitle" to "AI Parameters",
            "modal.temperatureLabel" to "Temperature (0-2):",
            "modal.temperaturePlaceholder" to "Default: depends on provider",
            "modal.maxTokensLabel" to "Max Tokens:",
            "modal.maxTokensPlaceholder" to "Default: 8192 (Claude) / unlimited (DeepSeek)",
            "modal.topPLabel" to "Top P (0-1):",
            "modal.topPPlaceholder" to "Default: 1",
            "modal.systemPromptLabel" to "System Prompt:",
            "modal.systemPromptPlaceholder" to "Optional system prompt...",
            "modal.settingsHint" to "Leave fields empty to use default values",
            "modal.saveButton" to "Save",

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
            "error.failedToSendMessage" to "Failed to send message",
            "error.tryAgain" to "Something went wrong. Please try again.",
            "error.failedToLoadSettings" to "Failed to load settings",
            "error.failedToSaveSettings" to "Failed to save settings",
            "error.pleaseSelectChat" to "Please select or create a chat first",

            // Settings
            "settings.language" to "Language",
            "settings.button" to "Settings",

            // Login
            "login.title" to "Ai Chats",
            "login.subtitle" to "Welcome back! Please login to continue.",
            "login.emailLabel" to "Email",
            "login.emailPlaceholder" to "Enter your email",
            "login.passwordLabel" to "Password",
            "login.passwordPlaceholder" to "Enter your password",
            "login.submitButton" to "Sign In",
            "login.submitting" to "Signing in..."
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
            "modal.renameTitle" to "Переименовать чат",
            "modal.renameLabel" to "Новое название чата:",
            "modal.renamePlaceholder" to "Введите название чата...",
            "modal.renameButton" to "Переименовать",
            "modal.settingsTitle" to "Параметры AI",
            "modal.temperatureLabel" to "Температура (0-2):",
            "modal.temperaturePlaceholder" to "По умолчанию: зависит от провайдера",
            "modal.maxTokensLabel" to "Макс. токенов:",
            "modal.maxTokensPlaceholder" to "По умолчанию: 8192 (Claude) / без ограничений (DeepSeek)",
            "modal.topPLabel" to "Top P (0-1):",
            "modal.topPPlaceholder" to "По умолчанию: 1",
            "modal.systemPromptLabel" to "Системный промпт:",
            "modal.systemPromptPlaceholder" to "Опциональный системный промпт...",
            "modal.settingsHint" to "Оставьте поля пустыми для использования значений по умолчанию",
            "modal.saveButton" to "Сохранить",

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
            "error.failedToSendMessage" to "Не удалось отправить сообщение",
            "error.tryAgain" to "Что-то пошло не так. Попробуйте снова.",
            "error.failedToLoadSettings" to "Не удалось загрузить настройки",
            "error.failedToSaveSettings" to "Не удалось сохранить настройки",
            "error.pleaseSelectChat" to "Пожалуйста, выберите или создайте чат",

            // Settings
            "settings.language" to "Язык",
            "settings.button" to "Настройки",

            // Login
            "login.title" to "Ai Chats",
            "login.subtitle" to "Добро пожаловать! Войдите, чтобы продолжить.",
            "login.emailLabel" to "Email",
            "login.emailPlaceholder" to "Введите ваш email",
            "login.passwordLabel" to "Пароль",
            "login.passwordPlaceholder" to "Введите ваш пароль",
            "login.submitButton" to "Войти",
            "login.submitting" to "Вход..."
        )
    )
}
