package sg.edumate.domain.ai

/**
 * Replaces identifiable data with stable placeholder tokens before text leaves the device for
 * the AI backend, and restores them in the response. The vault never leaves the device.
 *
 * Detects: NRIC/FIN, email addresses, Singapore phone numbers, and roster names (full names and
 * individual name parts of 3+ letters). Over-tokenising a common word that is also a name (e.g. "Rose")
 * is an accepted trade-off: we prefer false positives to leaking a child's name.
 */
class PiiTokeniser(knownNames: Collection<String> = emptyList()) {

    data class Result(val text: String, val vault: Map<String, String>, val replacements: Int)

    private val namePatterns: List<Regex> = knownNames
        .flatMap { full -> listOf(full.trim()) + full.trim().split(Regex("\\s+")).filter { it.length >= 3 } }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedByDescending { it.length } // full names before their parts
        .map { Regex("(?<![\\p{L}])" + Regex.escape(it) + "(?![\\p{L}])", RegexOption.IGNORE_CASE) }

    fun tokenise(input: String, existingVault: Map<String, String> = emptyMap()): Result {
        val vault = LinkedHashMap(existingVault)          // token -> original
        val reverse = HashMap<String, String>()           // normalised original -> token
        existingVault.forEach { (t, v) -> reverse[v.lowercase()] = t }
        val counters = HashMap<String, Int>()
        existingVault.keys.forEach { t -> TOKEN.matchEntire(t)?.let { m ->
            val k = m.groupValues[1]; counters[k] = maxOf(counters[k] ?: 0, m.groupValues[2].toInt()) } }
        var replacements = 0

        fun tokenFor(kind: String, original: String): String {
            replacements++
            return reverse.getOrPut(original.lowercase()) {
                val n = (counters[kind] ?: 0) + 1
                counters[kind] = n
                "[${kind}_$n]".also { vault[it] = original }
            }
        }

        var text = input
        for ((kind, regex) in STRUCTURED_PATTERNS) {
            text = regex.replace(text) { tokenFor(kind, it.value) }
        }
        for (regex in namePatterns) {
            text = regex.replace(text) { tokenFor("PERSON", it.value) }
        }
        return Result(text, vault, replacements)
    }

    fun detokenise(text: String, vault: Map<String, String>): String =
        TOKEN_ANYWHERE.replace(text) { m -> vault[m.value] ?: m.value }

    /** True if [text] still contains a structured identifier. Used as a last-line check on outputs. */
    fun containsStructuredPii(text: String): Boolean = STRUCTURED_PATTERNS.any { (_, r) -> r.containsMatchIn(text) }

    companion object {
        private val TOKEN = Regex("\\[([A-Z]+)_(\\d+)]")
        private val TOKEN_ANYWHERE = Regex("\\[[A-Z]+_\\d+]")
        val NRIC_FIN = Regex("(?<![A-Za-z0-9])[STFGM]\\d{7}[A-Z](?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
        val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        val SG_PHONE = Regex("(?<![\\d+])(?:\\+65[\\s-]?)?[3689]\\d{3}[\\s-]?\\d{4}(?!\\d)")
        private val STRUCTURED_PATTERNS = listOf("NRIC" to NRIC_FIN, "EMAIL" to EMAIL, "PHONE" to SG_PHONE)
    }
}
