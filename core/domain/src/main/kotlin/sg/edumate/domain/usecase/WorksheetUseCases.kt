package sg.edumate.domain.usecase

import sg.edumate.domain.ai.SafeAiGateway
import sg.edumate.domain.ai.WorksheetRequest
import sg.edumate.domain.model.*
import sg.edumate.domain.quality.WorksheetQualityChecker
import sg.edumate.domain.repository.CurriculumRepository
import sg.edumate.domain.repository.ResourceRepository
import sg.edumate.domain.repository.WorksheetRepository
import sg.edumate.domain.util.Clock
import sg.edumate.domain.util.IdGenerator
import sg.edumate.domain.util.Outcome
import javax.inject.Inject

class GenerateWorksheetUseCase @Inject constructor(
    private val gateway: SafeAiGateway,
    private val curriculum: CurriculumRepository,
    private val resources: ResourceRepository,
    private val worksheets: WorksheetRepository,
    private val quality: WorksheetQualityChecker,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val currentUser: CurrentUser,
) {
    suspend operator fun invoke(spec: WorksheetSpec, refinement: Refinement? = null): Outcome<Worksheet> {
        val subject = curriculum.subject(spec.topic.subjectId)
        val refs = resources.byIds(spec.referenceResourceIds).filterNot { it.isMock }.map { it.toRef() }
        // Always generate in tier order so the UI tabs read Support → Core → Extension.
        val ordered = spec.copy(streams = spec.streams.distinct().sortedBy { it.tier.ordinal })
        val result = gateway.worksheet { requestId -> WorksheetRequest(requestId, ordered, subject?.name ?: spec.topic.subjectId, refs, refinement = refinement) }
        return when (result) {
            is Outcome.Blocked -> result
            is Outcome.Failed -> result
            is Outcome.Ok -> {
                val draft = result.value.value
                val coordination = quality.coordinationIssues(draft.versions)
                val versions = draft.versions.map { v ->
                    v.copy(checks = quality.check(v, spec.topic.level) + coordination.map { QualityCheck("Versions coordinated", false, it) })
                }
                val ws = Worksheet(
                    id = ids.newId(), title = draft.title, topicId = spec.topic.id, level = spec.topic.level, subjectId = spec.topic.subjectId,
                    objectives = spec.objectives, versions = versions, misconceptions = draft.misconceptions, ownerId = currentUser.id,
                    reviewStatus = ReviewStatus.DRAFT_AI, provenance = result.value.provenance, updatedAtEpochMs = clock.nowMs(),
                )
                worksheets.upsert(ws)
                Outcome.Ok(ws)
            }
        }
    }
}
