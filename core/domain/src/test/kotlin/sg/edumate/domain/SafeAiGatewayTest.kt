package sg.edumate.domain

import kotlinx.coroutines.test.runTest
import sg.edumate.domain.ai.*
import sg.edumate.domain.model.*
import sg.edumate.domain.util.Outcome
import kotlin.test.*

class SafeAiGatewayTest {
    private val audit = RecordingAudit()

    private class CapturingProvider(val inner: LlmProvider = OfflineTemplateProvider(), val mutate: (LessonPlanDraft) -> LessonPlanDraft = { it }) : LlmProvider by inner {
        var lastLessonRequest: LessonPlanRequest? = null
        var lastFeedbackRequest: FeedbackRequest? = null
        override val id = "test"
        override suspend fun lessonPlan(request: LessonPlanRequest) = mutate(inner.lessonPlan(request).also { lastLessonRequest = request })
        override suspend fun feedback(request: FeedbackRequest): FeedbackDraft { lastFeedbackRequest = request
            return FeedbackDraft("Good try ${Regex("\\[PERSON_\\d+]").find(request.studentResponse)?.value ?: ""}", 1, null) }
    }

    private fun gateway(p: LlmProvider) = SafeAiGateway(p, LocalContentModerator(), audit, { listOf("Tan Wei Ming") }, FixedClock(), SeqIds())

    private fun req(id: String, notes: String = "", refs: List<ResourceRef> = emptyList()) =
        LessonPlanRequest(id, Level.P4, "Mathematics", FRACTIONS, listOf(Stream.PRI_FOUNDATION, Stream.PRI_STANDARD), 60, notes, refs)

    @Test fun `roster names never reach the provider and are restored`() = runTest {
        val p = CapturingProvider()
        gateway(p).feedback { FeedbackRequest(it, "Q", "Tan Wei Ming thinks 1/2 + 1/4 = 2/6", listOf("3/4"), ScaffoldTier.SUPPORT, Level.P4)
        }.let { r ->
            assertFalse(p.lastFeedbackRequest!!.studentResponse.contains("Wei"))
            assertEquals("Good try Tan Wei Ming", (r as Outcome.Ok).value.value.feedback)
        }
        assertEquals(AuditAction.AI_REQUEST, audit.entries.single().action)
        assertTrue(audit.entries.single().piiTokensReplaced >= 1)
    }

    @Test fun `fabricated citations are removed and noted`() = runTest {
        val real = ResourceRef("r1", "Fractions slides", "", "Dept", "CC BY 4.0")
        val p = CapturingProvider(mutate = { it.copy(citations = listOf(Citation("r1", "altered title", "x", "x"), Citation("fake", "Made up", "Nowhere", "?"))) })
        val ok = gateway(p).lessonPlan { req(it, refs = listOf(real)) } as Outcome.Ok
        assertEquals(listOf(Citation("r1", "Fractions slides", "Dept", "CC BY 4.0")), ok.value.provenance.citations)
        assertTrue(audit.entries.any { it.action == AuditAction.AI_CITATION_REMOVED })
    }

    @Test fun `no citations yields explicit no-source note`() = runTest {
        val ok = gateway(CapturingProvider()).lessonPlan { req(it) } as Outcome.Ok
        assertEquals(SafeAiGateway.NO_SOURCE_NOTE, ok.value.provenance.citationNote)
        assertEquals(listOf("P4-MA-NUM-03.1", "P4-MA-NUM-03.2"), ok.value.provenance.syllabusOutcomesCited)
    }

    @Test fun `unsafe output is blocked`() = runTest {
        val p = CapturingProvider(mutate = { it.copy(objectives = it.objectives + "this is shit") })
        assertIs<Outcome.Blocked>(gateway(p).lessonPlan { req(it) })
        assertTrue(audit.entries.any { it.action == AuditAction.AI_RESPONSE_BLOCKED })
    }

    @Test fun `structured PII in teacher notes is tokenised`() = runTest {
        val p = CapturingProvider()
        gateway(p).lessonPlan { req(it, notes = "Parent of S1234567D asked for more practice") }
        assertEquals("Parent of [NRIC_1] asked for more practice", p.lastLessonRequest!!.teacherNotes)
    }

    @Test fun `provider failure becomes Failed not crash`() = runTest {
        val boom = object : LlmProvider by OfflineTemplateProvider() {
            override suspend fun lessonPlan(request: LessonPlanRequest): LessonPlanDraft = throw java.io.IOException("offline")
        }
        assertIs<Outcome.Failed>(gateway(boom).lessonPlan { req(it) })
    }
}
