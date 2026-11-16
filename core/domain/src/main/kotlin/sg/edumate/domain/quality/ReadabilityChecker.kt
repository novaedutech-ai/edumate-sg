package sg.edumate.domain.quality

import sg.edumate.domain.model.Level
import sg.edumate.domain.model.ScaffoldTier
import kotlin.math.roundToInt

/** Flesch–Kincaid grade estimate with a heuristic syllable counter. Good enough to flag outliers. */
class ReadabilityChecker {

    fun gradeLevel(text: String): Double? {
        val sentences = text.split(Regex("[.!?]+")).count { it.isNotBlank() }.coerceAtLeast(1)
        val words = Regex("[A-Za-z']+").findAll(text).map { it.value }.toList()
        if (words.size < 5) return null
        val syllables = words.sumOf { syllables(it) }
        val grade = 0.39 * words.size / sentences + 11.8 * syllables / words.size - 15.59
        return (grade * 10).roundToInt() / 10.0
    }

    /** Target max grade for a level and tier. Support tier aims ~1.5 grades lower. */
    fun targetMax(level: Level, tier: ScaffoldTier): Double = level.approxGrade + when (tier) {
        ScaffoldTier.SUPPORT -> 0.5
        ScaffoldTier.CORE -> 2.0
        ScaffoldTier.EXTENSION -> 3.5
    }

    internal fun syllables(word: String): Int {
        val w = word.lowercase().trimEnd('\'', 's').ifEmpty { word.lowercase() }
        if (w.length <= 3) return 1
        val trimmed = w.removeSuffix("e").let { if (it.endsWith("l") && w.endsWith("le")) w else it }
        val groups = Regex("[aeiouy]+").findAll(trimmed).count()
        return groups.coerceAtLeast(1)
    }
}
