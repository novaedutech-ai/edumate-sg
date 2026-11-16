package sg.edumate.domain.repository

import kotlinx.coroutines.flow.Flow
import sg.edumate.domain.model.*

interface LessonPlanRepository {
    fun observeAll(): Flow<List<LessonPlan>>
    fun observe(id: String): Flow<LessonPlan?>
    suspend fun get(id: String): LessonPlan?
    suspend fun upsert(plan: LessonPlan)
    suspend fun delete(id: String)
    suspend fun purgeUnreviewedDraftsOlderThan(epochMs: Long): Int
}

interface WorksheetRepository {
    fun observeAll(): Flow<List<Worksheet>>
    suspend fun get(id: String): Worksheet?
    suspend fun upsert(worksheet: Worksheet)
    suspend fun delete(id: String)
}

interface CurriculumRepository {
    fun subjects(band: LevelBand): List<Subject>
    fun topics(level: Level, subjectId: String): List<SyllabusTopic>
    fun topic(id: String): SyllabusTopic?
    fun subject(id: String): Subject?
}

/** A pluggable resource source (MOE library via official API/SSO, user uploads, open-licensed). */
interface ResourceAdapter {
    val kind: ResourceSourceKind
    suspend fun search(query: ResourceQuery): List<Resource>
    suspend fun byIds(ids: Collection<String>): List<Resource>
}

interface ResourceRepository {
    suspend fun search(query: ResourceQuery): List<Resource>
    suspend fun byIds(ids: Collection<String>): List<Resource>
    fun observeBookmarkIds(): Flow<Set<String>>
    suspend fun toggleBookmark(resourceId: String)
    suspend fun setOffline(resourceId: String, offline: Boolean)
}

interface ClassRepository {
    fun observeClasses(): Flow<List<SchoolClass>>
    fun observeStudents(classId: String): Flow<List<StudentRef>>
    fun observeAssignments(): Flow<List<Assignment>>
    fun observeAnnouncements(classId: String): Flow<List<Announcement>>
    suspend fun addStudents(students: List<StudentRef>)
    suspend fun upsertAssignment(assignment: Assignment)
    suspend fun upsertAnnouncement(announcement: Announcement)
    suspend fun allRealNames(): List<String>
}

interface ScheduleRepository {
    fun observeToday(): Flow<List<LessonSlot>>
}

interface AuditLogger {
    suspend fun log(entry: AuditEntry)
}

interface AuditLogRepository : AuditLogger {
    fun observeRecent(limit: Int = 200): Flow<List<AuditEntry>>
    suspend fun purgeOlderThan(epochMs: Long): Int
}

/** Real-time transport for a live class session (WebSocket in production, simulated in dev). */
interface LiveSessionGateway {
    suspend fun start(activityTitle: String, classId: String?): LiveSessionHandle
}

interface LiveSessionHandle {
    val sessionId: String
    val joinCode: String
    val events: Flow<LiveEvent>
    suspend fun pushIntervention(intervention: Intervention)
    suspend fun assignGroups(groups: List<StudentGroup>)
    suspend fun end()
}
