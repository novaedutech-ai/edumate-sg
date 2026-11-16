package sg.edumate.domain.quality

import sg.edumate.domain.model.*
import javax.inject.Inject

/** Checks run on every generated worksheet version before the teacher reviews it. */
class WorksheetQualityChecker @Inject constructor() {
    private val spelling = SgEnglishChecker()
    private val readability = ReadabilityChecker()

    fun check(version: WorksheetVersion, level: Level): List<QualityCheck> {
        val text = version.questions.joinToString(". ") { it.prompt }
        val spellingIssues = spelling.check(text + " " + version.questions.flatMap { q -> q.scaffolds.map { it.content } }.joinToString(" "))
        val grade = readability.gradeLevel(text)
        val target = readability.targetMax(level, version.stream.tier)
        val keyed = version.answerKey.map { it.questionId }.toSet()
        val missingKeys = version.questions.count { it.id !in keyed }
        val scaffolded = version.questions.count { it.scaffolds.isNotEmpty() }

        return buildList {
            add(QualityCheck("SG English spelling", spellingIssues.isEmpty(),
                if (spellingIssues.isEmpty()) "British spelling throughout" else spellingIssues.joinToString { "${it.found} → ${it.suggestion}" }))
            add(QualityCheck("Reading level", grade == null || grade <= target,
                if (grade == null) "Too little text to estimate" else "Estimated grade $grade (target ≤ $target for ${level.label} ${version.stream.label})"))
            add(QualityCheck("Answer key complete", missingKeys == 0,
                if (missingKeys == 0) "Every question has a key" else "$missingKeys question(s) missing an answer"))
            if (version.stream.tier == ScaffoldTier.SUPPORT)
                add(QualityCheck("Scaffolds present", scaffolded == version.questions.size, "$scaffolded/${version.questions.size} questions scaffolded"))
            if (version.stream.tier == ScaffoldTier.EXTENSION)
                add(QualityCheck("Challenge item", version.questions.any { it.isChallenge || it.difficulty == Difficulty.ADVANCED }, "Extension version includes higher-order items"))
        }
    }

    /** The versions must be "coordinated": same core items, aligned to the same outcomes. */
    fun coordinationIssues(versions: List<WorksheetVersion>): List<String> {
        if (versions.size < 2) return emptyList()
        val core = versions.map { v -> v.questions.filterNot { it.isChallenge } }
        val issues = mutableListOf<String>()
        if (core.map { it.size }.distinct().size > 1) issues += "Versions have different numbers of core questions"
        val outcomes = core.map { qs -> qs.map { it.outcomeCode } }
        if (outcomes.distinct().size > 1) issues += "Question outcomes are not aligned across versions"
        return issues
    }
}
