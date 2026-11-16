package sg.edumate.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Content entities store the domain object as JSON plus indexed columns for queries. */
@Entity(tableName = "lesson_plans", indices = [Index("updatedAt"), Index("reviewStatus")])
data class LessonPlanEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subjectId: String,
    val level: String,
    val reviewStatus: String,
    val syncState: String,
    val updatedAt: Long,
    val json: String,
)

@Entity(tableName = "worksheets", indices = [Index("updatedAt")])
data class WorksheetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val reviewStatus: String,
    val syncState: String,
    val updatedAt: Long,
    val json: String,
)

@Entity(tableName = "classes")
data class ClassEntity(@PrimaryKey val id: String, val json: String)

/** realName is null unless the school enables real names. The whole DB is SQLCipher-encrypted. */
@Entity(tableName = "students", indices = [Index("classId")])
data class StudentEntity(
    @PrimaryKey val pseudonymId: String,
    val classId: String,
    val alias: String,
    val realName: String?,
    val stream: String?,
)

@Entity(tableName = "assignments", indices = [Index("dueAt")])
data class AssignmentEntity(@PrimaryKey val id: String, val classId: String, val dueAt: Long, val json: String)

@Entity(tableName = "announcements", indices = [Index("classId")])
data class AnnouncementEntity(@PrimaryKey val id: String, val classId: String, val json: String)

@Entity(tableName = "resource_flags")
data class ResourceFlagEntity(@PrimaryKey val resourceId: String, val bookmarked: Boolean, val offline: Boolean, val cachedJson: String?)

@Entity(tableName = "user_resources")
data class UserResourceEntity(@PrimaryKey val id: String, val json: String, val createdAt: Long)

@Entity(tableName = "audit_log", indices = [Index("atEpochMs")])
data class AuditEntity(
    @PrimaryKey val id: String,
    val atEpochMs: Long,
    val actorRole: String,
    val action: String,
    val detail: String,
    val piiTokensReplaced: Int,
    val requestId: String?,
)
