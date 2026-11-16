package sg.edumate.app.data

import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sg.edumate.app.data.local.EduMateDatabase
import sg.edumate.app.data.settings.UserPreferencesRepository
import sg.edumate.app.security.SecretStore
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.*
import sg.edumate.domain.usecase.CurrentUser
import sg.edumate.domain.util.Clock
import sg.edumate.domain.util.IdGenerator
import javax.inject.Inject
import javax.inject.Singleton

/** PDPA access, portability and deletion for the signed-in teacher's on-device data. */
@Singleton
class PrivacyDataService @Inject constructor(
    private val db: EduMateDatabase,
    private val plans: LessonPlanRepository,
    private val worksheets: WorksheetRepository,
    private val classes: ClassRepository,
    private val audit: AuditLogRepository,
    private val prefs: UserPreferencesRepository,
    private val secrets: SecretStore,
    private val json: Json,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val user: CurrentUser,
) {
    @Serializable
    data class Export(
        val exportedAtEpochMs: Long,
        val userId: String,
        val lessonPlans: List<LessonPlan>,
        val worksheets: List<Worksheet>,
        val classes: List<SchoolClass>,
        val students: List<StudentRef>,
        val assignments: List<Assignment>,
    )

    suspend fun exportJson(): String {
        val cls = classes.observeClasses().first()
        val export = Export(
            clock.nowMs(), user.id, plans.observeAll().first(), worksheets.observeAll().first(), cls,
            cls.flatMap { classes.observeStudents(it.id).first() }, classes.observeAssignments().first(),
        )
        audit.log(AuditEntry(ids.newId(), clock.nowMs(), user.role, AuditAction.DATA_EXPORT, "on-device export"))
        return json.encodeToString(Export.serializer(), export)
    }

    /** Deletes all local personal data. Server-side deletion is requested via DELETE /v1/me/data. */
    suspend fun deleteAllLocalData() {
        db.lessonPlanDao().clear(); db.worksheetDao().clear()
        db.classDao().apply { clearStudents(); clearClasses(); clearAssignments(); clearAnnouncements() }
        db.resourceDao().apply { clearUserResources(); clearFlags() }
        audit.log(AuditEntry(ids.newId(), clock.nowMs(), user.role, AuditAction.DATA_DELETE, "all local data deleted"))
        prefs.clear()
        secrets.put(SecretStore.ACCESS_TOKEN, null); secrets.put(SecretStore.REFRESH_TOKEN, null)
    }
}
