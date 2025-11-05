package app

import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.w3c.dom.*

data class AdminUser(
    val id: Int,
    val email: String,
    val isAdmin: Boolean,
    val allowedProviders: String,
    val requestLimit: Int,
    val requestCount: Int
)

class AdminUI {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val apiClient = BackendApiClient()

    private var users = mutableListOf<AdminUser>()
    private var editingUserId: Int? = null

    // DOM elements
    private val usersTableBody: HTMLTableSectionElement
    private val createUserBtn: HTMLButtonElement
    private val backToChatBtn: HTMLButtonElement
    private val userModal: HTMLDivElement
    private val modalTitle: HTMLHeadingElement
    private val userEmail: HTMLInputElement
    private val userPassword: HTMLInputElement
    private val userIsAdmin: HTMLInputElement
    private val userProviders: HTMLInputElement
    private val userUnlimited: HTMLInputElement
    private val userLimit: HTMLInputElement
    private val limitContainer: HTMLDivElement
    private val saveUserBtn: HTMLButtonElement
    private val cancelUserBtn: HTMLButtonElement
    private val adminCheckboxContainer: HTMLDivElement
    private val deleteModal: HTMLDivElement
    private val deleteUserEmail: HTMLSpanElement
    private val confirmDeleteBtn: HTMLButtonElement
    private val cancelDeleteBtn: HTMLButtonElement
    private val passwordContainer: HTMLDivElement
    private val messageContainer: HTMLDivElement

    private var userToDelete: Int? = null

    init {
        console.log("AdminUI: Initializing...")

        // Check authentication
        val token = localStorage["jwt_token"]
        val currentUserEmail = localStorage["user_email"]

        console.log("AdminUI: token exists:", token != null)
        console.log("AdminUI: user_email:", currentUserEmail)

        if (token == null) {
            console.error("AdminUI: No token found, redirecting to login")
            window.location.href = "/"
            throw RuntimeException("Not authenticated, redirecting...")
        }

        // Check if user is admin
        if (currentUserEmail != "alexseera@yandex.ru") {
            console.error("AdminUI: Access denied - User is not admin, email:", currentUserEmail)
            window.location.href = "/chats"
            throw RuntimeException("Access denied, redirecting to chats...")
        }

        console.log("AdminUI: Admin check passed, loading DOM elements...")

        try {
            // Get DOM elements with individual error handling
            usersTableBody = (document.getElementById("usersTableBody") as? HTMLTableSectionElement)
                ?: throw RuntimeException("usersTableBody not found")
            createUserBtn = (document.getElementById("createUserBtn") as? HTMLButtonElement)
                ?: throw RuntimeException("createUserBtn not found")
            backToChatBtn = (document.getElementById("backToChatBtn") as? HTMLButtonElement)
                ?: throw RuntimeException("backToChatBtn not found")
            userModal = (document.getElementById("userModal") as? HTMLDivElement)
                ?: throw RuntimeException("userModal not found")
            modalTitle = (document.getElementById("modalTitle") as? HTMLHeadingElement)
                ?: throw RuntimeException("modalTitle not found")
            userEmail = (document.getElementById("userEmail") as? HTMLInputElement)
                ?: throw RuntimeException("userEmail not found")
            userPassword = (document.getElementById("userPassword") as? HTMLInputElement)
                ?: throw RuntimeException("userPassword not found")
            userIsAdmin = (document.getElementById("userIsAdmin") as? HTMLInputElement)
                ?: throw RuntimeException("userIsAdmin not found")
            userProviders = (document.getElementById("userProviders") as? HTMLInputElement)
                ?: throw RuntimeException("userProviders not found")
            userUnlimited = (document.getElementById("userUnlimited") as? HTMLInputElement)
                ?: throw RuntimeException("userUnlimited not found")
            userLimit = (document.getElementById("userLimit") as? HTMLInputElement)
                ?: throw RuntimeException("userLimit not found")
            limitContainer = (document.getElementById("limitContainer") as? HTMLDivElement)
                ?: throw RuntimeException("limitContainer not found")
            saveUserBtn = (document.getElementById("saveUserBtn") as? HTMLButtonElement)
                ?: throw RuntimeException("saveUserBtn not found")
            cancelUserBtn = (document.getElementById("cancelUserBtn") as? HTMLButtonElement)
                ?: throw RuntimeException("cancelUserBtn not found")
            adminCheckboxContainer = (document.getElementById("adminCheckboxContainer") as? HTMLDivElement)
                ?: throw RuntimeException("adminCheckboxContainer not found")
            deleteModal = (document.getElementById("deleteModal") as? HTMLDivElement)
                ?: throw RuntimeException("deleteModal not found")
            deleteUserEmail = (document.getElementById("deleteUserEmail") as? HTMLSpanElement)
                ?: throw RuntimeException("deleteUserEmail not found")
            confirmDeleteBtn = (document.getElementById("confirmDeleteBtn") as? HTMLButtonElement)
                ?: throw RuntimeException("confirmDeleteBtn not found")
            cancelDeleteBtn = (document.getElementById("cancelDeleteBtn") as? HTMLButtonElement)
                ?: throw RuntimeException("cancelDeleteBtn not found")
            passwordContainer = (document.getElementById("passwordContainer") as? HTMLDivElement)
                ?: throw RuntimeException("passwordContainer not found")
            messageContainer = (document.getElementById("messageContainer") as? HTMLDivElement)
                ?: throw RuntimeException("messageContainer not found")

            console.log("AdminUI: All DOM elements loaded successfully")

            setupEventListeners()
            loadUsers()
        } catch (e: Exception) {
            console.error("AdminUI: Failed to load DOM elements:", e.message)
            throw e
        }
    }

