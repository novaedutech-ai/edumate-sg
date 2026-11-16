package sg.edumate.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        LessonPlanEntity::class, WorksheetEntity::class, ClassEntity::class, StudentEntity::class,
        AssignmentEntity::class, AnnouncementEntity::class, ResourceFlagEntity::class, UserResourceEntity::class,
        AuditEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class EduMateDatabase : RoomDatabase() {
    abstract fun lessonPlanDao(): LessonPlanDao
    abstract fun worksheetDao(): WorksheetDao
    abstract fun classDao(): ClassDao
    abstract fun resourceDao(): ResourceDao
    abstract fun auditDao(): AuditDao
}
