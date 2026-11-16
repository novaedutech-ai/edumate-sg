package sg.edumate.domain.ai

import kotlinx.serialization.Serializable
import sg.edumate.domain.model.*

/**
 * Abstraction over the AI backend. Implementations call EduMate's own backend (which holds the
 * model API keys and the prompt templates) and never talk to a model vendor directly.
 *
 * Callers must NOT use a provider directly: go through [SafeAiGateway], which tokenises PII,
 * moderates output, validates citations and writes the audit log.
 */
interface LlmProvider {
    val id: String
    suspend fun lessonPlan(request: LessonPlanRequest): LessonPlanDraft
    suspend fun worksheet(request: WorksheetRequest): WorksheetDraft
    suspend fun feedback(request: FeedbackRequest): FeedbackDraft
}

/** Minimal resource info sent to the AI. The resource must have been selected by the teacher. */
@Serializable
data class ResourceRef(val id: String, val title: String, val summary: String, val source: String, val licence: String)

@Serializable
data class LessonPlanRequest(
    val requestId: String,
    val level: Level,
    val subjectName: String,
    val topic: SyllabusTopic,
    val streams: List<Stream>,
    val durationMin: Int,
    val teacherNotes: String = "",
    val references: List<ResourceRef> = emptyList(),
    val tone: Tone = Tone.WARM,
    val refinement: Refinement? = null,
    val previous: LessonPlanDraft? = null,
)

@Serializable
data class LessonPlanDraft(
    val title: String,
    val objectives: List<String>,
    val priorKnowledge: List<String>,
    val successCriteria: List<String>,
    val flow: List<LessonBlock>,
    val differentiation: List<DifferentiatedActivity>,
    val aflChecks: List<String>,
    val competencies: List<Competency21>,
    val outcomesCited: List<String>,
    val citations: List<Citation> = emptyList(),
)

@Serializable
data class WorksheetRequest(
    val requestId: String,
    val spec: WorksheetSpec,
    val subjectName: String,
    val references: List<ResourceRef> = emptyList(),
    val tone: Tone = Tone.NEUTRAL,
    val refinement: Refinement? = null,
)

@Serializable
data class WorksheetDraft(
    val title: String,
    val versions: List<WorksheetVersion>,
    val misconceptions: List<Misconception>,
    val outcomesCited: List<String>,
    val citations: List<Citation> = emptyList(),
)

@Serializable
data class FeedbackRequest(
    val requestId: String,
    val questionPrompt: String,
    val studentResponse: String,
    val markingPoints: List<String>,
    val tier: ScaffoldTier,
    val level: Level,
)

@Serializable
data class FeedbackDraft(val feedback: String, val suggestedMarks: Int?, val misconceptionTag: String?)
