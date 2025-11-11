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

    // Model information
    private val modelInfo = mapOf(
        "distilbert/distilgpt2" to ModelInfo("82M", 0.0), // Free
        "bigscience/bloom-560m" to ModelInfo("560M", 0.0), // Free
        "bigscience/bloom-1b7" to ModelInfo("1.7B", 0.0), // Free
        "mistralai/Mistral-7B-Instruct-v0.1" to ModelInfo("7B", 0.0) // Free via Inference API
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
                inputs = prompt,
                parameters = HuggingFaceParameters(
                    temperature = temperature,
                    maxNewTokens = maxNewTokens,
                    topP = 0.9,
                    doSample = true,
                    returnFullText = false
                ),
                options = HuggingFaceOptions(
                    useCache = false,
                    waitForModel = true
                )
            )

            var response: String? = null
            val responseTimeMs = measureTimeMillis {
                val httpResponse: HttpResponse = client.post("https://api-inference.huggingface.co/models/$modelId") {
                    header("Authorization", "Bearer $apiKey")
                    header("x-use-cache", "false")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                if (!httpResponse.status.isSuccess()) {
                    val errorBody = httpResponse.bodyAsText()
                    throw Exception("HuggingFace API error for $modelId: ${httpResponse.status} - $errorBody")
                }

                val responseList: List<HuggingFaceResponse> = httpResponse.body()
                response = responseList.firstOrNull()?.generatedText
                    ?: throw Exception("No content in HuggingFace response for $modelId")
            }

            // Estimate tokens (rough approximation: ~4 chars per token)
            val tokensGenerated = response!!.length / 4
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
