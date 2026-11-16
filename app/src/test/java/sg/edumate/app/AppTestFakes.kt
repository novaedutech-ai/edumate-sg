package sg.edumate.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.*
import sg.edumate.domain.usecase.CurrentUser
import sg.edumate.domain.util.Clock
import sg.edumate.domain.util.IdGenerator

object FakeUser : CurrentUser { override val id = "t1"; override val role = Role.TEACHER; override val schoolId = "s1" }
class TestClock(var now: Long = 1_700_000_000_000L) : Clock { override fun nowMs() = now }
class TestIds : IdGenerator { private var n = 0; override fun newId() = "id${++n}" }
class MemAudit : AuditLogger { val entries = mutableListOf<AuditEntry>(); override suspend fun log(entry: AuditEntry) { entries += entry } }

class MemPlans : LessonPlanRepository {
    val state = MutableStateFlow<Map<String, LessonPlan>>(emptyMap())
    override fun observeAll() = state.map { it.values.toList() }
    override fun observe(id: String) = state.map { it[id] }
    override suspend fun get(id: String) = state.value[id]
    override suspend fun upsert(plan: LessonPlan) { state.value = state.value + (plan.id to plan) }
    override suspend fun delete(id: String) { state.value = state.value - id }
    override suspend fun purgeUnreviewedDraftsOlderThan(epochMs: Long) = 0
}

class MemCurriculum(private val topics: List<SyllabusTopic>) : CurriculumRepository {
    override fun subjects(band: LevelBand) = listOf(Subject("math", "Mathematics", LevelBand.entries.toSet()))
    override fun topics(level: Level, subjectId: String) = topics.filter { it.level == level }
    override fun topic(id: String) = topics.firstOrNull { it.id == id }
    override fun subject(id: String) = subjects(LevelBand.SECONDARY).firstOrNull { it.id == id }
}

val TOPIC = SyllabusTopic("s1-ma-ae", "S1-MA-AE", "Algebraic expressions", "math", Level.SEC1, "Number and Algebra",
    listOf(LearningOutcome("S1-MA-AE.1", "Simplify linear expressions")))
