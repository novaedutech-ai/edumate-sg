package sg.edumate.domain.usecase

import sg.edumate.domain.model.*
import sg.edumate.domain.repository.*
import sg.edumate.domain.util.Clock
import sg.edumate.domain.util.IdGenerator
import javax.inject.Inject

/**
 * Imports a roster CSV with header `name[,stream]`. Columns that look like NRIC/FIN, email or phone are
 * rejected outright: the app does not collect them (data minimisation).
 */
class RosterCsvImporter @Inject constructor(private val ids: IdGenerator) {

    data class Result(val students: List<StudentRef>, val skippedRows: Int, val rejectedColumns: List<String>)

    fun parse(csv: String, classId: String, keepRealNames: Boolean): Result {
        val lines = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return Result(emptyList(), 0, emptyList())
        val header = splitCsv(lines.first()).map { it.trim().lowercase() }
        val forbidden = header.filter { h -> FORBIDDEN_COLUMNS.any { h.contains(it) } }
        val nameIdx = header.indexOfFirst { it == "name" || it == "student name" || it == "full name" }
        val streamIdx = header.indexOf("stream")
        if (nameIdx < 0) return Result(emptyList(), lines.size - 1, forbidden)

        var skipped = 0
        val students = lines.drop(1).mapIndexedNotNull { i, line ->
            val cells = splitCsv(line)
            val name = cells.getOrNull(nameIdx)?.trim().orEmpty()
            if (name.isEmpty()) { skipped++; return@mapIndexedNotNull null }
            val stream = cells.getOrNull(streamIdx)?.trim()?.let { s -> Stream.entries.firstOrNull { it.name.equals(s, true) || it.label.equals(s, true) } }
            StudentRef(
                pseudonymId = "STU-" + ids.newId().filter { it.isLetterOrDigit() }.take(6).uppercase(),
                classId = classId,
                alias = "Student %02d".format(i + 1),
                realName = if (keepRealNames) name else null,
                stream = stream,
            )
        }
        return Result(students, skipped, forbidden)
    }

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>(); val sb = StringBuilder(); var quoted = false
        for (c in line) when {
            c == '"' -> quoted = !quoted
            c == ',' && !quoted -> { out += sb.toString(); sb.clear() }
            else -> sb.append(c)
        }
        out += sb.toString()
        return out
    }

    companion object { val FORBIDDEN_COLUMNS = listOf("nric", "fin", "email", "phone", "mobile", "address", "contact") }
}

class ImportRosterUseCase @Inject constructor(
    private val importer: RosterCsvImporter, private val classes: ClassRepository, private val audit: AuditLogger,
    private val settings: SchoolSettingsProvider, private val clock: Clock, private val ids: IdGenerator, private val user: CurrentUser,
) {
    suspend operator fun invoke(csv: String, classId: String): RosterCsvImporter.Result {
        val result = importer.parse(csv, classId, settings.current().allowRealStudentNames)
        classes.addStudents(result.students)
        audit.log(AuditEntry(ids.newId(), clock.nowMs(), user.role, AuditAction.ROSTER_IMPORT,
            "class:$classId imported=${result.students.size} rejectedColumns=${result.rejectedColumns.size}"))
        return result
    }
}

fun interface SchoolSettingsProvider { suspend fun current(): SchoolPrivacySettings }

class SearchResourcesUseCase @Inject constructor(private val repo: ResourceRepository) {
    suspend operator fun invoke(query: ResourceQuery): List<Resource> = repo.search(query)
}

/** Applies the school's retention policy. Scheduled daily by WorkManager. */
class ApplyRetentionPolicyUseCase @Inject constructor(
    private val plans: LessonPlanRepository, private val auditLog: AuditLogRepository,
    private val settings: SchoolSettingsProvider, private val clock: Clock, private val ids: IdGenerator,
) {
    suspend operator fun invoke(): Int {
        val p = settings.current().retention
        val now = clock.nowMs()
        val day = 86_400_000L
        val drafts = plans.purgeUnreviewedDraftsOlderThan(now - p.aiDraftsUnreviewedDays * day)
        val logs = auditLog.purgeOlderThan(now - p.auditLogDays * day)
        auditLog.log(AuditEntry(ids.newId(), now, Role.SCHOOL_ADMIN, AuditAction.RETENTION_PURGE, "drafts=$drafts auditEntries=$logs"))
        return drafts + logs
    }
}
