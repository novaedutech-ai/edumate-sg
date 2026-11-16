package sg.edumate.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SchoolClass(
    @Classified(DataClassification.INTERNAL) val id: String,
    @Classified(DataClassification.INTERNAL) val schoolId: String,
    @Classified(DataClassification.INTERNAL) val name: String,
    @Classified(DataClassification.PUBLIC) val level: Level,
    @Classified(DataClassification.PUBLIC) val subjectId: String,
    @Classified(DataClassification.INTERNAL) val stream: Stream?,
    @Classified(DataClassification.INTERNAL) val academicYear: Int,
    @Classified(DataClassification.INTERNAL) val endsAtEpochMs: Long? = null,
)

/**
 * A student on a roster. The pseudonym is the only identifier that leaves the device by default.
 * [realName] is held only if the school enables it, is stored encrypted at rest (SQLCipher), is never
 * synced to analytics and is always tokenised before any AI call. NRIC/FIN and contact details are
 * deliberately not modelled.
 */
@Serializable
data class StudentRef(
    @Classified(DataClassification.PERSONAL) val pseudonymId: String,
    @Classified(DataClassification.INTERNAL) val classId: String,
    @Classified(DataClassification.PERSONAL) val alias: String,
    @Classified(DataClassification.SENSITIVE) val realName: String? = null,
    @Classified(DataClassification.INTERNAL) val stream: Stream? = null,
    @Classified(DataClassification.SENSITIVE) val masteryEstimate: Double? = null,
) {
    val displayName: String get() = realName ?: alias
}

@Serializable
enum class SubmissionStatus { NOT_SUBMITTED, SUBMITTED, LATE, MARKED }

@Serializable
data class Rubric(val id: String, val title: String, val criteria: List<String>)

@Serializable
data class Assignment(
    val id: String,
    val classId: String,
    val title: String,
    val dueEpochMs: Long,
    val rubric: Rubric? = null,
    val linkedDocUrl: String? = null,
    val worksheetId: String? = null,
    val submitted: Int,
    val total: Int,
    val toMark: Int,
    val remindBeforeMinutes: Int = 24 * 60,
)

@Serializable
enum class Audience { STUDENTS, PARENTS }

@Serializable
data class Announcement(
    val id: String,
    val classId: String,
    val audience: Audience,
    val body: String,
    val scheduledForEpochMs: Long? = null,
    val sentAtEpochMs: Long? = null,
    val readCount: Int = 0,
    val recipientCount: Int = 0,
)

@Serializable
data class LessonSlot(val id: String, val classId: String, val className: String, val startsAtEpochMs: Long,
                      val durationMin: Int, val lessonPlanId: String?, val room: String?)

@Serializable
data class DashboardSummary(
    val lessonsToday: List<LessonSlot>,
    val assignmentsToMark: List<Assignment>,
    val pendingInterventions: Int,
    val draftsAwaitingReview: Int,
)
