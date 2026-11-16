package sg.edumate.backend

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import sg.edumate.domain.ai.*
import sg.edumate.domain.model.*
import org.slf4j.LoggerFactory

class PiiDetectedException(message: String) : RuntimeException(message)
class ContentBlockedException(message: String) : RuntimeException(message)
class ModelOutputException(message: String) : RuntimeException(message)

/**
 * Server side of the AI contract (docs/05-api/openapi.yaml). Defence in depth on top of the app's
 * SafeAiGateway: re-checks for identifiers, moderates output, keeps only citations of resources the
 * teacher supplied, and normalises ids so worksheet versions stay coordinated.
 */
class GenerationService(private val llm: StructuredLlm) {
    private val log = LoggerFactory.getLogger("Generation")
    private val pii = PiiTokeniser()
    private val moderator = LocalContentModerator()
    private val modelJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val wireJson = Json { encodeDefaults = true }

    private val lessonSchema = JsonSchemas.of(LessonPlanDraft.serializer().descriptor)
    // Quality checks are computed by the app, not the model.
    private val worksheetSchema = JsonSchemas.of(WorksheetDraft.serializer().descriptor, exclude = setOf("checks"))
    private val feedbackSchema = JsonSchemas.of(FeedbackDraft.serializer().descriptor)

    fun lessonPlan(req: LessonPlanRequest): LessonPlanDraft {
        requireNoPii(req.teacherNotes, *req.references.map { it.summary }.toTypedArray())
        require(req.streams.isNotEmpty()) { "Choose at least one stream" }
        val previous = req.previous?.let { wireJson.encodeToString(LessonPlanDraft.serializer(), it) }
        val draft = decode(
            llm.generate(Effort.MEDIUM, Prompts.SYSTEM, Prompts.lessonPlan(req, previous), lessonSchema),
            LessonPlanDraft.serializer(),
        )
        val codes = req.topic.outcomes.map { it.code }.toSet()
        val cleaned = draft.copy(
            flow = uniqueBlockIds(draft.flow),
            citations = keepSuppliedCitations(draft.citations, req.references),
            outcomesCited = draft.outcomesCited.filter { it in codes }.ifEmpty { codes.toList() },
        )
        moderate(listOf(cleaned.title) + cleaned.objectives + cleaned.successCriteria + cleaned.priorKnowledge + cleaned.aflChecks +
            cleaned.flow.flatMap { listOf(it.title, it.instructions) + it.options } + cleaned.differentiation.map { it.description })
        return cleaned
    }

    fun worksheet(req: WorksheetRequest): WorksheetDraft {
        requireNoPii(req.spec.teacherNotes, *req.references.map { it.summary }.toTypedArray())
        // Worksheets need correct answer keys, so think harder.
        val draft = decode(
            llm.generate(Effort.HIGH, Prompts.SYSTEM, Prompts.worksheet(req), worksheetSchema),
            WorksheetDraft.serializer(),
        )
        val codes = req.spec.topic.outcomes.map { it.code }.toSet()
        val versions = alignVersions(draft.versions, req.spec.streams).map(::normaliseIds)
        val cleaned = draft.copy(
            versions = versions,
            citations = keepSuppliedCitations(draft.citations, req.references),
            outcomesCited = draft.outcomesCited.filter { it in codes }.ifEmpty { codes.toList() },
        )
        moderate(listOf(cleaned.title) + cleaned.misconceptions.flatMap { listOf(it.description, it.remedy) } +
            versions.flatMap { v ->
                v.questions.flatMap { q -> listOf(q.prompt) + q.options + q.scaffolds.map { it.content } } +
                    v.answerKey.flatMap { listOf(it.answer) + it.markingPoints }
            })
        return cleaned
    }

    fun feedback(req: FeedbackRequest): FeedbackDraft {
        requireNoPii(req.studentResponse, req.questionPrompt)
        val draft = decode(llm.generate(Effort.LOW, Prompts.SYSTEM, Prompts.feedback(req), feedbackSchema), FeedbackDraft.serializer())
        moderate(listOf(draft.feedback))
        return draft
    }

    private fun requireNoPii(vararg texts: String) {
        if (texts.any { pii.containsStructuredPii(it) }) {
            throw PiiDetectedException("Your notes contain an NRIC/FIN, email address or phone number. Remove it and try again.")
        }
    }

    private fun moderate(texts: List<String>) {
        val flags = texts.map { moderator.review(it, ContentOrigin.AI_OUTPUT) }.filterNot { it.allowed }
        if (flags.isNotEmpty()) {
            log.warn("output blocked by moderation: {}", flags.flatMap { it.flags }.distinct())
            throw ContentBlockedException("The AI response did not pass the safety check and was not returned. Try regenerating.")
        }
    }

    private fun <T> decode(raw: String, strategy: DeserializationStrategy<T>): T = try {
        modelJson.decodeFromString(strategy, raw)
    } catch (e: SerializationException) {
        log.warn("could not parse model output: {}", e.message)
        throw ModelOutputException("The AI returned an unexpected format. Please try again.")
    } catch (e: IllegalArgumentException) {
        log.warn("invalid model output: {}", e.message)
        throw ModelOutputException("The AI returned an unexpected format. Please try again.")
    }

    companion object {
        /** Rebuild citations from the supplied references so titles/licences can't be altered or invented. */
        fun keepSuppliedCitations(cited: List<Citation>, supplied: List<ResourceRef>): List<Citation> {
            val byId = supplied.associateBy { it.id }
            return cited.mapNotNull { c -> byId[c.resourceId]?.let { Citation(it.id, it.title, it.source, it.licence) } }
                .distinctBy { it.resourceId }
        }

        fun uniqueBlockIds(flow: List<LessonBlock>): List<LessonBlock> {
            val seen = mutableSetOf<String>()
            return flow.mapIndexed { i, b ->
                val base = b.id.trim().ifEmpty { "block-${i + 1}" }
                val id = if (seen.add(base)) base else "$base-${i + 1}".also { seen.add(it) }
                b.copy(id = id, durationMin = b.durationMin.coerceIn(1, 180))
            }
        }

        /** Return exactly one version per requested stream, in the requested order. */
        fun alignVersions(versions: List<WorksheetVersion>, streams: List<Stream>): List<WorksheetVersion> {
            val byStream = versions.associateBy { it.stream }
            if (streams.all { it in byStream }) return streams.map { byStream.getValue(it) }
            if (versions.size == streams.size) return versions.zip(streams) { v, s -> v.copy(stream = s) }
            throw ModelOutputException("The AI returned ${versions.size} version(s) for ${streams.size} stream(s). Please try again.")
        }

        /** ids q1..qN for core questions, "challenge" for the extension item; answer keys follow the renaming. */
        fun normaliseIds(v: WorksheetVersion): WorksheetVersion {
            val core = v.questions.filterNot { it.isChallenge }
            val challenge = v.questions.filter { it.isChallenge }.take(1)
            val rename = mutableMapOf<String, String>()
            val questions = core.mapIndexed { i, q -> q.copy(id = "q${i + 1}", number = i + 1).also { rename[q.id] = it.id } } +
                challenge.map { q -> q.copy(id = "challenge", number = core.size + 1).also { rename[q.id] = "challenge" } }
            val keys = v.answerKey.mapNotNull { k -> rename[k.questionId]?.let { k.copy(questionId = it) } }.distinctBy { it.questionId }
            return v.copy(questions = questions, answerKey = keys, checks = emptyList())
        }
    }
}
