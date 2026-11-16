package sg.edumate.domain.live

import sg.edumate.domain.model.*

/** Suggests targeted follow-ups for flagged students. The teacher picks who receives them. */
class InterventionPlanner {

    data class Suggestion(val reason: FlagReason, val studentIds: List<String>, val intervention: Intervention)

    fun suggest(state: LiveSessionState, topicTitle: String, idPrefix: String = "int"): List<Suggestion> {
        val byReason = state.flagged.flatMap { s -> s.flags.map { it to s.studentId } }.groupBy({ it.first }, { it.second })
        return byReason.entries.sortedBy { it.key.ordinal }.mapIndexed { i, (reason, ids) ->
            val (title, content, tier) = when (reason) {
                FlagReason.REPEATED_INCORRECT -> Triple("Worked example: $topicTitle",
                    "Study this worked example step by step, then try the near-identical question below. Hint: check each step against the example.", ScaffoldTier.SUPPORT)
                FlagReason.LOW_CONFIDENCE -> Triple("Mini-lesson recap", "A 2-minute recap of the key idea, then one confidence-building question with a hint.", ScaffoldTier.SUPPORT)
                FlagReason.HELP_REQUESTED, FlagReason.SELF_REPORTED_STUCK -> Triple("Hint card", "Try this: break the question into smaller steps. What do you know? What do you need to find?", ScaffoldTier.SUPPORT)
                FlagReason.INACTIVE -> Triple("Check-in", "Are you able to continue? Tap 'I need help' or carry on with the next question.", ScaffoldTier.CORE)
                FlagReason.PASTORAL_CONCERN -> Triple("Teacher check-in (private, in person)", "Follow your school's pastoral care protocol. Do not discuss in front of the class.", ScaffoldTier.CORE)
            }
            Suggestion(reason, ids, Intervention("$idPrefix-${i + 1}", title, content, tier, ids))
        }
    }
}
