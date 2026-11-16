package sg.edumate.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonPlanDao {
    @Query("SELECT * FROM lesson_plans ORDER BY updatedAt DESC") fun observeAll(): Flow<List<LessonPlanEntity>>
    @Query("SELECT * FROM lesson_plans WHERE id = :id") fun observe(id: String): Flow<LessonPlanEntity?>
    @Query("SELECT * FROM lesson_plans WHERE id = :id") suspend fun get(id: String): LessonPlanEntity?
    @Query("SELECT * FROM lesson_plans WHERE syncState = 'PENDING_UPLOAD'") suspend fun pendingUpload(): List<LessonPlanEntity>
    @Upsert suspend fun upsert(e: LessonPlanEntity)
    @Query("DELETE FROM lesson_plans WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM lesson_plans WHERE reviewStatus = 'DRAFT_AI' AND updatedAt < :before") suspend fun purgeDrafts(before: Long): Int
    @Query("DELETE FROM lesson_plans") suspend fun clear()
}

@Dao
interface WorksheetDao {
    @Query("SELECT * FROM worksheets ORDER BY updatedAt DESC") fun observeAll(): Flow<List<WorksheetEntity>>
    @Query("SELECT * FROM worksheets WHERE id = :id") suspend fun get(id: String): WorksheetEntity?
    @Upsert suspend fun upsert(e: WorksheetEntity)
    @Query("DELETE FROM worksheets WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM worksheets") suspend fun clear()
}

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes") fun observeClasses(): Flow<List<ClassEntity>>
    @Query("SELECT COUNT(*) FROM classes") suspend fun classCount(): Int
    @Upsert suspend fun upsertClasses(e: List<ClassEntity>)
    @Query("SELECT * FROM students WHERE classId = :classId ORDER BY alias") fun observeStudents(classId: String): Flow<List<StudentEntity>>
    @Upsert suspend fun upsertStudents(e: List<StudentEntity>)
    @Query("SELECT realName FROM students WHERE realName IS NOT NULL") suspend fun realNames(): List<String>
    @Query("SELECT * FROM assignments ORDER BY dueAt") fun observeAssignments(): Flow<List<AssignmentEntity>>
    @Upsert suspend fun upsertAssignment(e: AssignmentEntity)
    @Query("SELECT * FROM announcements WHERE classId = :classId") fun observeAnnouncements(classId: String): Flow<List<AnnouncementEntity>>
    @Upsert suspend fun upsertAnnouncement(e: AnnouncementEntity)
    @Query("DELETE FROM students") suspend fun clearStudents()
    @Query("DELETE FROM classes") suspend fun clearClasses()
    @Query("DELETE FROM assignments") suspend fun clearAssignments()
    @Query("DELETE FROM announcements") suspend fun clearAnnouncements()
}

@Dao
interface ResourceDao {
    @Query("SELECT * FROM resource_flags") fun observeFlags(): Flow<List<ResourceFlagEntity>>
    @Query("SELECT * FROM resource_flags") suspend fun flags(): List<ResourceFlagEntity>
    @Query("SELECT * FROM resource_flags WHERE resourceId = :id") suspend fun flag(id: String): ResourceFlagEntity?
    @Upsert suspend fun upsertFlag(e: ResourceFlagEntity)
    @Query("SELECT * FROM user_resources ORDER BY createdAt DESC") suspend fun userResources(): List<UserResourceEntity>
    @Upsert suspend fun upsertUserResource(e: UserResourceEntity)
    @Query("DELETE FROM user_resources") suspend fun clearUserResources()
    @Query("DELETE FROM resource_flags") suspend fun clearFlags()
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_log ORDER BY atEpochMs DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<AuditEntity>>
    @Upsert suspend fun insert(e: AuditEntity)
    @Query("DELETE FROM audit_log WHERE atEpochMs < :before") suspend fun purge(before: Long): Int
}
