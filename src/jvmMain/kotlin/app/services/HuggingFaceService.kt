package app.services

import app.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlin.system.measureTimeMillis

object HuggingFaceService {
    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = 180_000  // 3 minutes for model loading

            endpoint {
                connectTimeout = 60_000
                socketTimeout = 180_000
                connectAttempts = 3
                keepAliveTime = 60_000
                maxConnectionsCount = 10
            }
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
            sanitizeHeader { header -> header == "Authorization" }
        }

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    // Model information - using chat-compatible models from Router API
    private val modelInfo = mapOf(
        "google/gemma-2-2b-it" to ModelInfo("2B", 0.0), // Free - small model
        "Qwen/Qwen3-4B-Thinking-2507" to ModelInfo("4B", 0.0), // Free - medium model with reasoning
        "Qwen/Qwen2.5-Coder-32B-Instruct" to ModelInfo("32B", 0.0), // Free - large model
        "openai/gpt-oss-120b" to ModelInfo("120B", 0.0) // Free - very large model
    )

    data class ModelInfo(
        val size: String,
        val costPer1kTokens: Double
    )

    /**
     * Send a prompt to a specific HuggingFace model and measure metrics
     */
    suspend fun sendToModel(
        modelId: String,
        prompt: String,
        temperature: Double = 0.7,
        maxNewTokens: Int = 150
    ): ModelMetrics {
        val apiKey = System.getenv("HUGGINGFACE_API_KEY")
            ?: throw IllegalStateException("HUGGINGFACE_API_KEY environment variable is not set")

        val info = modelInfo[modelId] ?: ModelInfo("Unknown", 0.0)
        val startTime = System.currentTimeMillis()

        return try {
            val request = HuggingFaceRequest(
                model = modelId,
                messages = listOf(Message(role = "user", content = prompt)),
                temperature = temperature,
                maxTokens = maxNewTokens,
                topP = 0.9,
                stream = false
            )

            var response: String? = null
            var tokensGenerated: Int = 0

            val responseTimeMs = measureTimeMillis {
                val httpResponse: HttpResponse = client.post("https://router.huggingface.co/v1/chat/completions") {
                    header("Authorization", "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                if (!httpResponse.status.isSuccess()) {
                    val errorBody = httpResponse.bodyAsText()
                    throw Exception("HuggingFace API error for $modelId: ${httpResponse.status} - $errorBody")
                }

                val hfResponse: HuggingFaceResponse = httpResponse.body()
                response = hfResponse.choices.firstOrNull()?.message?.content
                    ?: throw Exception("No content in HuggingFace response for $modelId")

                // Use actual token count from API if available
                tokensGenerated = hfResponse.usage?.completionTokens ?: (response!!.length / 4)
            }

            val estimatedCost = (tokensGenerated / 1000.0) * info.costPer1kTokens

            ModelMetrics(
                modelName = modelId,
                modelSize = info.size,
                responseTimeMs = responseTimeMs,
                tokensGenerated = tokensGenerated,
                estimatedCost = estimatedCost,
                response = response!!,
                error = null
            )
        } catch (e: Exception) {
            val responseTimeMs = System.currentTimeMillis() - startTime
            println("Error with model $modelId: ${e.message}")

            ModelMetrics(
                modelName = modelId,
                modelSize = info.size,
                responseTimeMs = responseTimeMs,
                tokensGenerated = null,
                estimatedCost = null,
                response = "",
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Compare multiple models on the same prompt
     */
    suspend fun compareModels(
        prompt: String,
        modelIds: List<String>,
        temperature: Double = 0.7,
        maxNewTokens: Int = 150
    ): ModelComparisonResult {
        println("[HuggingFaceService] Starting comparison for ${modelIds.size} models")

        // Run all models in parallel
        val results = coroutineScope {
            modelIds.map { modelId ->
                async {
                    println("[HuggingFaceService] Starting request for $modelId")
                    val result = sendToModel(modelId, prompt, temperature, maxNewTokens)
                    println("[HuggingFaceService] Completed request for $modelId in ${result.responseTimeMs}ms")
                    result
                }
            }.map { it.await() }
        }

        return ModelComparisonResult(
            prompt = prompt,
            timestamp = System.currentTimeMillis(),
            results = results
        )
    }

    /**
     * Get list of available models
     */
    fun getAvailableModels(): List<String> {
        return modelInfo.keys.toList()
    }
}
