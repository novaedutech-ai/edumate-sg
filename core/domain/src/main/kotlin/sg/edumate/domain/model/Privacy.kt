package sg.edumate.domain.model

import kotlinx.serialization.Serializable

/**
 * Privacy classification applied to every persisted field (see docs/03-data-model.md).
 *
 * - [PUBLIC]: curriculum data, open-licensed resources. No restrictions.
 * - [INTERNAL]: teacher-authored content (plans, worksheets). School-scoped.
 * - [PERSONAL]: data relating to an identifiable person (teacher name, student pseudonym linked to roster).
 * - [SENSITIVE]: data about minors that could cause harm if leaked (real names, responses, wellbeing flags).
 *   Never leaves the device/school tenant un-tokenised; never sent to an LLM.
 */
enum class DataClassification { PUBLIC, INTERNAL, PERSONAL, SENSITIVE }

/** Documentation + lint hook: marks a property's privacy class. Checked by DataClassificationTest. */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Classified(val value: DataClassification)

@Serializable
enum class Role { TEACHER, HOD, SCHOOL_ADMIN, STUDENT }

@Serializable
enum class AuditAction {
    AI_REQUEST, AI_RESPONSE_BLOCKED, AI_CITATION_REMOVED, CONTENT_APPROVED, SHARE_TO_STUDENTS,
    DATA_EXPORT, DATA_DELETE, CONSENT_GIVEN, CONSENT_WITHDRAWN, RETENTION_PURGE,
    ROSTER_IMPORT, PASTORAL_FLAG,
}

/** Audit entries never contain raw prompt text or student content, only counts and IDs. */
@Serializable
data class AuditEntry(
    val id: String,
    val atEpochMs: Long,
    val actorRole: Role,
    val action: AuditAction,
    val detail: String,
    val piiTokensReplaced: Int = 0,
    val requestId: String? = null,
)

@Serializable
data class RetentionPolicy(
    val liveResponsesDays: Int = 30,
    val sessionAnalyticsDays: Int = 365,
    val auditLogDays: Int = 730,
    val rosterAfterClassEndDays: Int = 180,
    val aiDraftsUnreviewedDays: Int = 90,
)

/** School-admin configurable settings. Defaults are the most privacy-protective option. */
@Serializable
data class SchoolPrivacySettings(
    val schoolId: String,
    val dpoName: String,
    val dpoEmail: String,
    val dpoPhone: String? = null,
    val allowRealStudentNames: Boolean = false,
    val allowDirectStudentMessaging: Boolean = false,
    val allowLeaderboards: Boolean = true,
    val leaderboardsTeamOnly: Boolean = true,
    val aiFeaturesEnabled: Boolean = true,
    val requireAppLock: Boolean = true,
    val retention: RetentionPolicy = RetentionPolicy(),
    val privacyNoticeVersion: Int = 1,
)

@Serializable
data class ConsentRecord(
    val userId: String,
    val noticeVersion: Int,
    val acceptedAtEpochMs: Long,
    val aiProcessingAccepted: Boolean,
    val analyticsAccepted: Boolean,
)
