package sg.edumate.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LevelBand { PRIMARY, SECONDARY, PRE_U }

@Serializable
enum class Level(val label: String, val band: LevelBand, val approxGrade: Int) {
    P1("Primary 1", LevelBand.PRIMARY, 1), P2("Primary 2", LevelBand.PRIMARY, 2),
    P3("Primary 3", LevelBand.PRIMARY, 3), P4("Primary 4", LevelBand.PRIMARY, 4),
    P5("Primary 5", LevelBand.PRIMARY, 5), P6("Primary 6", LevelBand.PRIMARY, 6),
    SEC1("Secondary 1", LevelBand.SECONDARY, 7), SEC2("Secondary 2", LevelBand.SECONDARY, 8),
    SEC3("Secondary 3", LevelBand.SECONDARY, 9), SEC4("Secondary 4", LevelBand.SECONDARY, 10),
    SEC5("Secondary 5", LevelBand.SECONDARY, 10),
    JC1("JC 1", LevelBand.PRE_U, 11), JC2("JC 2", LevelBand.PRE_U, 12),
    MI1("MI Year 1", LevelBand.PRE_U, 11), MI2("MI Year 2", LevelBand.PRE_U, 12), MI3("MI Year 3", LevelBand.PRE_U, 12),
}

/** How much scaffolding a version of content carries. Drives both the AI and the templates. */
@Serializable
enum class ScaffoldTier { SUPPORT, CORE, EXTENSION }

/**
 * Learning streams. Secondary uses Full Subject-Based Banding (G1/G2/G3). Primary and Pre-U have
 * equivalents mapped onto the same three scaffold tiers so a single generator serves every level.
 */
@Serializable
enum class Stream(val label: String, val band: LevelBand, val tier: ScaffoldTier) {
    PRI_FOUNDATION("Foundation", LevelBand.PRIMARY, ScaffoldTier.SUPPORT),
    PRI_STANDARD("Standard", LevelBand.PRIMARY, ScaffoldTier.CORE),
    PRI_STANDARD_EXT("Standard (Extension)", LevelBand.PRIMARY, ScaffoldTier.EXTENSION),
    G1("G1", LevelBand.SECONDARY, ScaffoldTier.SUPPORT),
    G2("G2", LevelBand.SECONDARY, ScaffoldTier.CORE),
    G3("G3", LevelBand.SECONDARY, ScaffoldTier.EXTENSION),
    PREU_SUPPORT("H1 (Support)", LevelBand.PRE_U, ScaffoldTier.SUPPORT),
    PREU_CORE("H2", LevelBand.PRE_U, ScaffoldTier.CORE),
    PREU_EXTENSION("H2 (Extension) / H3", LevelBand.PRE_U, ScaffoldTier.EXTENSION),
    ;

    companion object {
        fun forBand(band: LevelBand): List<Stream> = entries.filter { it.band == band }
        fun of(band: LevelBand, tier: ScaffoldTier): Stream = entries.first { it.band == band && it.tier == tier }
    }
}

@Serializable
data class Subject(val id: String, val name: String, val bands: Set<LevelBand>)

@Serializable
data class LearningOutcome(val code: String, val description: String)

/**
 * A node in the syllabus tree. [code] is an internal placeholder until the official MOE/SEAB
 * syllabus feed is integrated; [isPlaceholderCode] makes that explicit in the UI and in AI citations.
 */
@Serializable
data class SyllabusTopic(
    val id: String,
    val code: String,
    val title: String,
    val subjectId: String,
    val level: Level,
    val strand: String,
    val outcomes: List<LearningOutcome>,
    val seabSyllabusRef: String? = null,
    val isPlaceholderCode: Boolean = true,
)

/** 21st Century Competencies (MOE framework). */
@Serializable
enum class Competency21(val label: String) {
    CRITICAL_ADAPTIVE_INVENTIVE("Critical, Adaptive and Inventive Thinking"),
    COMMUNICATION_COLLABORATION_INFO("Communication, Collaboration and Information Skills"),
    CIVIC_GLOBAL_CROSSCULTURAL("Civic, Global and Cross-cultural Literacy"),
    SOCIAL_EMOTIONAL("Social-Emotional Competencies"),
}
