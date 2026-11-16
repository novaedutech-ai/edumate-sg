package sg.edumate.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class BlockType(val label: String, val interactive: Boolean) {
    EXPLAIN("Explain", false),
    POLL("Poll", true),
    SHORT_ANSWER("Short answer", true),
    DRAWING("Drawing", true),
    SORTING("Sorting", true),
    MCQ("MCQ", true),
    REFLECTION("Reflection", true),
    GROUP_TASK("Group task", true),
    EXIT_TICKET("Exit ticket", true),
}

@Serializable
data class LessonBlock(
    val id: String,
    val type: BlockType,
    val title: String,
    val instructions: String,
    val durationMin: Int,
    /** null = whole class; otherwise targeted at one tier. */
    val tier: ScaffoldTier? = null,
    val options: List<String> = emptyList(),
    val isCheckForUnderstanding: Boolean = false,
)

@Serializable
data class DifferentiatedActivity(val tier: ScaffoldTier, val description: String)

@Serializable
data class LessonPlan(
    @Classified(DataClassification.INTERNAL) val id: String,
    @Classified(DataClassification.INTERNAL) val title: String,
    @Classified(DataClassification.PUBLIC) val level: Level,
    @Classified(DataClassification.PUBLIC) val subjectId: String,
    @Classified(DataClassification.PUBLIC) val topicId: String,
    @Classified(DataClassification.INTERNAL) val streams: List<Stream>,
    @Classified(DataClassification.INTERNAL) val durationMin: Int,
    @Classified(DataClassification.INTERNAL) val objectives: List<String>,
    @Classified(DataClassification.INTERNAL) val priorKnowledge: List<String>,
    @Classified(DataClassification.INTERNAL) val successCriteria: List<String>,
    @Classified(DataClassification.INTERNAL) val flow: List<LessonBlock>,
    @Classified(DataClassification.INTERNAL) val differentiation: List<DifferentiatedActivity>,
    @Classified(DataClassification.INTERNAL) val aflChecks: List<String>,
    @Classified(DataClassification.INTERNAL) val competencies: List<Competency21>,
    @Classified(DataClassification.INTERNAL) val tags: Set<String> = emptySet(),
    @Classified(DataClassification.INTERNAL) val version: Int = 1,
    @Classified(DataClassification.INTERNAL) val parentPlanId: String? = null,
    @Classified(DataClassification.PERSONAL) val ownerId: String,
    @Classified(DataClassification.INTERNAL) val sharedWithDepartment: Boolean = false,
    @Classified(DataClassification.INTERNAL) val reviewStatus: ReviewStatus,
    @Classified(DataClassification.INTERNAL) val provenance: AiProvenance? = null,
    @Classified(DataClassification.INTERNAL) val updatedAtEpochMs: Long,
    @Classified(DataClassification.INTERNAL) val syncState: SyncState = SyncState.LOCAL_ONLY,
) {
    val totalMinutes: Int get() = flow.sumOf { it.durationMin }
}
