package sg.edumate.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import sg.edumate.app.data.local.*
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLessonPlanRepository @Inject constructor(private val dao: LessonPlanDao, private val codec: Json) : LessonPlanRepository {
    private fun LessonPlanEntity.toDomain() = codec.decodeFromString(LessonPlan.serializer(), json)
    private fun LessonPlan.toEntity() = LessonPlanEntity(id, title, subjectId, level.name, reviewStatus.name, syncState.name, updatedAtEpochMs,
        codec.encodeToString(LessonPlan.serializer(), this))

    override fun observeAll(): Flow<List<LessonPlan>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observe(id: String): Flow<LessonPlan?> = dao.observe(id).map { it?.toDomain() }
    override suspend fun get(id: String) = dao.get(id)?.toDomain()
    override suspend fun upsert(plan: LessonPlan) = dao.upsert(plan.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun purgeUnreviewedDraftsOlderThan(epochMs: Long) = dao.purgeDrafts(epochMs)
    suspend fun pendingUpload(): List<LessonPlan> = dao.pendingUpload().map { it.toDomain() }
}

@Singleton
class RoomWorksheetRepository @Inject constructor(private val dao: WorksheetDao, private val codec: Json) : WorksheetRepository {
    override fun observeAll(): Flow<List<Worksheet>> = dao.observeAll().map { list -> list.map { codec.decodeFromString(Worksheet.serializer(), it.json) } }
    override suspend fun get(id: String) = dao.get(id)?.let { codec.decodeFromString(Worksheet.serializer(), it.json) }
    override suspend fun upsert(worksheet: Worksheet) = dao.upsert(
        WorksheetEntity(worksheet.id, worksheet.title, worksheet.reviewStatus.name, worksheet.syncState.name, worksheet.updatedAtEpochMs,
            codec.encodeToString(Worksheet.serializer(), worksheet)))
    override suspend fun delete(id: String) = dao.delete(id)
}

@Singleton
class RoomAuditLogRepository @Inject constructor(private val dao: AuditDao) : AuditLogRepository {
    override suspend fun log(entry: AuditEntry) = dao.insert(
        AuditEntity(entry.id, entry.atEpochMs, entry.actorRole.name, entry.action.name, entry.detail, entry.piiTokensReplaced, entry.requestId))
    override fun observeRecent(limit: Int): Flow<List<AuditEntry>> = dao.observeRecent(limit).map { list ->
        list.map { AuditEntry(it.id, it.atEpochMs, Role.valueOf(it.actorRole), AuditAction.valueOf(it.action), it.detail, it.piiTokensReplaced, it.requestId) }
    }
    override suspend fun purgeOlderThan(epochMs: Long) = dao.purge(epochMs)
}
