package sg.edumate.domain.usecase

import sg.edumate.domain.ai.LessonPlanDraft
import sg.edumate.domain.ai.LessonPlanRequest
import sg.edumate.domain.ai.ResourceRef
import sg.edumate.domain.ai.SafeAiGateway
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.AuditLogger
import sg.edumate.domain.repository.CurriculumRepository
import sg.edumate.domain.repository.LessonPlanRepository
import sg.edumate.domain.repository.ResourceRepository
import sg.edumate.domain.util.Clock
import sg.edumate.domain.util.IdGenerator
import sg.edumate.domain.util.Outcome
import sg.edumate.domain.util.map
import javax.inject.Inject

data class LessonPlanInput(
    val topicId: String,
    val streams: List<Stream>,
    val durationMin: Int = 60,
    val teacherNotes: String = "",
    val referenceResourceIds: List<String> = emptyList(),
    val tone: Tone = Tone.WARM,
)

class GenerateLessonPlanUseCase @Inject constructor(
    private val gateway: SafeAiGateway,
    private val curriculum: CurriculumRepository,
    private val resources: ResourceRepository,
    private val plans: LessonPlanRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val currentUser: CurrentUser,
) {
    suspend operator fun invoke(input: LessonPlanInput): Outcome<LessonPlan> {
        val topic = curriculum.topic(input.topicId) ?: return Outcome.Failed("Unknown topic")
        val subject = curriculum.subject(topic.subjectId)
        val refs = resources.byIds(input.referenceResourceIds).filterNot { it.isMock }.map { it.toRef() }
        val result = gateway.lessonPlan { requestId ->
            LessonPlanRequest(requestId, topic.level, subject?.name ?: topic.subjectId, topic, input.streams,
                input.durationMin, input.teacherNotes, refs, input.tone)
        }
        return result.map { ai ->
            ai.value.toPlan(ids.newId(), topic, input.streams, input.durationMin, currentUser.id, clock.nowMs(), ai.provenance)
                .also { plans.upsert(it) }
        }
    }
}

/** Regenerate/refine creates a new version linked to the previous one, preserving history. */
class RefineLessonPlanUseCase @Inject constructor(
    private val gateway: SafeAiGateway,
    private val curriculum: CurriculumRepository,
    private val plans: LessonPlanRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(planId: String, refinement: Refinement?, tone: Tone = Tone.WARM): Outcome<LessonPlan> {
        val plan = plans.get(planId) ?: return Outcome.Failed("Plan not found")
        val topic = curriculum.topic(plan.topicId) ?: return Outcome.Failed("Unknown topic")
        val subject = curriculum.subject(topic.subjectId)
        val refs = plan.provenance?.citations.orEmpty().map { ResourceRef(it.resourceId, it.title, "", it.source, it.licence) }
        val result = gateway.lessonPlan { requestId ->
            LessonPlanRequest(requestId, plan.level, subject?.name ?: plan.subjectId, topic, plan.streams, plan.durationMin,
                references = refs, tone = tone, refinement = refinement, previous = plan.toDraft())
        }
        return result.map { ai ->
            ai.value.toPlan(ids.newId(), topic, plan.streams, plan.durationMin, plan.ownerId, clock.nowMs(), ai.provenance).copy(
                version = plan.version + 1, parentPlanId = plan.id, tags = plan.tags, sharedWithDepartment = false,
            ).also { plans.upsert(it) }
        }
    }
}

class DuplicateLessonPlanUseCase @Inject constructor(
    private val plans: LessonPlanRepository, private val clock: Clock, private val ids: IdGenerator,
) {
    suspend operator fun invoke(planId: String): LessonPlan? = plans.get(planId)?.let {
        it.copy(id = ids.newId(), title = "${it.title} (copy)", version = 1, parentPlanId = it.id,
            sharedWithDepartment = false, updatedAtEpochMs = clock.nowMs(), syncState = SyncState.LOCAL_ONLY).also { c -> plans.upsert(c) }
    }
}

/** Teacher edits move AI drafts to TEACHER_EDITED; only explicit approval makes content shareable. */
class SaveLessonPlanEditsUseCase @Inject constructor(private val plans: LessonPlanRepository, private val clock: Clock) {
    suspend operator fun invoke(plan: LessonPlan) {
        val status = if (plan.reviewStatus == ReviewStatus.DRAFT_AI) ReviewStatus.TEACHER_EDITED else plan.reviewStatus
        plans.upsert(plan.copy(reviewStatus = status, updatedAtEpochMs = clock.nowMs(), syncState = SyncState.PENDING_UPLOAD))
    }
}

class SetReviewStatusUseCase @Inject constructor(
    private val plans: LessonPlanRepository, private val audit: AuditLogger, private val clock: Clock, private val ids: IdGenerator,
    private val currentUser: CurrentUser,
) {
    suspend operator fun invoke(planId: String, status: ReviewStatus) {
        val plan = plans.get(planId) ?: return
        plans.upsert(plan.copy(reviewStatus = status, updatedAtEpochMs = clock.nowMs(), syncState = SyncState.PENDING_UPLOAD))
        if (status == ReviewStatus.APPROVED)
            audit.log(AuditEntry(ids.newId(), clock.nowMs(), currentUser.role, AuditAction.CONTENT_APPROVED, "lesson_plan:$planId"))
    }
}

/** Session identity, provided by the auth layer. */
interface CurrentUser { val id: String; val role: Role; val schoolId: String }

internal fun Resource.toRef() = ResourceRef(id, title, summary, sourceName, licence.name)

internal fun LessonPlanDraft.toPlan(
    id: String, topic: SyllabusTopic, streams: List<Stream>, duration: Int, ownerId: String, now: Long, provenance: AiProvenance,
) = LessonPlan(
    id = id, title = title, level = topic.level, subjectId = topic.subjectId, topicId = topic.id, streams = streams,
    durationMin = duration, objectives = objectives, priorKnowledge = priorKnowledge, successCriteria = successCriteria,
    flow = flow, differentiation = differentiation, aflChecks = aflChecks, competencies = competencies, ownerId = ownerId,
    reviewStatus = ReviewStatus.DRAFT_AI, provenance = provenance, updatedAtEpochMs = now,
)

internal fun LessonPlan.toDraft() = LessonPlanDraft(title, objectives, priorKnowledge, successCriteria, flow, differentiation,
    aflChecks, competencies, provenance?.syllabusOutcomesCited.orEmpty(), provenance?.citations.orEmpty())