    private fun setupEventListeners() {
        createUserBtn.onclick = {
            showCreateUserModal()
            null
        }

        backToChatBtn.onclick = {
            window.location.href = "/chats"
            null
        }

        saveUserBtn.onclick = {
            handleSaveUser()
            null
        }

        cancelUserBtn.onclick = {
            hideUserModal()
            null
        }

        confirmDeleteBtn.onclick = {
            handleConfirmDelete()
            null
        }

        cancelDeleteBtn.onclick = {
            hideDeleteModal()
            null
        }

        userModal.onclick = { event ->
            if (event.target == userModal) {
                hideUserModal()
            }
            null
        }

        deleteModal.onclick = { event ->
            if (event.target == deleteModal) {
                hideDeleteModal()
            }
            null
        }

        // Toggle limit input visibility based on unlimited checkbox
        userUnlimited.onchange = {
            if (userUnlimited.checked) {
                limitContainer.style.display = "none"
            } else {
                limitContainer.style.display = "block"
            }
            null
        }
    }

    private fun loadUsers() {
        scope.launch {
            try {
                val result = apiClient.getUsers()
                result.fold(
                    onSuccess = { usersList ->
                        users = usersList.toMutableList()
                        renderUsers()
                    },
                    onFailure = { error ->
                        console.error("Failed to load users", error)

                        // Check if it's an authorization error
                        if (error.message?.contains("403") == true || error.message?.contains("Forbidden") == true) {
                            showError("Access denied: You don't have admin privileges")
                            window.setTimeout({
                                window.location.href = "/chats"
                            }, 2000)
                        } else {
                            showError("Failed to load users: ${error.message}")
                        }
                    }
                )
            } catch (e: Exception) {
                showError("Failed to load users: ${e.message}")
                console.error("Error loading users", e)
            }
        }
    }

