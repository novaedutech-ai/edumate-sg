package sg.edumate.domain

import kotlinx.coroutines.test.runTest
import sg.edumate.domain.ai.*
import sg.edumate.domain.model.*
import sg.edumate.domain.quality.WorksheetQualityChecker
import sg.edumate.domain.usecase.*
import sg.edumate.domain.util.Outcome
import kotlin.test.*

class UseCaseTest {
    private val audit = RecordingAudit()
    private val clock = FixedClock()
    private val ids = SeqIds()
    private val gateway = SafeAiGateway(OfflineTemplateProvider(), LocalContentModerator(), audit, { emptyList() }, clock, ids)
    private val plans = FakePlans()

    @Test fun `generated plan is stored as AI draft and not shareable`() = runTest {
        val uc = GenerateLessonPlanUseCase(gateway, FakeCurriculum(), FakeResources(), plans, clock, ids, TestUser)
        val plan = (uc(LessonPlanInput("t-frac", Stream.forBand(LevelBand.PRIMARY), 60)) as Outcome.Ok).value
        assertEquals(ReviewStatus.DRAFT_AI, plan.reviewStatus)
        assertFalse(plan.reviewStatus.shareableWithStudents)
        assertTrue(plan.provenance!!.isOfflineTemplate.not() || plan.provenance!!.generatedBy == OfflineTemplateProvider.ID)
        assertEquals(plan, plans.get(plan.id))
        assertTrue(plan.flow.any { it.isCheckForUnderstanding })
    }

    @Test fun `refine creates linked new version`() = runTest {
        val gen = GenerateLessonPlanUseCase(gateway, FakeCurriculum(), FakeResources(), plans, clock, ids, TestUser)
        val v1 = (gen(LessonPlanInput("t-alg", listOf(Stream.G1, Stream.G2, Stream.G3))) as Outcome.Ok).value
        val v2 = (RefineLessonPlanUseCase(gateway, FakeCurriculum(), plans, clock, ids)(v1.id, Refinement.ADD_EXTENSION) as Outcome.Ok).value
        assertEquals(2, v2.version)
        assertEquals(v1.id, v2.parentPlanId)
        assertTrue(v2.flow.any { it.type == BlockType.GROUP_TASK })
        assertNotNull(plans.get(v1.id), "previous version is preserved")
    }

    @Test fun `editing an AI draft marks it teacher edited`() = runTest {
        val gen = GenerateLessonPlanUseCase(gateway, FakeCurriculum(), FakeResources(), plans, clock, ids, TestUser)
        val p = (gen(LessonPlanInput("t-frac", listOf(Stream.PRI_STANDARD))) as Outcome.Ok).value
        SaveLessonPlanEditsUseCase(plans, clock)(p.copy(title = "My title"))
        assertEquals(ReviewStatus.TEACHER_EDITED, plans.get(p.id)!!.reviewStatus)
    }

    @Test fun `worksheet has three coordinated versions with tier-appropriate scaffolds`() = runTest {
        val repo = FakeWorksheets()
        val uc = GenerateWorksheetUseCase(gateway, FakeCurriculum(), FakeResources(), repo, WorksheetQualityChecker(), clock, ids, TestUser)
        val spec = WorksheetSpec(ALGEBRA, listOf("Simplify expressions"), 6, setOf(QuestionType.MCQ, QuestionType.STRUCTURED),
            streams = listOf(Stream.G3, Stream.G1, Stream.G2))
        val ws = (uc(spec) as Outcome.Ok).value
        assertEquals(listOf(Stream.G1, Stream.G2, Stream.G3), ws.versions.map { it.stream })
        val (g1, g2, g3) = ws.versions
        assertTrue(g1.questions.all { it.scaffolds.isNotEmpty() })
        assertTrue(g3.questions.any { it.isChallenge })
        assertEquals(g1.questions.map { it.outcomeCode }, g2.questions.map { it.outcomeCode })
        assertTrue(ws.versions.all { v -> v.checks.none { it.name == "Versions coordinated" } })
        assertTrue(g1.questions.count { it.difficulty == Difficulty.FOUNDATIONAL } >= g3.questions.count { it.difficulty == Difficulty.FOUNDATIONAL })
        assertTrue(ws.versions.all { v -> v.answerKey.size == v.questions.size })
    }

    @Test fun `difficulty mix allocation sums to n`() {
        listOf(1, 5, 7, 10, 13).forEach { n ->
            ScaffoldTier.entries.forEach { t -> assertEquals(n, DifficultyMix.BALANCED.forTier(t).allocate(n).size) }
        }
        assertEquals(100, DifficultyMix.BALANCED.forTier(ScaffoldTier.SUPPORT).let { it.foundational + it.intermediate + it.advanced })
    }

    @Test fun `roster import pseudonymises and rejects identifier columns`() {
        val csv = "name,stream,nric\n\"Tan, Wei Ming\",G2,S1234567D\nNur Aisyah,G3,T0123456A\n,G1,\n"
        val r = RosterCsvImporter(SeqIds()).parse(csv, "c1", keepRealNames = false)
        assertEquals(2, r.students.size)
        assertEquals(1, r.skippedRows)
        assertEquals(listOf("nric"), r.rejectedColumns)
        assertTrue(r.students.all { it.realName == null && it.pseudonymId.startsWith("STU-") })
        assertEquals(Stream.G2, r.students.first().stream)
        assertEquals("Tan, Wei Ming", RosterCsvImporter(SeqIds()).parse(csv, "c1", true).students.first().realName)
    }
}
