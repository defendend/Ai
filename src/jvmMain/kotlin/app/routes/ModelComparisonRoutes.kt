package app.routes

import app.*
import app.services.HuggingFaceService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private fun ApplicationCall.getUserId(): Int? {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.getClaim("userId")?.asInt()
}

fun Route.modelComparisonRoutes() {
    route("/api/models") {
        authenticate("auth-jwt") {
            // Get available models
            get("/available") {
                try {
                    val models = HuggingFaceService.getAvailableModels()
                    call.respond(HttpStatusCode.OK, AvailableModelsResponse(models))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponseSimple("Failed to fetch models: ${e.message}")
                    )
                }
            }

            // Compare models
            post("/compare") {
                try {
                    val userId = call.getUserId()
                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponseSimple("User not authenticated"))
                        return@post
                    }

                    val request = call.receive<ModelComparisonRequest>()

                    // Validate request
                    if (request.prompt.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponseSimple("Prompt cannot be empty"))
                        return@post
                    }

                    if (request.models.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponseSimple("At least one model must be specified"))
                        return@post
                    }

                    // Validate models
                    val availableModels = HuggingFaceService.getAvailableModels()
                    val invalidModels = request.models.filter { it !in availableModels }
                    if (invalidModels.isNotEmpty()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponseSimple("Invalid models: ${invalidModels.joinToString(", ")}")
                        )
                        return@post
                    }

                    println("[ModelComparison] Starting comparison for user $userId with ${request.models.size} models")

                    // Perform comparison
                    val result = HuggingFaceService.compareModels(
                        prompt = request.prompt,
                        modelIds = request.models,
                        temperature = 0.7,
                        maxNewTokens = 150
                    )

                    println("[ModelComparison] Comparison completed successfully")
                    call.respond(HttpStatusCode.OK, result)
                } catch (e: Exception) {
                    println("[ModelComparison] Error: ${e.message}")
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponseSimple("Failed to compare models: ${e.message}")
                    )
                }
            }

            // Test single model
            post("/test/{modelId}") {
                try {
                    val userId = call.getUserId()
                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponseSimple("User not authenticated"))
                        return@post
                    }

                    val modelId = call.parameters["modelId"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponseSimple("Model ID is required"))

                    val availableModels = HuggingFaceService.getAvailableModels()
                    if (modelId !in availableModels) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponseSimple("Invalid model: $modelId")
                        )
                        return@post
                    }

                    val body = call.receive<TestModelRequest>()
                    if (body.prompt.isBlank()) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponseSimple("Prompt is required")
                        )
                    }

                    println("[ModelTest] Testing model $modelId for user $userId")

                    val result = HuggingFaceService.sendToModel(
                        modelId = modelId,
                        prompt = body.prompt,
                        temperature = 0.7,
                        maxNewTokens = 150
                    )

                    println("[ModelTest] Test completed for model $modelId")
                    call.respond(HttpStatusCode.OK, result)
                } catch (e: Exception) {
                    println("[ModelTest] Error: ${e.message}")
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponseSimple("Failed to test model: ${e.message}")
                    )
                }
            }
        }
    }
}
