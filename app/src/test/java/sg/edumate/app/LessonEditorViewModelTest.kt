package sg.edumate.app

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sg.edumate.app.ui.plan.LessonEditorViewModel
import sg.edumate.domain.ai.LocalContentModerator
import sg.edumate.domain.ai.OfflineTemplateProvider
import sg.edumate.domain.ai.SafeAiGateway
import sg.edumate.domain.model.*
import sg.edumate.domain.usecase.*
import sg.edumate.domain.util.Outcome

@OptIn(ExperimentalCoroutinesApi::class)
class LessonEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val plans = MemPlans()
    private val clock = TestClock()
    private val ids = TestIds()
    private val audit = MemAudit()
    private val curriculum = MemCurriculum(listOf(TOPIC))
    private val gateway = SafeAiGateway(OfflineTemplateProvider(), LocalContentModerator(), audit, { emptyList() }, clock, ids)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun seed(): LessonPlan {
        val gen = GenerateLessonPlanUseCase(gateway, curriculum, FakeResourceRepo, plans, clock, ids, FakeUser)
        return (gen(LessonPlanInput(TOPIC.id, listOf(Stream.G1, Stream.G2, Stream.G3))) as Outcome.Ok).value
    }

    private fun vm(id: String) = LessonEditorViewModel(
        SavedStateHandle(mapOf("planId" to id)), plans, SaveLessonPlanEditsUseCase(plans, clock),
        RefineLessonPlanUseCase(gateway, curriculum, plans, clock, ids), SetReviewStatusUseCase(plans, audit, clock, ids, FakeUser),
    )

    @Test fun editsMarkDirtyAndSaveAsTeacherEdited() = runTest(dispatcher) {
        val p = seed(); val vm = vm(p.id)
        vm.title("Expressions with algebra tiles")
        assertTrue(vm.state.value.dirty)
        vm.saveEdits()
        assertEquals(ReviewStatus.TEACHER_EDITED, plans.get(p.id)!!.reviewStatus)
        assertEquals("Expressions with algebra tiles", plans.get(p.id)!!.title)
    }

    @Test fun reorderAndAddBlocks() = runTest(dispatcher) {
        val p = seed(); val vm = vm(p.id)
        val first = p.flow[0].id
        vm.moveBlock(first, 1)
        assertEquals(first, vm.state.value.plan!!.flow[1].id)
        vm.moveBlock(first, -5) // out of range: no-op
        assertEquals(first, vm.state.value.plan!!.flow[1].id)
        vm.addBlock(BlockType.EXIT_TICKET)
        assertTrue(vm.state.value.plan!!.flow.last().isCheckForUnderstanding)
    }

    @Test fun approveAuditsAndUnlocksSharing() = runTest(dispatcher) {
        val p = seed(); val vm = vm(p.id)
        vm.approve()
        assertTrue(plans.get(p.id)!!.reviewStatus.shareableWithStudents)
        assertTrue(audit.entries.any { it.action == AuditAction.CONTENT_APPROVED })
    }

    @Test fun refineProducesNewVersionAndNavigates() = runTest(dispatcher) {
        val p = seed(); val vm = vm(p.id)
        vm.refine(Refinement.MAKE_EASIER)
        val newId = vm.state.value.newVersionId
        assertNotNull(newId)
        assertEquals(2, plans.get(newId!!)!!.version)
        assertEquals(p.id, plans.get(newId)!!.parentPlanId)
    }
}

object FakeResourceRepo : sg.edumate.domain.repository.ResourceRepository {
    override suspend fun search(query: ResourceQuery) = emptyList<Resource>()
    override suspend fun byIds(ids: Collection<String>) = emptyList<Resource>()
    override fun observeBookmarkIds() = kotlinx.coroutines.flow.flowOf(emptySet<String>())
    override suspend fun toggleBookmark(resourceId: String) {}
    override suspend fun setOffline(resourceId: String, offline: Boolean) {}
}
