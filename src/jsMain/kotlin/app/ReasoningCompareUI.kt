package app

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.*

class ReasoningCompareUI {
    private val scope = MainScope()
    private val apiClient = BackendApiClient()

    private val taskInput: HTMLTextAreaElement
    private val compareBtn: HTMLButtonElement
    private val providerSelect: HTMLSelectElement
    private val errorContainer: HTMLDivElement
    private val resultsGrid: HTMLDivElement

    private val directContent: HTMLDivElement
    private val singleContent: HTMLDivElement
    private val twoContent: HTMLDivElement
    private val chainContent: HTMLDivElement

    init {
        // Get elements
        taskInput = document.getElementById("taskInput") as HTMLTextAreaElement
        compareBtn = document.getElementById("compareBtn") as HTMLButtonElement
        providerSelect = document.getElementById("providerSelect") as HTMLSelectElement
        errorContainer = document.getElementById("errorContainer") as HTMLDivElement
        resultsGrid = document.getElementById("resultsGrid") as HTMLDivElement

        directContent = document.getElementById("directContent") as HTMLDivElement
        singleContent = document.getElementById("singleContent") as HTMLDivElement
        twoContent = document.getElementById("twoContent") as HTMLDivElement
        chainContent = document.getElementById("chainContent") as HTMLDivElement

        // Setup event listeners
        compareBtn.onclick = {
            handleCompare()
            null
        }

        // Enter key to submit (Ctrl+Enter or Cmd+Enter)
        taskInput.onkeydown = { event ->
            if ((event.ctrlKey || event.metaKey) && event.key == "Enter") {
                event.preventDefault()
                handleCompare()
            }
            null
        }
    }

    private fun handleCompare() {
        val task = taskInput.value.trim()
        if (task.isEmpty()) {
            showError("Пожалуйста, введите задачу для решения")
            return
        }

        val provider = providerSelect.value

        scope.launch {
            compareBtn.disabled = true
            compareBtn.textContent = "⏳ Сравниваем..."
            hideError()
            showLoadingStates()

            try {
                val result = apiClient.compareReasoningApproaches(task, provider)

                result.fold(
                    onSuccess = { response ->
                        displayResults(response)
                        compareBtn.disabled = false
                        compareBtn.textContent = "🚀 Сравнить подходы"
                    },
                    onFailure = { error ->
                        showError("Ошибка при сравнении подходов: ${error.message}")
                        hideLoadingStates()
                        compareBtn.disabled = false
                        compareBtn.textContent = "🚀 Сравнить подходы"
                    }
                )
            } catch (e: Exception) {
                showError("Ошибка: ${e.message}")
                hideLoadingStates()
                compareBtn.disabled = false
                compareBtn.textContent = "🚀 Сравнить подходы"
            }
        }
    }

    private fun showLoadingStates() {
        listOf(directContent, singleContent, twoContent, chainContent).forEach { content ->
            content.innerHTML = """
                <div class="loading-spinner"></div>
                <p style="text-align: center; margin-top: 10px; color: #666;">Загрузка...</p>
            """.trimIndent()
        }
    }

    private fun hideLoadingStates() {
        listOf(directContent, singleContent, twoContent, chainContent).forEach { content ->
            content.innerHTML = """
                <p style="color: #999; font-style: italic;">Результат недоступен</p>
            """.trimIndent()
        }
    }

    private fun displayResults(response: CompareReasoningResponse) {
        response.approaches.forEach { approach ->
            val content = when (approach.name) {
                "Direct Answer" -> directContent
                "Expert Panel - Single Request" -> singleContent
                "Expert Panel - Two Requests" -> twoContent
                "Expert Panel - Chain" -> chainContent
                else -> null
            }

            if (content != null) {
                // Render markdown
                val formattedAnswer = renderMarkdown(approach.answer)
                content.innerHTML = formattedAnswer
            }
        }
    }

    private fun showError(message: String) {
        errorContainer.innerHTML = """
            <div class="error-message">
                ❌ $message
            </div>
        """.trimIndent()
    }

    private fun hideError() {
        errorContainer.innerHTML = ""
    }

    /**
     * Simple markdown renderer (matches ChatUI implementation)
     */
    private fun renderMarkdown(text: String): String {
        var html = text
            // Escape HTML
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        // Code blocks
        html = html.replace(Regex("```([\\s\\S]*?)```")) { match ->
            val code = match.groupValues[1].trim()
            "<pre style='background: #f5f5f5; padding: 12px; border-radius: 6px; overflow-x: auto; margin: 8px 0;'><code>$code</code></pre>"
        }

        // Inline code
        html = html.replace(Regex("`([^`]+)`")) { match ->
            "<code style='background: #f5f5f5; padding: 2px 6px; border-radius: 3px; font-family: monospace;'>${match.groupValues[1]}</code>"
        }

        // Bold
        html = html.replace(Regex("\\*\\*(.+?)\\*\\*")) { match ->
            "<strong>${match.groupValues[1]}</strong>"
        }

        // Headers
        html = html.split("\n").joinToString("\n") { line ->
            when {
                line.startsWith("#### ") -> "<h4 style='margin: 10px 0 6px 0; font-size: 1.1em;'>${line.substring(5)}</h4>"
                line.startsWith("### ") -> "<h3 style='margin: 12px 0 6px 0; font-size: 1.3em;'>${line.substring(4)}</h3>"
                line.startsWith("## ") -> "<h2 style='margin: 14px 0 8px 0; font-size: 1.4em;'>${line.substring(3)}</h2>"
                line.startsWith("# ") -> "<h1 style='margin: 16px 0 10px 0; font-size: 1.6em;'>${line.substring(2)}</h1>"
                else -> line
            }
        }

        // Lists
        val lines = html.split("\n").toMutableList()
        var inList = false
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.matches(Regex("^[-*+] .+"))) {
                val content = trimmed.substring(2)
                if (!inList) {
                    lines[i] = "<ul style='margin: 6px 0; padding-left: 24px;'><li>$content</li>"
                    inList = true
                } else {
                    lines[i] = "<li>$content</li>"
                }
            } else if (inList) {
                lines[i - 1] = lines[i - 1] + "</ul>"
                inList = false
            }
            i++
        }
        if (inList && lines.isNotEmpty()) {
            lines[lines.size - 1] = lines[lines.size - 1] + "</ul>"
        }
        html = lines.joinToString("\n")

        // Newlines to <br>
        html = html.replace("\n", "<br>")

        return html
    }
}

// Initialize when on reasoning compare page
fun initReasoningCompare() {
    if (window.location.pathname.contains("reasoning-compare")) {
        ReasoningCompareUI()
    }
}
