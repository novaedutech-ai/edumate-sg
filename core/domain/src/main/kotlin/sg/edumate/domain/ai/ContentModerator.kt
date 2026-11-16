package sg.edumate.domain.ai

enum class ContentOrigin { AI_OUTPUT, STUDENT_SUBMISSION }

data class ModerationResult(
    val allowed: Boolean,
    val flags: List<String>,
    /** Student content suggesting the student may be at risk: route to the teacher, never auto-hide silently. */
    val pastoralConcern: Boolean = false,
)

interface ContentModerator {
    fun review(text: String, origin: ContentOrigin): ModerationResult
}

/**
 * On-device first-pass filter. The backend runs a full moderation model as well; this layer
 * guarantees a baseline even offline. Word lists are intentionally short and configurable by school.
 */
class LocalContentModerator(
    private val blocked: Set<String> = DEFAULT_BLOCKED,
    private val pastoral: Set<String> = DEFAULT_PASTORAL,
    private val tokeniser: PiiTokeniser = PiiTokeniser(),
) : ContentModerator {

    override fun review(text: String, origin: ContentOrigin): ModerationResult {
        val lower = text.lowercase()
        val flags = mutableListOf<String>()
        blocked.filter { containsWord(lower, it) }.forEach { flags += "blocked_term:$it" }
        val pastoralHit = pastoral.any { lower.contains(it) }
        if (pastoralHit) flags += "pastoral_concern"
        if (origin == ContentOrigin.AI_OUTPUT && tokeniser.containsStructuredPii(text)) flags += "pii_in_output"

        val allowed = flags.none { it.startsWith("blocked_term") || it == "pii_in_output" } &&
            !(origin == ContentOrigin.AI_OUTPUT && pastoralHit)
        return ModerationResult(allowed, flags, pastoralConcern = origin == ContentOrigin.STUDENT_SUBMISSION && pastoralHit)
    }

    private fun containsWord(text: String, word: String) =
        Regex("(?<![a-z])" + Regex.escape(word) + "(?![a-z])").containsMatchIn(text)

    companion object {
        val DEFAULT_BLOCKED = setOf("fuck", "shit", "bitch", "porn", "nude", "kill yourself")
        val DEFAULT_PASTORAL = setOf("kill myself", "want to die", "hurt myself", "self harm", "self-harm", "suicide", "no one cares about me")
    }
}
