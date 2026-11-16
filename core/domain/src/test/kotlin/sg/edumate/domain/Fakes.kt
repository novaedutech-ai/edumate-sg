package sg.edumate.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.*
import sg.edumate.domain.usecase.CurrentUser
import sg.edumate.domain.util.Clock
import sg.edumate.domain.util.IdGenerator

class FixedClock(var now: Long = 1_700_000_000_000L) : Clock { override fun nowMs() = now }
class SeqIds : IdGenerator { private var n = 0; override fun newId() = "id${++n}" }

class RecordingAudit : AuditLogger { val entries = mutableListOf<AuditEntry>(); override suspend fun log(entry: AuditEntry) { entries += entry } }

object TestUser : CurrentUser { override val id = "teacher-1"; override val role = Role.TEACHER; override val schoolId = "school-1" }

val FRACTIONS = SyllabusTopic(
    id = "t-frac", code = "P4-MA-NUM-03", title = "Fractions", subjectId = "math", level = Level.P4, strand = "Number and Algebra",
    outcomes = listOf(LearningOutcome("P4-MA-NUM-03.1", "Add and subtract related fractions"),
        LearningOutcome("P4-MA-NUM-03.2", "Find a fraction of a set")),
)
val ALGEBRA = FRACTIONS.copy(id = "t-alg", code = "S1-MA-ALG-01", title = "Algebraic expressions", level = Level.SEC1)

class FakeCurriculum : CurriculumRepository {
    private val topics = listOf(FRACTIONS, ALGEBRA)
    override fun subjects(band: LevelBand) = listOf(Subject("math", "Mathematics", LevelBand.entries.toSet()))
    override fun topics(level: Level, subjectId: String) = topics.filter { it.level == level }
    override fun topic(id: String) = topics.firstOrNull { it.id == id }
    override fun subject(id: String) = subjects(LevelBand.PRIMARY).firstOrNull { it.id == id }
}

class FakeResources(private val all: List<Resource> = emptyList()) : ResourceRepository {
    override suspend fun search(query: ResourceQuery) = all.filter(query::matches)
    override suspend fun byIds(ids: Collection<String>) = all.filter { it.id in ids }
    override fun observeBookmarkIds(): Flow<Set<String>> = MutableStateFlow(emptySet())
    override suspend fun toggleBookmark(resourceId: String) {}
    override suspend fun setOffline(resourceId: String, offline: Boolean) {}
}

class FakePlans : LessonPlanRepository {
    val state = MutableStateFlow<Map<String, LessonPlan>>(emptyMap())
    override fun observeAll() = state.map { it.values.toList() }
    override fun observe(id: String) = state.map { it[id] }
    override suspend fun get(id: String) = state.value[id]
    override suspend fun upsert(plan: LessonPlan) { state.value = state.value + (plan.id to plan) }
    override suspend fun delete(id: String) { state.value = state.value - id }
    override suspend fun purgeUnreviewedDraftsOlderThan(epochMs: Long) = 0
}

class FakeWorksheets : WorksheetRepository {
    val items = mutableMapOf<String, Worksheet>()
    override fun observeAll(): Flow<List<Worksheet>> = MutableStateFlow(items.values.toList())
    override suspend fun get(id: String) = items[id]
    override suspend fun upsert(worksheet: Worksheet) { items[worksheet.id] = worksheet }
    override suspend fun delete(id: String) { items.remove(id) }
}
