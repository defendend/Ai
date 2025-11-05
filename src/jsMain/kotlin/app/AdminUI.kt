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
    private val userLimit: HTMLInputElement
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
        // Check authentication
        val token = localStorage["jwt_token"]
        if (token == null) {
            window.location.href = "/"
            throw RuntimeException("Not authenticated, redirecting...")
        }

        // Get DOM elements
        usersTableBody = document.getElementById("usersTableBody") as HTMLTableSectionElement
        createUserBtn = document.getElementById("createUserBtn") as HTMLButtonElement
        backToChatBtn = document.getElementById("backToChatBtn") as HTMLButtonElement
        userModal = document.getElementById("userModal") as HTMLDivElement
        modalTitle = document.getElementById("modalTitle") as HTMLHeadingElement
        userEmail = document.getElementById("userEmail") as HTMLInputElement
        userPassword = document.getElementById("userPassword") as HTMLInputElement
        userIsAdmin = document.getElementById("userIsAdmin") as HTMLInputElement
        userProviders = document.getElementById("userProviders") as HTMLInputElement
        userLimit = document.getElementById("userLimit") as HTMLInputElement
        saveUserBtn = document.getElementById("saveUserBtn") as HTMLButtonElement
        cancelUserBtn = document.getElementById("cancelUserBtn") as HTMLButtonElement
        adminCheckboxContainer = document.getElementById("adminCheckboxContainer") as HTMLDivElement
        deleteModal = document.getElementById("deleteModal") as HTMLDivElement
        deleteUserEmail = document.getElementById("deleteUserEmail") as HTMLSpanElement
        confirmDeleteBtn = document.getElementById("confirmDeleteBtn") as HTMLButtonElement
        cancelDeleteBtn = document.getElementById("cancelDeleteBtn") as HTMLButtonElement
        passwordContainer = document.getElementById("passwordContainer") as HTMLDivElement
        messageContainer = document.getElementById("messageContainer") as HTMLDivElement

        setupEventListeners()
        loadUsers()
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
                        showError("Failed to load users: ${error.message}")
                        console.error("Failed to load users", error)
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
            limitCell.textContent = user.requestLimit.toString()
            row.appendChild(limitCell)

            // Requests Used
            val usedCell = document.createElement("td") as HTMLTableCellElement
            usedCell.textContent = "${user.requestCount} / ${user.requestLimit}"
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
        userLimit.value = "100"
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
        userLimit.value = user.requestLimit.toString()
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
        val limit = userLimit.value.toIntOrNull()

        if (email.isBlank()) {
            showError("Email is required")
            return
        }

        if (providers.isBlank()) {
            showError("At least one provider must be specified")
            return
        }

        if (limit == null || limit < 1) {
            showError("Request limit must be at least 1")
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
                    val result = apiClient.createUser(email, password, providers, limit)
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
                    val result = apiClient.updateUser(userId, isAdmin, providers, limit)
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
