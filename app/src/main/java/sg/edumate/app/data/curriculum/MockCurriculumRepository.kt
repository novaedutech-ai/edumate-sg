package sg.edumate.app.data.curriculum

import sg.edumate.domain.model.*
import sg.edumate.domain.repository.CurriculumRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sample syllabus tree for development. Topic titles follow the shape of MOE syllabuses, but the
 * codes are internal placeholders (isPlaceholderCode = true) until the official syllabus feed is
 * licensed and integrated. The UI shows "placeholder code" wherever these are displayed.
 */
@Singleton
class MockCurriculumRepository @Inject constructor() : CurriculumRepository {

    private val subjects = listOf(
        Subject("math", "Mathematics", setOf(LevelBand.PRIMARY, LevelBand.SECONDARY, LevelBand.PRE_U)),
        Subject("science", "Science", setOf(LevelBand.PRIMARY, LevelBand.SECONDARY)),
        Subject("english", "English Language", setOf(LevelBand.PRIMARY, LevelBand.SECONDARY)),
        Subject("geography", "Geography", setOf(LevelBand.SECONDARY)),
        Subject("physics", "Physics", setOf(LevelBand.SECONDARY, LevelBand.PRE_U)),
        Subject("economics", "Economics", setOf(LevelBand.PRE_U)),
        Subject("gp", "General Paper", setOf(LevelBand.PRE_U)),
    )

    private val topics: List<SyllabusTopic> = listOf(
        t("P3-MA-WN", "Whole numbers to 10 000", "math", Level.P3, "Number and Algebra", "Compare and order numbers to 10 000", "Add and subtract numbers up to 4 digits"),
        t("P4-MA-FR", "Fractions", "math", Level.P4, "Number and Algebra", "Add and subtract related fractions", "Find a fraction of a set", "Express an improper fraction as a mixed number"),
        t("P4-MA-AN", "Angles", "math", Level.P4, "Measurement and Geometry", "Measure angles in degrees using a protractor", "Draw an angle of a given size"),
        t("P5-SC-WC", "Water cycle", "science", Level.P5, "Cycles", "Describe how water changes state in the water cycle", "Explain the importance of the water cycle"),
        t("P6-SC-FW", "Food chains and food webs", "science", Level.P6, "Interactions", "Trace energy flow through a food web", "Predict the effect of removing an organism from a food web"),
        t("P5-EL-NW", "Narrative writing", "english", Level.P5, "Writing and Representing", "Plan a narrative with a clear beginning, problem and resolution", "Use descriptive language to show feelings"),
        t("S1-MA-AE", "Algebraic expressions", "math", Level.SEC1, "Number and Algebra", "Use letters to represent numbers", "Simplify linear expressions", "Evaluate expressions by substitution"),
        t("S1-MA-RP", "Ratio and proportion", "math", Level.SEC1, "Number and Algebra", "Express a ratio in its simplest form", "Solve problems involving direct proportion"),
        t("S2-MA-LE", "Linear equations and graphs", "math", Level.SEC2, "Number and Algebra", "Solve linear equations in one variable", "Interpret the gradient of a straight-line graph"),
        t("S1-SC-CE", "Cells", "science", Level.SEC1, "Systems", "Identify the parts of plant and animal cells", "Relate cell structures to their functions"),
        t("S2-SC-EL", "Electrical systems", "science", Level.SEC2, "Systems", "Draw circuit diagrams using standard symbols", "Explain the effect of resistance on current"),
        t("S1-EL-AW", "Argumentative writing", "english", Level.SEC1, "Writing and Representing", "State a clear position with reasons", "Support claims with relevant evidence"),
        t("S3-GE-TO", "Tourism", "geography", Level.SEC3, "Human Geography", "Explain factors driving the growth of tourism", "Evaluate the impacts of tourism on host communities"),
        t("S3-PH-KI", "Kinematics", "physics", Level.SEC3, "Newtonian Mechanics", "Distinguish between speed, velocity and acceleration", "Interpret distance–time and speed–time graphs"),
        t("J1-EC-DS", "Demand, supply and market equilibrium", "economics", Level.JC1, "Microeconomics", "Explain the determinants of demand and supply", "Analyse changes in market equilibrium"),
        t("J1-MA-FN", "Functions", "math", Level.JC1, "Functions and Graphs", "Determine the range of a function", "Find composite and inverse functions"),
        t("J1-GP-AR", "Argument and evaluation", "gp", Level.JC1, "Paper 1 Essay", "Construct a balanced argument", "Evaluate the strength of evidence"),
    )

    override fun subjects(band: LevelBand) = subjects.filter { band in it.bands }
    override fun topics(level: Level, subjectId: String) = topics.filter { it.level == level && it.subjectId == subjectId }
    override fun topic(id: String) = topics.firstOrNull { it.id == id }
    override fun subject(id: String) = subjects.firstOrNull { it.id == id }
    fun allTopics(): List<SyllabusTopic> = topics
    fun levelsWithTopics(subjectId: String): List<Level> = topics.filter { it.subjectId == subjectId }.map { it.level }.distinct().sorted()

    private fun t(code: String, title: String, subject: String, level: Level, strand: String, vararg outcomes: String) = SyllabusTopic(
        id = code.lowercase(), code = code, title = title, subjectId = subject, level = level, strand = strand,
        outcomes = outcomes.mapIndexed { i, o -> LearningOutcome("$code.${i + 1}", o) },
    )
}
