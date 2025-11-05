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
            "modal.temperaturePlaceholder" to "Default: provider-specific",
            "modal.maxTokensLabel" to "Max Tokens:",
            "modal.maxTokensPlaceholder" to "Default: 8192 / unlimited",
            "modal.topPLabel" to "Top P (0-1):",
            "modal.topPPlaceholder" to "Default: 1",
            "modal.systemPromptLabel" to "System Prompt:",
            "modal.systemPromptPlaceholder" to "Optional system prompt...",
            "modal.settingsHint" to "Leave fields empty to use default values",
            "modal.saveButton" to "Save",

            // Tooltips for AI parameters
            "tooltip.temperature" to "Controls creativity and randomness of responses. Lower values (0-0.5) make answers more focused and consistent. Higher values (1-2) make answers more creative and varied. Try 0.7 for balanced responses.",
            "tooltip.maxTokens" to "Maximum length of AI response. One token ≈ 4 characters. Leave empty for maximum length. Reduce if you need shorter answers to save costs.",
            "tooltip.topP" to "Alternative way to control randomness. Value of 0.9 means AI considers only top 90% most likely words. Lower values make responses more predictable. Usually used instead of temperature.",
            "tooltip.systemPrompt" to "Special instructions that define AI behavior and role. For example: 'You are a helpful assistant that answers briefly' or 'You are an expert programmer'. Affects all responses in this chat.",
            "tooltip.streaming" to "When enabled, AI responses are displayed in real-time as they are generated, similar to ChatGPT. When disabled, you'll see the complete response at once after generation is complete.",
            "tooltip.responseFormat" to "Force AI to respond in structured format (JSON/XML). Useful when you need machine-readable output for further processing.",
            "tooltip.responseSchema" to "Define exact structure for JSON/XML responses. AI will follow this schema strictly. Leave empty to use default flexible schema.",
            "tooltip.responseStyle" to "Set the tone and manner of AI responses. Professional for business, friendly for casual chat, formal for official documents, etc.",
            "tooltip.responseLength" to "Control how verbose AI responses should be. Brief for quick answers (1-2 sentences), detailed for comprehensive explanations.",
            "tooltip.language" to "Specify which language AI should use for responses. Auto-detect lets AI choose based on your messages.",
            "tooltip.includeExamples" to "When enabled, AI will include relevant examples to illustrate points and make explanations clearer.",
            "tooltip.contentFormat" to "How content should be structured: paragraphs for essays, bullets for lists, steps for instructions, etc.",

            // Default values display
            "default.temperature.claude" to "Default for Claude: provider-specific",
            "default.temperature.deepseek" to "Default for DeepSeek: 0.7",
            "default.maxTokens.claude" to "Default for Claude: 8192",
            "default.maxTokens.deepseek" to "Default for DeepSeek: unlimited",
            "default.topP.claude" to "Default for Claude: 1.0",
            "default.topP.deepseek" to "Default for DeepSeek: 1.0",
            "default.systemPrompt" to "Default: none",

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
            "modal.temperaturePlaceholder" to "По умолчанию: зависит от AI",
            "modal.maxTokensLabel" to "Макс. токенов:",
            "modal.maxTokensPlaceholder" to "По умолчанию: 8192 / без лимита",
            "modal.topPLabel" to "Top P (0-1):",
            "modal.topPPlaceholder" to "По умолчанию: 1",
            "modal.systemPromptLabel" to "Системный промпт:",
            "modal.systemPromptPlaceholder" to "Опциональный системный промпт...",
            "modal.settingsHint" to "Оставьте поля пустыми для использования значений по умолчанию",
            "modal.saveButton" to "Сохранить",

            // Tooltips for AI parameters
            "tooltip.temperature" to "Контролирует креативность и случайность ответов. Низкие значения (0-0.5) делают ответы более сфокусированными и последовательными. Высокие значения (1-2) делают ответы более креативными и разнообразными. Попробуйте 0.7 для сбалансированных ответов.",
            "tooltip.maxTokens" to "Максимальная длина ответа AI. Один токен ≈ 4 символа. Оставьте пустым для максимальной длины. Уменьшите, если нужны более короткие ответы для экономии.",
            "tooltip.topP" to "Альтернативный способ контроля случайности. Значение 0.9 означает, что AI рассматривает только топ 90% наиболее вероятных слов. Меньшие значения делают ответы более предсказуемыми. Обычно используется вместо температуры.",
            "tooltip.systemPrompt" to "Специальные инструкции, определяющие поведение и роль AI. Например: 'Ты полезный помощник, который отвечает кратко' или 'Ты эксперт-программист'. Влияет на все ответы в этом чате.",
            "tooltip.streaming" to "При включении ответы AI отображаются в реальном времени по мере их генерации, как в ChatGPT. При отключении вы увидите полный ответ сразу после завершения генерации.",
            "tooltip.responseFormat" to "Заставить AI отвечать в структурированном формате (JSON/XML). Полезно, когда нужен машиночитаемый вывод для дальнейшей обработки.",
            "tooltip.responseSchema" to "Определите точную структуру для JSON/XML ответов. AI будет строго следовать этой схеме. Оставьте пустым для использования гибкой схемы по умолчанию.",
            "tooltip.responseStyle" to "Установите тон и манеру ответов AI. Professional для бизнеса, friendly для непринужденного общения, formal для официальных документов и т.д.",
            "tooltip.responseLength" to "Контролируйте многословность ответов AI. Brief для быстрых ответов (1-2 предложения), detailed для подробных объяснений.",
            "tooltip.language" to "Укажите, на каком языке AI должен отвечать. Auto-detect позволяет AI выбирать язык на основе ваших сообщений.",
            "tooltip.includeExamples" to "При включении AI будет добавлять релевантные примеры для иллюстрации и более понятных объяснений.",
            "tooltip.contentFormat" to "Как должен быть структурирован контент: paragraphs для эссе, bullets для списков, steps для инструкций и т.д.",

            // Default values display
            "default.temperature.claude" to "По умолчанию для Claude: по умолчанию провайдера",
            "default.temperature.deepseek" to "По умолчанию для DeepSeek: 0.7",
            "default.maxTokens.claude" to "По умолчанию для Claude: 8192",
            "default.maxTokens.deepseek" to "По умолчанию для DeepSeek: без ограничений",
            "default.topP.claude" to "По умолчанию для Claude: 1.0",
            "default.topP.deepseek" to "По умолчанию для DeepSeek: 1.0",
            "default.systemPrompt" to "По умолчанию: отсутствует",

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
