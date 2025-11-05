package app

import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLHeadingElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLLabelElement
import org.w3c.dom.HTMLParagraphElement
import org.w3c.dom.events.Event
import org.w3c.dom.get
import org.w3c.dom.set

class LoginUI {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val apiClient = BackendApiClient()

    private val loginForm: HTMLFormElement
    private val emailInput: HTMLInputElement
    private val passwordInput: HTMLInputElement
    private val loginBtn: HTMLButtonElement
    private val errorMessage: HTMLDivElement
    private val loginTitle: HTMLHeadingElement
    private val loginSubtitle: HTMLParagraphElement
    private val emailLabel: HTMLLabelElement
    private val passwordLabel: HTMLLabelElement

    init {
        // Check if already logged in
        val token = localStorage["jwt_token"]
        if (token != null) {
            window.location.href = "/chats"
            throw RuntimeException("Already logged in, redirecting...")
        }

        loginForm = document.getElementById("loginForm") as HTMLFormElement
        emailInput = document.getElementById("email") as HTMLInputElement
        passwordInput = document.getElementById("password") as HTMLInputElement
        loginBtn = document.getElementById("loginBtn") as HTMLButtonElement
        errorMessage = document.getElementById("errorMessage") as HTMLDivElement
        loginTitle = document.getElementById("loginTitle") as HTMLHeadingElement
        loginSubtitle = document.getElementById("loginSubtitle") as HTMLParagraphElement
        emailLabel = document.getElementById("emailLabel") as HTMLLabelElement
        passwordLabel = document.getElementById("passwordLabel") as HTMLLabelElement

        updateUITexts()
        setupEventListeners()
    }

    private fun setupEventListeners() {
        loginForm.addEventListener("submit", { event ->
            event.preventDefault()
            handleLogin()
            null
        })
    }

    private fun updateUITexts() {
        loginTitle.textContent = Localization.t("login.title")
        loginSubtitle.textContent = Localization.t("login.subtitle")
        emailLabel.textContent = Localization.t("login.emailLabel")
        passwordLabel.textContent = Localization.t("login.passwordLabel")
        emailInput.placeholder = Localization.t("login.emailPlaceholder")
        passwordInput.placeholder = Localization.t("login.passwordPlaceholder")
        loginBtn.textContent = Localization.t("login.submitButton")
    }

    private fun handleLogin() {
        val email = emailInput.value.trim()
        val password = passwordInput.value.trim()

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields")
            return
        }

        loginBtn.disabled = true
        loginBtn.textContent = Localization.t("login.submitting")
        hideError()

        scope.launch {
            try {
                val result = apiClient.login(email, password)

                result.fold(
                    onSuccess = { response ->
                        // Save JWT token and user info
                        localStorage["jwt_token"] = response.token
                        localStorage["user_id"] = response.user.id.toString()
                        localStorage["user_email"] = response.user.email
                        localStorage["is_admin"] = response.user.isAdmin.toString()

                        // Redirect to chats
                        window.location.href = "/chats"
                    },
                    onFailure = { error ->
                        showError(error.message ?: "Login failed. Please check your credentials.")
                        loginBtn.disabled = false
                        loginBtn.textContent = Localization.t("login.submitButton")
                    }
                )
            } catch (e: Exception) {
                console.error("Login error", e)
                showError("An error occurred. Please try again.")
                loginBtn.disabled = false
                loginBtn.textContent = Localization.t("login.submitButton")
            }
        }
    }

    private fun showError(message: String) {
        errorMessage.textContent = message
        errorMessage.style.display = "block"
    }

    private fun hideError() {
        errorMessage.style.display = "none"
    }
}
