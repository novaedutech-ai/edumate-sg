package sg.edumate.domain.live

import sg.edumate.domain.ai.ContentModerator
import sg.edumate.domain.ai.ContentOrigin
import sg.edumate.domain.model.*

/**
 * Pure state machine for a live session. The ViewModel feeds transport events and periodic
 * ticks; the reducer derives per-student status, struggle flags and the word cloud.
 */
class LiveSessionReducer(
    private val moderator: ContentModerator,
    private val inactivityMs: Long = 90_000,
    private val lowConfidenceThreshold: Int = 2,
    private val incorrectStreak: Int = 2,
) {
    fun reduce(state: LiveSessionState, event: LiveEvent): LiveSessionState {
        val existing = state.students[event.studentId]
        if (existing == null && event !is LiveEvent.Joined) return state
        if (event is LiveEvent.Joined && existing == null && state.students.size >= LiveSessionState.MAX_STUDENTS) return state

        val updated: StudentLiveState = when (event) {
            is LiveEvent.Joined -> existing?.copy(connected = true, lastActivityMs = event.atMs)
                ?: StudentLiveState(event.studentId, event.alias, event.stream, lastActivityMs = event.atMs)
            is LiveEvent.StatusChanged -> existing!!.copy(status = event.status, lastActivityMs = event.atMs,
                helpRequested = existing.helpRequested && event.status != StudentStatus.DONE)
            is LiveEvent.HelpRequested -> existing!!.copy(helpRequested = true, status = StudentStatus.STUCK, lastActivityMs = event.atMs)
            is LiveEvent.Left -> existing!!.copy(connected = false)
            is LiveEvent.ResponseSubmitted -> {
                val s = existing!!
                val moderation = moderator.review(event.text, ContentOrigin.STUDENT_SUBMISSION)
                val pastoral = moderation.pastoralConcern
                // Blocked text is withheld from the class feed but still counted, and the teacher sees the flag.
                val visible = if (moderation.allowed) event.text else "[hidden by safety filter]"
                s.copy(
                    status = if (s.status == StudentStatus.NOT_STARTED) StudentStatus.WORKING else s.status,
                    responses = (s.responses + visible).takeLast(20),
                    latestConfidence = event.confidence.coerceIn(1, 5),
                    answered = s.answered + if (event.correct != null) 1 else 0,
                    correct = s.correct + if (event.correct == true) 1 else 0,
                    consecutiveIncorrect = when (event.correct) { false -> s.consecutiveIncorrect + 1; true -> 0; null -> s.consecutiveIncorrect },
                    lastActivityMs = event.atMs,
                    flags = if (pastoral) s.flags + FlagReason.PASTORAL_CONCERN else s.flags,
                )
            }
        }
        val students = state.students + (event.studentId to withFlags(updated, event.atMs))
        return state.copy(students = students, wordCloud = if (event is LiveEvent.ResponseSubmitted) wordCloud(students.values) else state.wordCloud)
    }

    /** Re-evaluate time-based flags (inactivity). Call every few seconds. */
    fun tick(state: LiveSessionState, nowMs: Long): LiveSessionState =
        state.copy(students = state.students.mapValues { (_, s) -> withFlags(s, nowMs) })

    fun clearFlags(state: LiveSessionState, studentIds: Collection<String>): LiveSessionState =
        state.copy(students = state.students.mapValues { (id, s) ->
            if (id in studentIds) s.copy(flags = s.flags.intersect(setOf(FlagReason.PASTORAL_CONCERN)), helpRequested = false, consecutiveIncorrect = 0) else s
        })

    private fun withFlags(s: StudentLiveState, nowMs: Long): StudentLiveState {
        val flags = buildSet {
            if (FlagReason.PASTORAL_CONCERN in s.flags) add(FlagReason.PASTORAL_CONCERN) // sticky until teacher clears
            if (s.helpRequested) add(FlagReason.HELP_REQUESTED)
            if (s.status == StudentStatus.STUCK && !s.helpRequested) add(FlagReason.SELF_REPORTED_STUCK)
            if ((s.latestConfidence ?: 5) <= lowConfidenceThreshold) add(FlagReason.LOW_CONFIDENCE)
            if (s.consecutiveIncorrect >= incorrectStreak) add(FlagReason.REPEATED_INCORRECT)
            if (s.connected && s.status != StudentStatus.DONE && nowMs - s.lastActivityMs > inactivityMs) add(FlagReason.INACTIVE)
        }
        return s.copy(flags = flags)
    }

    fun wordCloud(students: Collection<StudentLiveState>, top: Int = 25): List<WordFreq> =
        students.flatMap { it.responses }
            .filterNot { it.startsWith("[hidden") }
            .flatMap { Regex("[\\p{L}']+").findAll(it.lowercase()).map { m -> m.value.trim('\'') } }
            .filter { it.length > 2 && it !in STOPWORDS }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(top).map { WordFreq(it.key, it.value) }

    companion object {
        val STOPWORDS = setOf("the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her", "was", "one",
            "our", "out", "has", "his", "how", "its", "may", "who", "did", "get", "she", "him", "too", "use", "that", "with",
            "this", "they", "have", "from", "what", "when", "will", "your", "think", "because", "then", "than", "them", "there", "it's", "dont", "don't")
    }
}