    private fun renderUsers() {
        usersTableBody.innerHTML = ""

        if (users.isEmpty()) {
            val row = document.createElement("tr") as HTMLTableRowElement
            val cell = document.createElement("td") as HTMLTableCellElement
            cell.colSpan = 7
            cell.style.textAlign = "center"
            cell.style.padding = "30px"
            cell.textContent = "No users found"
            row.appendChild(cell)
            usersTableBody.appendChild(row)
            return
        }

        users.forEach { user ->
            val row = document.createElement("tr") as HTMLTableRowElement

            // ID
            val idCell = document.createElement("td") as HTMLTableCellElement
            idCell.textContent = user.id.toString()
            row.appendChild(idCell)

            // Email
            val emailCell = document.createElement("td") as HTMLTableCellElement
            emailCell.textContent = user.email
            row.appendChild(emailCell)

            // Admin
            val adminCell = document.createElement("td") as HTMLTableCellElement
            adminCell.textContent = if (user.isAdmin) "✅ Yes" else "❌ No"
            row.appendChild(adminCell)

            // Allowed Providers
            val providersCell = document.createElement("td") as HTMLTableCellElement
            val providers = user.allowedProviders.split(",")
            providers.forEach { provider ->
                val tag = document.createElement("span") as HTMLSpanElement
                tag.className = "provider-tag"
                tag.textContent = provider.trim()
                providersCell.appendChild(tag)
            }
            row.appendChild(providersCell)

            // Request Limit
            val limitCell = document.createElement("td") as HTMLTableCellElement
            limitCell.textContent = if (user.requestLimit == -1) "♾️ Unlimited" else user.requestLimit.toString()
            row.appendChild(limitCell)

            // Requests Used
            val usedCell = document.createElement("td") as HTMLTableCellElement
            usedCell.textContent = if (user.requestLimit == -1) {
                "${user.requestCount} / ∞"
            } else {
                "${user.requestCount} / ${user.requestLimit}"
            }
            row.appendChild(usedCell)

            // Actions
            val actionsCell = document.createElement("td") as HTMLTableCellElement
            val actionsDiv = document.createElement("div") as HTMLDivElement
            actionsDiv.className = "user-actions"

            // Edit button
            val editBtn = document.createElement("button") as HTMLButtonElement
            editBtn.className = "action-btn action-btn-edit"
            editBtn.textContent = "✏️ Edit"
            editBtn.onclick = {
                showEditUserModal(user)
                null
            }
            actionsDiv.appendChild(editBtn)

            // Reset button
            val resetBtn = document.createElement("button") as HTMLButtonElement
            resetBtn.className = "action-btn action-btn-reset"
            resetBtn.textContent = "🔄 Reset"
            resetBtn.onclick = {
                handleResetRequests(user.id)
                null
            }
            actionsDiv.appendChild(resetBtn)

            // Delete button (disabled for admins)
            if (!user.isAdmin) {
                val deleteBtn = document.createElement("button") as HTMLButtonElement
                deleteBtn.className = "action-btn action-btn-delete"
                deleteBtn.textContent = "🗑️ Delete"
                deleteBtn.onclick = {
                    showDeleteModal(user)
                    null
                }
                actionsDiv.appendChild(deleteBtn)
            }

            actionsCell.appendChild(actionsDiv)
            row.appendChild(actionsCell)

            usersTableBody.appendChild(row)
        }
    }

    private fun showCreateUserModal() {
        editingUserId = null
        modalTitle.textContent = "Create User"
        userEmail.value = ""
        userPassword.value = ""
        userIsAdmin.checked = false
        userProviders.value = "deepseek,claude"
        userUnlimited.checked = false
        userLimit.value = "100"
        limitContainer.style.display = "block"
        passwordContainer.style.display = "block"
        adminCheckboxContainer.style.display = "block"
        userModal.classList.add("show")
    }

