package app

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.*

class ModelComparisonUI {
    private val scope = MainScope()
    private val apiClient = BackendApiClient()

    private val promptInput: HTMLTextAreaElement
    private val compareBtn: HTMLButtonElement
    private val errorContainer: HTMLDivElement
    private val resultsContainer: HTMLDivElement
    private val modelCheckboxes: List<HTMLInputElement>

    init {
        // Get elements
        promptInput = document.getElementById("promptInput") as HTMLTextAreaElement
        compareBtn = document.getElementById("compareBtn") as HTMLButtonElement
        errorContainer = document.getElementById("errorContainer") as HTMLDivElement
        resultsContainer = document.getElementById("resultsContainer") as HTMLDivElement

        // Get all model checkboxes
        modelCheckboxes = listOf(
            document.getElementById("model-phi3") as HTMLInputElement,
            document.getElementById("model-llama32") as HTMLInputElement,
            document.getElementById("model-mistral") as HTMLInputElement,
            document.getElementById("model-qwen") as HTMLInputElement
        )

        // Setup event listeners
        compareBtn.onclick = {
            handleCompare()
            null
        }

        // Enter key to submit (Ctrl+Enter or Cmd+Enter)
        promptInput.onkeydown = { event ->
            if ((event.ctrlKey || event.metaKey) && event.key == "Enter") {
                event.preventDefault()
                handleCompare()
            }
            null
        }

        // Load available models
        loadAvailableModels()
    }

    private fun loadAvailableModels() {
        scope.launch {
            try {
                val result = apiClient.getAvailableModels()
                result.fold(
                    onSuccess = { models ->
                        console.log("Available models:", models)
                    },
                    onFailure = { error ->
                        console.error("Failed to load models:", error)
                    }
                )
            } catch (e: Exception) {
                console.error("Error loading models:", e)
            }
        }
    }

    private fun handleCompare() {
        val prompt = promptInput.value.trim()
        if (prompt.isEmpty()) {
            showError("Пожалуйста, введите prompt для сравнения")
            return
        }

        // Get selected models
        val selectedModels = modelCheckboxes
            .filter { it.checked }
            .mapNotNull { it.getAttribute("data-model-id") }

        if (selectedModels.isEmpty()) {
            showError("Пожалуйста, выберите хотя бы одну модель")
            return
        }

        compareBtn.disabled = true
        compareBtn.textContent = "⏳ Сравниваем..."
        hideError()
        showLoadingStates(selectedModels.size)

        scope.launch {
            try {
                val result = apiClient.compareModels(prompt, selectedModels)

                result.fold(
                    onSuccess = { comparisonResult ->
                        displayResults(comparisonResult)
                    },
                    onFailure = { error ->
                        showError("Ошибка: ${error.message}")
                        resultsContainer.innerHTML = ""
                    }
                )
            } catch (e: Exception) {
                showError("Ошибка: ${e.message}")
                resultsContainer.innerHTML = ""
            } finally {
                compareBtn.disabled = false
                compareBtn.textContent = "🚀 Сравнить модели"
            }
        }
    }

    private fun showLoadingStates(count: Int) {
        val loadingHTML = (1..count).joinToString("") {
            """
            <div class="result-card">
                <div class="loading-spinner"></div>
                <p style="text-align: center; margin-top: 10px; color: #666;">Загрузка...</p>
            </div>
            """.trimIndent()
        }
        resultsContainer.innerHTML = loadingHTML
    }

    private fun displayResults(comparisonResult: ModelComparisonResult) {
        val sortedResults = comparisonResult.results.sortedBy { it.responseTimeMs }

        val resultsHTML = sortedResults.joinToString("") { metrics ->
            val statusIcon = if (metrics.error == null) "✅" else "❌"
            val timeColor = when {
                metrics.responseTimeMs < 5000 -> "#4caf50"
                metrics.responseTimeMs < 15000 -> "#ff9800"
                else -> "#f44336"
            }

            val responseContent = if (metrics.error != null) {
                """<p style="color: #d32f2f;">❌ Ошибка: ${metrics.error}</p>"""
            } else {
                """<div class="response-text">${escapeHtml(metrics.response)}</div>"""
            }

            """
            <div class="result-card">
                <div class="result-header">
                    <h3>$statusIcon ${metrics.modelName}</h3>
                    <span class="model-size">${metrics.modelSize}</span>
                </div>

                <div class="metrics-grid">
                    <div class="metric">
                        <span class="metric-label">⏱️ Время ответа:</span>
                        <span class="metric-value" style="color: $timeColor">
                            ${formatTime(metrics.responseTimeMs)}
                        </span>
                    </div>
                    <div class="metric">
                        <span class="metric-label">📊 Токенов:</span>
                        <span class="metric-value">${metrics.tokensGenerated ?: "N/A"}</span>
                    </div>
                    <div class="metric">
                        <span class="metric-label">💰 Стоимость:</span>
                        <span class="metric-value">
                            ${if (metrics.estimatedCost != null && metrics.estimatedCost > 0)
                                "$${metrics.estimatedCost}"
                              else "FREE"}
                        </span>
                    </div>
                </div>

                $responseContent
            </div>
            """.trimIndent()
        }

        // Add summary
        val summaryHTML = """
            <div class="summary-card">
                <h3>📊 Сводка сравнения</h3>
                <p><strong>Prompt:</strong> ${escapeHtml(comparisonResult.prompt)}</p>
                <p><strong>Моделей протестировано:</strong> ${comparisonResult.results.size}</p>
                <p><strong>Успешных:</strong> ${comparisonResult.results.count { it.error == null }}</p>
                <p><strong>С ошибками:</strong> ${comparisonResult.results.count { it.error != null }}</p>
                <p><strong>Самая быстрая:</strong> ${sortedResults.firstOrNull { it.error == null }?.modelName ?: "N/A"}</p>
                <p><strong>Дата:</strong> ${formatTimestamp(comparisonResult.timestamp)}</p>
            </div>
        """.trimIndent()

        resultsContainer.innerHTML = summaryHTML + resultsHTML
    }

    private fun formatTime(ms: Long): String {
        return if (ms < 1000) {
            "${ms}ms"
        } else {
            "${ms / 1000}.${(ms % 1000) / 100}s"
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = kotlin.js.Date(timestamp.toDouble())
        return date.toLocaleString("ru-RU", js("{}"))
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#039;")
    }

    private fun showError(message: String) {
        errorContainer.textContent = message
        errorContainer.style.display = "block"
    }

    private fun hideError() {
        errorContainer.style.display = "none"
    }
}
