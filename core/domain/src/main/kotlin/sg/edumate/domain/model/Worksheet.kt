package sg.edumate.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class QuestionType(val label: String) {
    MCQ("MCQ"), SHORT_ANSWER("Short answer"), STRUCTURED("Structured"),
    FILL_IN_BLANK("Fill in the blank"), OPEN_ENDED("Open-ended"),
}

@Serializable
enum class Difficulty { FOUNDATIONAL, INTERMEDIATE, ADVANCED }

@Serializable
enum class ScaffoldKind(val label: String) {
    SENTENCE_STARTER("Sentence starter"), WORKED_EXAMPLE("Worked example"), HINT("Hint"),
    WORD_BANK("Word bank"), GRAPHIC_ORGANISER("Graphic organiser"), CHALLENGE("Challenge"),
}

@Serializable
data class Scaffold(val kind: ScaffoldKind, val content: String)

@Serializable
data class WorksheetQuestion(
    val id: String,
    val number: Int,
    val type: QuestionType,
    val difficulty: Difficulty,
    val prompt: String,
    val options: List<String> = emptyList(),
    val scaffolds: List<Scaffold> = emptyList(),
    val marks: Int,
    val outcomeCode: String,
    val isChallenge: Boolean = false,
)

@Serializable
data class AnswerKeyItem(val questionId: String, val answer: String, val markingPoints: List<String>)

@Serializable
data class QualityCheck(val name: String, val passed: Boolean, val detail: String)

@Serializable
data class WorksheetVersion(
    val stream: Stream,
    val questions: List<WorksheetQuestion>,
    val answerKey: List<AnswerKeyItem>,
    val checks: List<QualityCheck> = emptyList(),
) {
    val totalMarks: Int get() = questions.sumOf { it.marks }
}

@Serializable
data class Misconception(val description: String, val remedy: String)

@Serializable
data class DifficultyMix(val foundational: Int, val intermediate: Int, val advanced: Int) {
    init { require(foundational + intermediate + advanced == 100) { "Difficulty mix must total 100%" } }

    /** Shift the mix toward easier/harder items for a tier, keeping the total at 100. */
    fun forTier(tier: ScaffoldTier): DifficultyMix = when (tier) {
        ScaffoldTier.CORE -> this
        ScaffoldTier.SUPPORT -> {
            val moveA = minOf(advanced, 20); val moveI = minOf(intermediate, 10)
            DifficultyMix(foundational + moveA + moveI, intermediate - moveI, advanced - moveA)
        }
        ScaffoldTier.EXTENSION -> {
            val moveF = minOf(foundational, 20); val moveI = minOf(intermediate, 10)
            DifficultyMix(foundational - moveF, intermediate - moveI, advanced + moveF + moveI)
        }
    }

    /** Distribute [n] questions according to the mix (largest-remainder method). */
    fun allocate(n: Int): List<Difficulty> {
        val raw = listOf(foundational, intermediate, advanced).map { it * n / 100.0 }
        val counts = raw.map { it.toInt() }.toMutableList()
        var remaining = n - counts.sum()
        raw.mapIndexed { i, r -> i to (r - r.toInt()) }.sortedByDescending { it.second }.forEach { (i, _) ->
            if (remaining > 0) { counts[i]++; remaining-- }
        }
        return Difficulty.entries.flatMapIndexed { i, d -> List(counts[i]) { d } }
    }

    companion object { val BALANCED = DifficultyMix(40, 40, 20) }
}

@Serializable
data class WorksheetSpec(
    val topic: SyllabusTopic,
    val objectives: List<String>,
    val questionCount: Int,
    val questionTypes: Set<QuestionType>,
    val difficultyMix: DifficultyMix = DifficultyMix.BALANCED,
    val streams: List<Stream>,
    val referenceResourceIds: List<String> = emptyList(),
    val teacherNotes: String = "",
) {
    init {
        require(questionCount in 1..40) { "Question count must be 1-40" }
        require(questionTypes.isNotEmpty()) { "Pick at least one question type" }
        require(streams.isNotEmpty()) { "Pick at least one stream" }
    }
}

@Serializable
data class Worksheet(
    @Classified(DataClassification.INTERNAL) val id: String,
    @Classified(DataClassification.INTERNAL) val title: String,
    @Classified(DataClassification.PUBLIC) val topicId: String,
    @Classified(DataClassification.PUBLIC) val level: Level,
    @Classified(DataClassification.PUBLIC) val subjectId: String,
    @Classified(DataClassification.INTERNAL) val objectives: List<String>,
    @Classified(DataClassification.INTERNAL) val versions: List<WorksheetVersion>,
    @Classified(DataClassification.INTERNAL) val misconceptions: List<Misconception>,
    @Classified(DataClassification.PERSONAL) val ownerId: String,
    @Classified(DataClassification.INTERNAL) val reviewStatus: ReviewStatus,
    @Classified(DataClassification.INTERNAL) val provenance: AiProvenance?,
    @Classified(DataClassification.INTERNAL) val updatedAtEpochMs: Long,
    @Classified(DataClassification.INTERNAL) val syncState: SyncState = SyncState.LOCAL_ONLY,
)
