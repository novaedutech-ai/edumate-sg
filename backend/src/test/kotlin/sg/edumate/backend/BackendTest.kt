package sg.edumate.backend

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import sg.edumate.domain.ai.*
import sg.edumate.domain.model.*
import kotlin.test.*

class BackendTest {
    private val topic = SyllabusTopic("s1-ma-ae", "S1-MA-AE", "Algebraic expressions", "math", Level.SEC1, "Number and Algebra",
        listOf(LearningOutcome("S1-MA-AE.1", "Simplify linear expressions"), LearningOutcome("S1-MA-AE.2", "Evaluate expressions by substitution")))
    private val ref = ResourceRef("usr-1", "Algebra tiles slides", "Tiles demo", "My resources", "Teacher-owned")

    /** Fake model: returns the offline template output as JSON, optionally mutated, and records prompts. */
    private class FakeLlm(val mutate: (String) -> String = { it }) : StructuredLlm {
        val prompts = mutableListOf<String>()
        val schemas = mutableListOf<Map<String, Any?>>()
        override fun generate(effort: Effort, system: String, user: String, schema: Map<String, Any?>): String {
            prompts += user; schemas += schema
            val offline = OfflineTemplateProvider()
            val out = runBlocking {
                when {
                    user.startsWith("Write a lesson plan") -> WireJson.encodeToString(LessonPlanDraft.serializer(),
                        offline.lessonPlan(LessonPlanRequest("r", Level.SEC1, "Mathematics", TOPIC, listOf(Stream.G1, Stream.G3), 60)).copy(
                            citations = listOf(Citation("usr-1", "changed title", "x", "x"), Citation("made-up", "Fake book", "Nowhere", "?"))))
                    user.startsWith("Write one worksheet") -> WireJson.encodeToString(WorksheetDraft.serializer(),
                        offline.worksheet(WorksheetRequest("r", WorksheetSpec(TOPIC, emptyList(), 4, setOf(QuestionType.MCQ),
                            streams = listOf(Stream.G3, Stream.G1)), "Mathematics")))
                    else -> """{"feedback":"Good start. Check the signs.","suggestedMarks":1,"misconceptionTag":"sign error"}"""
                }
            }
            return mutate(out)
        }
        companion object { val TOPIC = SyllabusTopic("s1-ma-ae", "S1-MA-AE", "Algebraic expressions", "math", Level.SEC1, "Number and Algebra",
            listOf(LearningOutcome("S1-MA-AE.1", "Simplify linear expressions"))) }
    }

    private fun lessonReq(notes: String = "", refs: List<ResourceRef> = emptyList()) =
        LessonPlanRequest("r1", Level.SEC1, "Mathematics", topic, listOf(Stream.G1, Stream.G2, Stream.G3), 60, notes, refs)

    @Test fun `schema requires every field and forbids extras`() {
        val s = JsonSchemas.of(LessonPlanDraft.serializer().descriptor)
        assertEquals("object", s["type"]); assertEquals(false, s["additionalProperties"])
        @Suppress("UNCHECKED_CAST") val props = s["properties"] as Map<String, Map<String, Any?>>
        assertEquals(props.keys.toList(), s["required"])
        @Suppress("UNCHECKED_CAST") val block = (props.getValue("flow")["items"] as Map<String, Any?>)
        @Suppress("UNCHECKED_CAST") val tier = (block["properties"] as Map<String, Map<String, Any?>>).getValue("tier")
        assertTrue(tier.containsKey("anyOf"), "nullable enum becomes anyOf with null")
        @Suppress("UNCHECKED_CAST") val type = (block["properties"] as Map<String, Map<String, Any?>>).getValue("type")
        assertTrue((type["enum"] as List<*>).contains("EXIT_TICKET"))
    }

    @Test fun `worksheet schema leaves out app-computed checks`() {
        val schemaText = JsonSchemas.of(WorksheetDraft.serializer().descriptor, exclude = setOf("checks")).toString()
        assertFalse(schemaText.contains("checks"))
        assertTrue(schemaText.contains("answerKey"))
    }

