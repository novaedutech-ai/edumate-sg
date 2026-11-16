package sg.edumate.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import sg.edumate.app.data.settings.UserPreferencesRepository
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import sg.edumate.domain.ai.*
import sg.edumate.domain.model.LessonPlan
import sg.edumate.domain.model.Resource
import sg.edumate.domain.model.ResourceQuery

/** Mirrors docs/05-api/openapi.yaml. All endpoints are served from asia-southeast1. */
interface EduMateApi {
    @POST("v1/ai/lesson-plans") suspend fun generateLessonPlan(@Body body: LessonPlanRequest): LessonPlanDraft
    @POST("v1/ai/worksheets") suspend fun generateWorksheet(@Body body: WorksheetRequest): WorksheetDraft
    @POST("v1/ai/feedback") suspend fun suggestFeedback(@Body body: FeedbackRequest): FeedbackDraft
    @POST("v1/resources/search") suspend fun searchResources(@Body query: ResourceQuery): ResourceSearchResponse
    @PUT("v1/lesson-plans/{id}") suspend fun putLessonPlan(@Path("id") id: String, @Body plan: LessonPlan)
    @POST("v1/sessions") suspend fun createSession(@Body body: CreateSessionRequest): CreateSessionResponse
}

@Serializable data class ResourceSearchResponse(val items: List<Resource>, val nextPageToken: String? = null)
@Serializable data class CreateSessionRequest(val activityTitle: String, val classId: String?, val maxParticipants: Int = 45)
@Serializable data class CreateSessionResponse(val sessionId: String, val joinCode: String, val wsTicket: String, val expiresAtEpochMs: Long)

@Serializable private data class ApiErrorBody(val code: String = "", val message: String = "")

/**
 * Calls the EduMate backend; the backend owns model selection, prompts and vendor keys.
 * HTTP errors become [AiProviderException]s carrying the backend's teacher-readable message.
 */
class RemoteLlmProvider(private val api: EduMateApi) : LlmProvider {
    override val id = "edumate-backend-v1"
    override suspend fun lessonPlan(request: LessonPlanRequest) = call { api.generateLessonPlan(request) }
    override suspend fun worksheet(request: WorksheetRequest) = call { api.generateWorksheet(request) }
    override suspend fun feedback(request: FeedbackRequest) = call { api.suggestFeedback(request) }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        val message = runCatching {
            e.response()?.errorBody()?.string()?.let { errorJson.decodeFromString(ApiErrorBody.serializer(), it).message }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        throw AiProviderException(
            userMessage = message ?: "The AI service returned an error (${e.code()}). Please try again.",
            blocked = e.code() == 422 || e.code() == 451,
            cause = e,
        )
    }

    private companion object { val errorJson = Json { ignoreUnknownKeys = true } }
}

/**
 * Enforces the teacher's AI consent and the school's AI setting on every request: if either is off,
 * nothing is sent to the AI service and the on-device templates are used instead.
 */
class ConsentAwareLlmProvider(
    private val online: LlmProvider,
    private val offline: LlmProvider,
    private val prefs: UserPreferencesRepository,
) : LlmProvider {
    @Volatile private var lastUsed: String = online.id
    override val id: String get() = lastUsed

    private suspend fun pick(): LlmProvider =
        if (prefs.prefs.first().aiConsent && prefs.current().aiFeaturesEnabled) online else offline

    private suspend fun <T> run(block: suspend (LlmProvider) -> T): T {
        val provider = pick()
        return block(provider).also { lastUsed = provider.id }
    }

    override suspend fun lessonPlan(request: LessonPlanRequest) = run { it.lessonPlan(request) }
    override suspend fun worksheet(request: WorksheetRequest) = run { it.worksheet(request) }
    override suspend fun feedback(request: FeedbackRequest) = run { it.feedback(request) }
}

/**
 * Offline-first: tries the backend and falls back to on-device templates on network failure.
 * The gateway reads [id] after each call to label provenance correctly.
 */
class FallbackLlmProvider(private val primary: LlmProvider, private val fallback: LlmProvider) : LlmProvider {
    @Volatile private var lastUsed: String = primary.id
    override val id: String get() = lastUsed

    private suspend fun <T> attempt(block: suspend (LlmProvider) -> T): T = try {
        block(primary).also { lastUsed = primary.id }
    } catch (e: java.io.IOException) {
        block(fallback).also { lastUsed = fallback.id }
    }

    override suspend fun lessonPlan(request: LessonPlanRequest) = attempt { it.lessonPlan(request) }
    override suspend fun worksheet(request: WorksheetRequest) = attempt { it.worksheet(request) }
    override suspend fun feedback(request: FeedbackRequest) = attempt { it.feedback(request) }
}
