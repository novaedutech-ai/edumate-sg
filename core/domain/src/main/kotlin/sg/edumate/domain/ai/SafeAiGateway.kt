package sg.edumate.domain.ai

import kotlinx.coroutines.CancellationException
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.AuditLogger
import sg.edumate.domain.util.Clock
import sg.edumate.domain.util.IdGenerator
import sg.edumate.domain.util.Outcome

/** Supplies roster names currently known on-device, so they can be tokenised. */
fun interface RosterNameSource { suspend fun knownNames(): Collection<String> }

data class AiResult<T>(val value: T, val provenance: AiProvenance)

/**
 * The only entry point to AI generation. Enforces the AI behaviour rules:
 *  1. Tokenise identifiable data before the request leaves the device; restore after.
 *  2. Moderate every output string; block unsafe output.
 *  3. Drop any citation that does not refer to a resource the teacher actually supplied, and say so.
 *  4. Label output as AI-generated with provenance; the caller stores it as DRAFT_AI (teacher review required).
 *  5. Audit-log the call without content.
 */
class SafeAiGateway(
    private val provider: LlmProvider,
    private val moderator: ContentModerator,
    private val audit: AuditLogger,
    private val roster: RosterNameSource,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val actorRole: Role = Role.TEACHER,
) {
    suspend fun lessonPlan(build: (requestId: String) -> LessonPlanRequest): Outcome<AiResult<LessonPlanDraft>> =
        run("lesson_plan") { requestId, tok ->
            val raw = build(requestId)
            val notes = tok.tokenise(raw.teacherNotes)
            val req = raw.copy(teacherNotes = notes.text)
            val draft = provider.lessonPlan(req)
            val restored = draft.copy(
                objectives = draft.objectives.map { tok.detokenise(it, notes.vault) },
                flow = draft.flow.map { it.copy(instructions = tok.detokenise(it.instructions, notes.vault)) },
            )
            val (citations, removed) = validateCitations(restored.citations, raw.references)
            // Moderate the tokenised draft: the teacher's own restored notes shouldn't trip the PII check.
            Generated(restored.copy(citations = citations), notes.replacements, removed, restored.outcomesCited, citations,
                textsOf(draft))
        }

    suspend fun worksheet(build: (requestId: String) -> WorksheetRequest): Outcome<AiResult<WorksheetDraft>> =
        run("worksheet") { requestId, tok ->
            val raw = build(requestId)
            val notes = tok.tokenise(raw.spec.teacherNotes)
            val draft = provider.worksheet(raw.copy(spec = raw.spec.copy(teacherNotes = notes.text)))
            val (citations, removed) = validateCitations(draft.citations, raw.references)
            val texts = draft.versions.flatMap { v -> v.questions.flatMap { q -> listOf(q.prompt) + q.options + q.scaffolds.map { it.content } } } +
                draft.misconceptions.flatMap { listOf(it.description, it.remedy) }
            Generated(draft.copy(citations = citations), notes.replacements, removed, draft.outcomesCited, citations, texts)
        }

    suspend fun feedback(build: (requestId: String) -> FeedbackRequest): Outcome<AiResult<FeedbackDraft>> =
        run("feedback") { requestId, tok ->
            val raw = build(requestId)
            val response = tok.tokenise(raw.studentResponse)
            val draft = provider.feedback(raw.copy(studentResponse = response.text))
            val restored = draft.copy(feedback = tok.detokenise(draft.feedback, response.vault))
            // Moderate the tokenised text so a restored name can't trip the PII-in-output check.
            Generated(restored, response.replacements, 0, emptyList(), emptyList(), listOf(draft.feedback))
        }

    private class Generated<T>(
        val value: T, val piiReplaced: Int, val citationsRemoved: Int,
        val outcomes: List<String>, val citations: List<Citation>, val textsToModerate: List<String>,
    )

    private suspend fun <T> run(kind: String, block: suspend (String, PiiTokeniser) -> Generated<T>): Outcome<AiResult<T>> {
        val requestId = ids.newId()
        val tokeniser = PiiTokeniser(roster.knownNames())
        return try {
            val g = block(requestId, tokeniser)
            val flags = g.textsToModerate.map { moderator.review(it, ContentOrigin.AI_OUTPUT) }
            val blocked = flags.filterNot { it.allowed }
            log(AuditAction.AI_REQUEST, "$kind via ${provider.id}", requestId, g.piiReplaced)
            if (g.citationsRemoved > 0) log(AuditAction.AI_CITATION_REMOVED, "$kind: ${g.citationsRemoved} unverifiable citation(s) removed", requestId)
            if (blocked.isNotEmpty()) {
                log(AuditAction.AI_RESPONSE_BLOCKED, "$kind: ${blocked.flatMap { it.flags }.distinct().joinToString()}", requestId)
                return Outcome.Blocked("The AI response did not pass the safety check and was not shown. Try rephrasing or regenerating.")
            }
            Outcome.Ok(AiResult(g.value, AiProvenance(
                generatedBy = provider.id,
                generatedAtEpochMs = clock.nowMs(),
                requestId = requestId,
                syllabusOutcomesCited = g.outcomes,
                citations = g.citations,
                citationNote = if (g.citations.isEmpty()) NO_SOURCE_NOTE else null,
                moderationFlags = flags.flatMap { it.flags }.distinct(),
                isOfflineTemplate = provider.id == OfflineTemplateProvider.ID,
            )))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Failed("Couldn't generate right now. Your inputs are saved; try again when online.", e)
        }
    }

    private fun validateCitations(cited: List<Citation>, supplied: List<ResourceRef>): Pair<List<Citation>, Int> {
        val byId = supplied.associateBy { it.id }
        // Re-derive citation details from what we supplied so the model can't alter titles or licences.
        val valid = cited.mapNotNull { c -> byId[c.resourceId]?.let { Citation(it.id, it.title, it.source, it.licence) } }
            .distinctBy { it.resourceId }
        return valid to (cited.size - cited.count { it.resourceId in byId })
    }

    private fun textsOf(d: LessonPlanDraft) = listOf(d.title) + d.objectives + d.successCriteria + d.aflChecks +
        d.flow.flatMap { listOf(it.title, it.instructions) + it.options } + d.differentiation.map { it.description }

    private suspend fun log(action: AuditAction, detail: String, requestId: String, pii: Int = 0) =
        audit.log(AuditEntry(ids.newId(), clock.nowMs(), actorRole, action, detail, pii, requestId))

    companion object {
        const val NO_SOURCE_NOTE = "No external source was used. Generated from the selected syllabus outcomes only."
    }
}
