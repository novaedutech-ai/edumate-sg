package sg.edumate.backend

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.OverloadedException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import sg.edumate.domain.ai.FeedbackRequest
import sg.edumate.domain.ai.LessonPlanRequest
import sg.edumate.domain.ai.WorksheetRequest
import kotlin.system.exitProcess

@Serializable
data class ApiError(val code: String, val message: String)

@Serializable
data class Health(val status: String, val model: String)

/** Same JSON settings as the Android app (encodeDefaults, explicitNulls=false). */
val WireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

private val UNAVAILABLE_FOR_LEGAL_REASONS = HttpStatusCode(451, "Content Blocked")

fun Application.edumateModule(service: GenerationService, model: String, devToken: String?) {
    val log = LoggerFactory.getLogger("EduMate")
    install(ContentNegotiation) { json(WireJson) }
    install(CallLogging) // logs method, path and status only - never request or response bodies
    install(StatusPages) {
        exception<PiiDetectedException> { call, e -> call.respond(HttpStatusCode.UnprocessableEntity, ApiError("pii_detected", e.message!!)) }
        exception<ContentBlockedException> { call, e -> call.respond(UNAVAILABLE_FOR_LEGAL_REASONS, ApiError("content_blocked", e.message!!)) }
        exception<ModelOutputException> { call, e -> call.respond(HttpStatusCode.BadGateway, ApiError("model_output", e.message!!)) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("invalid_request", "The app sent a request the server could not read. Update the app and backend together.")) }
        exception<IllegalArgumentException> { call, e -> call.respond(HttpStatusCode.BadRequest, ApiError("invalid_request", e.message ?: "Invalid request")) }
        exception<UnauthorizedException> { call, _ ->
            log.error("Claude API key rejected. Check ANTHROPIC_API_KEY in backend/secrets.properties.")
            call.respond(HttpStatusCode.BadGateway, ApiError("ai_auth", "The backend's Claude API key was rejected. Check backend/secrets.properties."))
        }
        exception<PermissionDeniedException> { call, e ->
            log.error("Claude API permission denied: {}", e.message)
            call.respond(HttpStatusCode.BadGateway, ApiError("ai_permission", "The Claude API account can't use this model or feature. See the backend window for details."))
        }
        exception<RateLimitException> { call, _ -> call.respond(HttpStatusCode.TooManyRequests, ApiError("rate_limited", "The AI service is busy. Wait a minute and try again.")) }
        exception<OverloadedException> { call, _ -> call.respond(HttpStatusCode.ServiceUnavailable, ApiError("overloaded", "The AI service is overloaded. Try again shortly.")) }
        exception<AnthropicServiceException> { call, e ->
            log.error("Claude API error {}: {}", e.statusCode(), e.message)
            call.respond(HttpStatusCode.BadGateway, ApiError("ai_error", "The AI service returned an error (${e.statusCode()}). See the backend window for details."))
        }
        exception<AnthropicIoException> { call, e ->
            log.error("Could not reach the Claude API: {}", e.message)
            call.respond(HttpStatusCode.BadGateway, ApiError("ai_unreachable", "The backend could not reach the AI service. Check the PC's internet connection."))
        }
    }

    fun ApplicationCall.authorised(): Boolean =
        devToken == null || request.headers["Authorization"] == "Bearer $devToken"

    routing {
        get("/health") { call.respond(Health("ok", model)) }

        route("/v1/ai") {
            post("/lesson-plans") {
                if (!call.authorised()) return@post call.respond(HttpStatusCode.Unauthorized, ApiError("unauthorised", "Missing or wrong dev token"))
                val req = call.receive<LessonPlanRequest>()
                call.respond(withContext(Dispatchers.IO) { service.lessonPlan(req) })
            }
            post("/worksheets") {
                if (!call.authorised()) return@post call.respond(HttpStatusCode.Unauthorized, ApiError("unauthorised", "Missing or wrong dev token"))
                val req = call.receive<WorksheetRequest>()
                call.respond(withContext(Dispatchers.IO) { service.worksheet(req) })
            }
            post("/feedback") {
                if (!call.authorised()) return@post call.respond(HttpStatusCode.Unauthorized, ApiError("unauthorised", "Missing or wrong dev token"))
                val req = call.receive<FeedbackRequest>()
                call.respond(withContext(Dispatchers.IO) { service.feedback(req) })
            }
        }
    }
}

fun main() {
    val config = try {
        Config.load()
    } catch (e: IllegalArgumentException) {
        System.err.println("\n*** EduMate backend could not start ***\n${e.message}\n")
        exitProcess(1)
    }
    val client = AnthropicOkHttpClient.builder().apiKey(config.apiKey).build()
    val service = GenerationService(ClaudeStructuredLlm(client, config.model))

    println(
        """

        ==============================================================
         EduMate SG backend is running
           Model:            ${config.model}
           Address (PC):     http://${config.host}:${config.port}/health
           Address (emulator): http://10.0.2.2:${config.port}/
         Leave this window open while you use the app. Ctrl+C to stop.
        ==============================================================
        """.trimIndent(),
    )
    embeddedServer(Netty, port = config.port, host = config.host) {
        edumateModule(service, config.model, config.devToken)
    }.start(wait = true)
}