    private fun showEditUserModal(user: AdminUser) {
        editingUserId = user.id
        modalTitle.textContent = "Edit User"
        userEmail.value = user.email
        userEmail.disabled = true
        userPassword.value = ""
        userIsAdmin.checked = user.isAdmin
        userProviders.value = user.allowedProviders

        // Handle unlimited requests (-1)
        if (user.requestLimit == -1) {
            userUnlimited.checked = true
            userLimit.value = "100"
            limitContainer.style.display = "none"
        } else {
            userUnlimited.checked = false
            userLimit.value = user.requestLimit.toString()
            limitContainer.style.display = "block"
        }

        passwordContainer.style.display = "none"
        adminCheckboxContainer.style.display = "block"
        userModal.classList.add("show")
    }

    private fun hideUserModal() {
        userModal.classList.remove("show")
        userEmail.disabled = false
        editingUserId = null
    }

    private fun handleSaveUser() {
        val email = userEmail.value.trim()
        val password = userPassword.value
        val isAdmin = userIsAdmin.checked
        val providers = userProviders.value.trim()

        // If unlimited is checked, use -1, otherwise get the limit value
        val limit = if (userUnlimited.checked) {
            -1
        } else {
            userLimit.value.toIntOrNull()
        }

        if (email.isBlank()) {
            showError("Email is required")
            return
        }

        if (providers.isBlank()) {
            showError("At least one provider must be specified")
            return
        }

        if (!userUnlimited.checked && (limit == null || limit < 1)) {
            showError("Request limit must be at least 1 or enable unlimited")
            return
        }

        val userId = editingUserId
        if (userId == null) {
            // Create new user
            if (password.length < 6) {
                showError("Password must be at least 6 characters")
                return
            }

            scope.launch {
                try {
                    val result = apiClient.createUser(email, password, providers, limit ?: 100)
                    result.fold(
                        onSuccess = {
                            hideUserModal()
                            showSuccess("User created successfully")
                            loadUsers()
                        },
                        onFailure = { error ->
                            showError("Failed to create user: ${error.message}")
                        }
                    )
                } catch (e: Exception) {
                    showError("Failed to create user: ${e.message}")
                }
            }
        } else {
            // Update existing user
            scope.launch {
                try {
                    val result = apiClient.updateUser(userId, isAdmin, providers, limit ?: 100)
                    result.fold(
                        onSuccess = {
                            hideUserModal()
                            showSuccess("User updated successfully")
                            loadUsers()
                        },
                        onFailure = { error ->
                            showError("Failed to update user: ${error.message}")
                        }
                    )
                } catch (e: Exception) {
                    showError("Failed to update user: ${e.message}")
                }
            }
        }
    }

    private fun showDeleteModal(user: AdminUser) {
        userToDelete = user.id
        deleteUserEmail.textContent = user.email
        deleteModal.classList.add("show")
    }

    private fun hideDeleteModal() {
        deleteModal.classList.remove("show")
        userToDelete = null
    }

    private fun handleConfirmDelete() {
        val userId = userToDelete ?: return
        hideDeleteModal()

        scope.launch {
            try {
                val result = apiClient.deleteUser(userId)
                result.fold(
                    onSuccess = {
                        showSuccess("User deleted successfully")
                        loadUsers()
                    },
                    onFailure = { error ->
                        showError("Failed to delete user: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                showError("Failed to delete user: ${e.message}")
            }
        }
    }

    private fun handleResetRequests(userId: Int) {
        scope.launch {
            try {
                val result = apiClient.resetUserRequests(userId)
                result.fold(
                    onSuccess = {
                        showSuccess("Request count reset successfully")
                        loadUsers()
                    },
                    onFailure = { error ->
                        showError("Failed to reset request count: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                showError("Failed to reset request count: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        messageContainer.innerHTML = "<div class='error-message'>$message</div>"
        window.setTimeout({ messageContainer.innerHTML = "" }, 5000)
    }

    private fun showSuccess(message: String) {
        messageContainer.innerHTML = "<div class='success-message'>$message</div>"
        window.setTimeout({ messageContainer.innerHTML = "" }, 3000)
    }
}
