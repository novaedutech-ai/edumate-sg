package sg.edumate.domain.model

enum class StudentStatus(val label: String) { NOT_STARTED("Not started"), WORKING("Working"), STUCK("Stuck"), DONE("Done") }

enum class FlagReason(val label: String) {
    HELP_REQUESTED("Asked for help"),
    SELF_REPORTED_STUCK("Marked stuck"),
    LOW_CONFIDENCE("Low confidence"),
    REPEATED_INCORRECT("Repeated incorrect"),
    INACTIVE("Inactive"),
    PASTORAL_CONCERN("Pastoral concern – check in"),
}

/** Events arriving from the real-time transport. Student IDs are session-scoped pseudonyms. */
sealed interface LiveEvent {
    val studentId: String
    val atMs: Long

    data class Joined(override val studentId: String, val alias: String, val stream: Stream?, override val atMs: Long) : LiveEvent
    data class StatusChanged(override val studentId: String, val status: StudentStatus, override val atMs: Long) : LiveEvent
    data class ResponseSubmitted(
        override val studentId: String,
        val blockId: String,
        val text: String,
        /** 1 (not confident) – 5 (very confident). */
        val confidence: Int,
        val correct: Boolean?,
        override val atMs: Long,
    ) : LiveEvent
    data class HelpRequested(override val studentId: String, override val atMs: Long) : LiveEvent
    data class Left(override val studentId: String, override val atMs: Long) : LiveEvent
}

data class StudentLiveState(
    val studentId: String,
    val alias: String,
    val stream: Stream?,
    val status: StudentStatus = StudentStatus.NOT_STARTED,
    val connected: Boolean = true,
    val lastActivityMs: Long,
    val responses: List<String> = emptyList(),
    val latestConfidence: Int? = null,
    val answered: Int = 0,
    val correct: Int = 0,
    val consecutiveIncorrect: Int = 0,
    val helpRequested: Boolean = false,
    val flags: Set<FlagReason> = emptySet(),
) {
    val flagged: Boolean get() = flags.isNotEmpty()
    val accuracy: Double? get() = if (answered == 0) null else correct.toDouble() / answered
}

data class WordFreq(val word: String, val count: Int)

data class StudentGroup(val id: String, val name: String, val memberIds: List<String>, val task: String? = null)

data class Intervention(
    val id: String,
    val title: String,
    val content: String,
    val tier: ScaffoldTier,
    val targetStudentIds: List<String>,
    val sentAtMs: Long? = null,
)

data class LiveSessionState(
    val sessionId: String,
    val joinCode: String,
    val activityTitle: String,
    val startedAtMs: Long,
    val students: Map<String, StudentLiveState> = emptyMap(),
    val wordCloud: List<WordFreq> = emptyList(),
    val groups: List<StudentGroup> = emptyList(),
    val interventions: List<Intervention> = emptyList(),
) {
    fun count(status: StudentStatus) = students.values.count { it.connected && it.status == status }
    val flagged: List<StudentLiveState> get() = students.values.filter { it.connected && it.flagged }
    val averageConfidence: Double? get() = students.values.mapNotNull { it.latestConfidence }.takeIf { it.isNotEmpty() }?.average()

    companion object { const val MAX_STUDENTS = 45 }
}
