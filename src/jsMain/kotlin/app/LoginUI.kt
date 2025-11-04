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
import org.w3c.dom.HTMLInputElement
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

        setupEventListeners()
    }

    private fun setupEventListeners() {
        loginForm.addEventListener("submit", { event ->
            event.preventDefault()
            handleLogin()
            null
        })
    }

    private fun handleLogin() {
        val email = emailInput.value.trim()
        val password = passwordInput.value.trim()

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields")
            return
        }

        loginBtn.disabled = true
        loginBtn.textContent = "Signing in..."
        hideError()

        scope.launch {
            try {
                val result = apiClient.login(email, password)

                result.fold(
                    onSuccess = { response ->
                        // Save JWT token
                        localStorage["jwt_token"] = response.token
                        localStorage["user_id"] = response.user.id.toString()
                        localStorage["user_email"] = response.user.email

                        // Redirect to chats
                        window.location.href = "/chats"
                    },
                    onFailure = { error ->
                        showError(error.message ?: "Login failed. Please check your credentials.")
                        loginBtn.disabled = false
                        loginBtn.textContent = "Sign In"
                    }
                )
            } catch (e: Exception) {
                console.error("Login error", e)
                showError("An error occurred. Please try again.")
                loginBtn.disabled = false
                loginBtn.textContent = "Sign In"
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
