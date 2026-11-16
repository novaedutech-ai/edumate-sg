package sg.edumate.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.Json
import sg.edumate.app.data.local.*
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.ClassRepository
import sg.edumate.domain.repository.ScheduleRepository
import sg.edumate.domain.util.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassRepositoryImpl @Inject constructor(
    private val dao: ClassDao, private val json: Json, private val clock: Clock,
) : ClassRepository, ScheduleRepository {

    override fun observeClasses(): Flow<List<SchoolClass>> = dao.observeClasses()
        .onStart { seedIfEmpty() }
        .map { list -> list.map { json.decodeFromString(SchoolClass.serializer(), it.json) }.sortedBy { it.name } }

    override fun observeStudents(classId: String): Flow<List<StudentRef>> = dao.observeStudents(classId).map { list ->
        list.map { StudentRef(it.pseudonymId, it.classId, it.alias, it.realName, it.stream?.let(Stream::valueOf)) }
    }

    override fun observeAssignments(): Flow<List<Assignment>> = dao.observeAssignments()
        .onStart { seedIfEmpty() }
        .map { list -> list.map { json.decodeFromString(Assignment.serializer(), it.json) } }

    override fun observeAnnouncements(classId: String): Flow<List<Announcement>> = dao.observeAnnouncements(classId)
        .map { list -> list.map { json.decodeFromString(Announcement.serializer(), it.json) } }

    override suspend fun addStudents(students: List<StudentRef>) =
        dao.upsertStudents(students.map { StudentEntity(it.pseudonymId, it.classId, it.alias, it.realName, it.stream?.name) })

    override suspend fun upsertAssignment(assignment: Assignment) =
        dao.upsertAssignment(AssignmentEntity(assignment.id, assignment.classId, assignment.dueEpochMs, json.encodeToString(Assignment.serializer(), assignment)))

    override suspend fun upsertAnnouncement(announcement: Announcement) =
        dao.upsertAnnouncement(AnnouncementEntity(announcement.id, announcement.classId, json.encodeToString(Announcement.serializer(), announcement)))

    override suspend fun allRealNames(): List<String> = dao.realNames()

    /** Timetable is mocked from classes until the school timetable integration (v1) lands. */
    override fun observeToday(): Flow<List<LessonSlot>> = observeClasses().map { classes ->
        val start = LocalDate.now().atTime(8, 0).atZone(ZoneId.of("Asia/Singapore")).toInstant().toEpochMilli()
        classes.take(4).mapIndexed { i, c ->
            LessonSlot("slot-${c.id}", c.id, c.name, start + i * 95 * 60_000L, 60, null, listOf("03-12", "Science Lab 2", "04-05", "ITR 1")[i % 4])
        }
    }

    private suspend fun seedIfEmpty() {
        if (dao.classCount() > 0) return
        val year = LocalDate.now().year
        val classes = listOf(
            SchoolClass("c-1e3", "school-demo", "1E3 Mathematics", Level.SEC1, "math", Stream.G3, year),
            SchoolClass("c-1n1", "school-demo", "1N1 Mathematics", Level.SEC1, "math", Stream.G2, year),
            SchoolClass("c-2t2", "school-demo", "2T2 Science", Level.SEC2, "science", Stream.G1, year),
            SchoolClass("c-4b", "school-demo", "P4 Bravery – Maths", Level.P4, "math", Stream.PRI_STANDARD, year),
        )
        dao.upsertClasses(classes.map { ClassEntity(it.id, json.encodeToString(SchoolClass.serializer(), it)) })
        classes.forEach { c ->
            dao.upsertStudents((1..28).map { n ->
                val stream = if (c.level.band == LevelBand.SECONDARY && n % 5 == 0) Stream.G2 else c.stream
                StudentEntity("STU-${c.id.uppercase().takeLast(3)}${"%02d".format(n)}", c.id, "Student %02d".format(n), null, stream?.name)
            })
        }
        val day = 86_400_000L
        val now = clock.nowMs()
        listOf(
            Assignment("a1", "c-1e3", "Algebra practice set 3", now + day, submitted = 22, total = 28, toMark = 22,
                rubric = Rubric("r1", "Algebra working", listOf("Correct method", "Accurate answer", "Clear working"))),
            Assignment("a2", "c-2t2", "Cells: labelled diagram", now + 2 * day, submitted = 15, total = 28, toMark = 9),
            Assignment("a3", "c-4b", "Fractions worksheet", now - day / 2, submitted = 27, total = 28, toMark = 27),
        ).forEach { upsertAssignment(it) }
    }
}