    @Test fun `lesson plan keeps only supplied citations and valid outcome codes`() {
        val llm = FakeLlm()
        val draft = GenerationService(llm).lessonPlan(lessonReq(refs = listOf(ref)))
        assertEquals(listOf(Citation("usr-1", "Algebra tiles slides", "My resources", "Teacher-owned")), draft.citations)
        assertEquals(listOf("S1-MA-AE.1"), draft.outcomesCited)
        assertTrue(llm.prompts.single().contains("S1-MA-AE.2: Evaluate expressions by substitution"))
        assertTrue(llm.prompts.single().contains("id=usr-1"))
    }

    @Test fun `identifiers in notes are rejected before calling the model`() {
        val llm = FakeLlm()
        assertFailsWith<PiiDetectedException> { GenerationService(llm).lessonPlan(lessonReq(notes = "Parent S1234567D asked")) }
        assertTrue(llm.prompts.isEmpty())
    }

    @Test fun `unsafe output is blocked`() {
        val llm = FakeLlm { it.replace("Students will be able to", "this is shit") }
        assertFailsWith<ContentBlockedException> { GenerationService(llm).lessonPlan(lessonReq()) }
    }

    @Test fun `unparseable output becomes a clear error`() {
        assertFailsWith<ModelOutputException> { GenerationService(FakeLlm { "{not json" }).lessonPlan(lessonReq()) }
    }

    @Test fun `worksheet versions follow requested order with normalised ids`() {
        val llm = FakeLlm()
        val spec = WorksheetSpec(topic, emptyList(), 4, setOf(QuestionType.MCQ), streams = listOf(Stream.G1, Stream.G3))
        val ws = GenerationService(llm).worksheet(WorksheetRequest("r", spec, "Mathematics"))
        assertEquals(listOf(Stream.G1, Stream.G3), ws.versions.map { it.stream })
        assertEquals(listOf("q1", "q2", "q3", "q4", "challenge"), ws.versions[1].questions.map { it.id })
        assertTrue(ws.versions.all { v -> v.answerKey.map { it.questionId }.toSet() == v.questions.map { it.id }.toSet() })
        assertTrue(llm.prompts.single().contains("stream G1 (G1, SUPPORT tier): q1="))
    }

    @Test fun `version alignment fails loudly when streams are missing`() {
        val v = WorksheetVersion(Stream.G2, emptyList(), emptyList())
        assertFailsWith<ModelOutputException> { GenerationService.alignVersions(listOf(v), listOf(Stream.G1, Stream.G3)) }
    }

    @Test fun `http endpoints speak the app's JSON and map errors`() = testApplication {
        application { edumateModule(GenerationService(FakeLlm()), "test-model", devToken = null) }
        assertEquals(HttpStatusCode.OK, client.get("/health").status)

        val ok = client.post("/v1/ai/lesson-plans") {
            contentType(ContentType.Application.Json)
            setBody(WireJson.encodeToString(LessonPlanRequest.serializer(), lessonReq()))
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        val draft = WireJson.decodeFromString(LessonPlanDraft.serializer(), ok.bodyAsText())
        assertTrue(draft.flow.isNotEmpty())

        val pii = client.post("/v1/ai/lesson-plans") {
            contentType(ContentType.Application.Json)
            setBody(WireJson.encodeToString(LessonPlanRequest.serializer(), lessonReq(notes = "call 9123 4567")))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, pii.status)
        assertTrue(pii.bodyAsText().contains("pii_detected"))

        val bad = client.post("/v1/ai/worksheets") { contentType(ContentType.Application.Json); setBody("{}") }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
    }

    @Test fun `dev token is enforced when configured`() = testApplication {
        application { edumateModule(GenerationService(FakeLlm()), "m", devToken = "secret") }
        val body = WireJson.encodeToString(FeedbackRequest.serializer(),
            FeedbackRequest("r", "Simplify 2x + 3x", "6x", listOf("5x"), ScaffoldTier.CORE, Level.SEC1))
        assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/ai/feedback") { contentType(ContentType.Application.Json); setBody(body) }.status)
        val ok = client.post("/v1/ai/feedback") { contentType(ContentType.Application.Json); bearerAuth("secret"); setBody(body) }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertTrue(ok.bodyAsText().contains("sign error"))
    }
}
