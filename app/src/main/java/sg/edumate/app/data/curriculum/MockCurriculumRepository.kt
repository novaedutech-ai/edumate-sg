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
        Subject("chemistry", "Chemistry", setOf(LevelBand.SECONDARY, LevelBand.PRE_U)),
        Subject("biology", "Biology", setOf(LevelBand.SECONDARY, LevelBand.PRE_U)),
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
        // --- Mathematics: every level ---
        t("P1-MA-NB", "Numbers to 100", "math", Level.P1, "Number and Algebra", "Count, read and write numbers to 100", "Compare and order numbers to 100"),
        t("P1-MA-AS", "Addition and subtraction within 100", "math", Level.P1, "Number and Algebra", "Add and subtract numbers within 100", "Solve 1-step word problems on addition and subtraction"),
        t("P2-MA-MD", "Multiplication and division", "math", Level.P2, "Number and Algebra", "Recall multiplication facts of 2, 3, 4, 5 and 10", "Solve 1-step word problems on multiplication and division"),
        t("P2-MA-MO", "Money", "math", Level.P2, "Measurement and Geometry", "Read and write money in dollars and cents", "Add and subtract money in dollars or in cents"),
        t("P3-MA-LM", "Length, mass and volume", "math", Level.P3, "Measurement and Geometry", "Measure length in km and m, mass in kg and g, volume in l and ml", "Convert between units of measurement"),
        t("P4-MA-DE", "Decimals", "math", Level.P4, "Number and Algebra", "Read and write decimals up to 3 decimal places", "Add and subtract decimals"),
        t("P5-MA-PE", "Percentage", "math", Level.P5, "Number and Algebra", "Express a part of a whole as a percentage", "Find a percentage of a quantity"),
        t("P5-MA-AT", "Area of triangle", "math", Level.P5, "Measurement and Geometry", "Identify the base and height of a triangle", "Find the area of a triangle"),
        t("P6-MA-RA", "Ratio", "math", Level.P6, "Number and Algebra", "Find the ratio of two or three quantities", "Solve word problems involving ratio"),
        t("P6-MA-AL", "Algebra", "math", Level.P6, "Number and Algebra", "Use a letter to represent an unknown number", "Simplify and evaluate simple linear expressions"),
        t("P6-MA-CI", "Circles", "math", Level.P6, "Measurement and Geometry", "Find the circumference of a circle", "Find the area of a circle and semicircle"),
        t("S1-MA-PH", "Primes, HCF and LCM", "math", Level.SEC1, "Number and Algebra", "Express a number as a product of prime factors", "Find the HCF and LCM of whole numbers"),
        t("S2-MA-PY", "Pythagoras' theorem", "math", Level.SEC2, "Geometry and Measurement", "Use Pythagoras' theorem to find unknown sides of right-angled triangles", "Determine whether a triangle is right-angled"),
        t("S3-MA-QE", "Quadratic equations", "math", Level.SEC3, "Number and Algebra", "Solve quadratic equations by factorisation", "Solve quadratic equations using the formula"),
        t("S3-MA-TR", "Trigonometric ratios", "math", Level.SEC3, "Geometry and Measurement", "Use sine, cosine and tangent in right-angled triangles", "Solve problems involving angles of elevation and depression"),
        t("S4-MA-PR", "Probability of combined events", "math", Level.SEC4, "Statistics and Probability", "Use possibility diagrams and tree diagrams", "Find probabilities of combined events"),
        t("S5-MA-ST", "Statistical diagrams", "math", Level.SEC5, "Statistics and Probability", "Interpret dot diagrams and stem-and-leaf diagrams", "Compare data sets using mean, median and range"),
        t("J1-MA-DF", "Differentiation", "math", Level.JC1, "Calculus", "Differentiate standard functions using the chain, product and quotient rules", "Find stationary points and determine their nature"),
        t("J2-MA-IN", "Integration techniques", "math", Level.JC2, "Calculus", "Integrate by substitution and by parts", "Find areas bounded by curves"),
        t("J2-MA-HT", "Hypothesis testing", "math", Level.JC2, "Probability and Statistics", "Formulate null and alternative hypotheses", "Carry out a hypothesis test for a population mean"),
        // --- Science ---
        t("P3-SC-DL", "Diversity of living things", "science", Level.P3, "Diversity", "Classify living things into groups based on characteristics", "Describe the characteristics of living things"),
        t("P3-SC-MA", "Materials", "science", Level.P3, "Diversity", "Relate the properties of materials to their uses", "Compare materials by strength, flexibility and waterproofness"),
        t("P4-SC-HE", "Heat", "science", Level.P4, "Energy", "Differentiate between heat and temperature", "Describe good and poor conductors of heat"),
        t("P4-SC-MT", "Matter", "science", Level.P4, "Diversity", "Describe the three states of matter", "Show that matter has mass and occupies space"),
        t("P5-SC-ES", "Electrical systems", "science", Level.P5, "Systems", "Identify the parts of a closed circuit", "Compare circuits with bulbs in series and in parallel"),
        t("P5-SC-RP", "Reproduction in plants", "science", Level.P5, "Cycles", "Describe pollination, fertilisation and seed dispersal", "Explain the importance of seed dispersal"),
        t("P6-SC-FO", "Forces", "science", Level.P6, "Interactions", "Describe the effects of a force", "Identify frictional, gravitational and elastic spring forces"),
        t("P6-SC-EC", "Energy conversion", "science", Level.P6, "Energy", "Describe energy conversions in everyday devices", "Explain the importance of conserving energy"),
        t("S1-SC-ST", "Separation techniques", "science", Level.SEC1, "Diversity", "Choose a suitable method to separate a mixture", "Explain filtration, evaporation and distillation"),
        t("S2-SC-EC", "Ecology", "science", Level.SEC2, "Interactions", "Describe food chains, food webs and energy flow", "Explain the impact of human activities on ecosystems"),
        t("S3-CH-AS", "Atomic structure", "chemistry", Level.SEC3, "Matter: Structures and Properties", "Describe the structure of an atom", "Deduce the numbers of protons, neutrons and electrons"),
        t("S3-CH-AB", "Acids and bases", "chemistry", Level.SEC3, "Chemical Reactions", "Describe the properties of acids and bases", "Use the pH scale and indicators"),
        t("S4-CH-MO", "The mole concept", "chemistry", Level.SEC4, "Chemical Reactions", "Calculate the number of moles from mass", "Use mole ratios in chemical equations"),
        t("S3-BI-TH", "Transport in humans", "biology", Level.SEC3, "Maintenance and Regulation of Life Processes", "Describe the structure and function of the heart", "Explain how blood vessels are adapted to their functions"),
        t("S4-BI-IN", "Inheritance", "biology", Level.SEC4, "Continuity of Life", "Use genetic diagrams for monohybrid crosses", "Distinguish between dominant and recessive alleles"),
        t("S4-PH-EM", "Electromagnetism", "physics", Level.SEC4, "Electricity and Magnetism", "Describe the magnetic field around a current-carrying wire", "Explain how a d.c. motor works"),
        t("J1-PH-DY", "Dynamics", "physics", Level.JC1, "Newtonian Mechanics", "Apply Newton's laws of motion", "Use the principle of conservation of momentum"),
        t("J1-CH-CB", "Chemical bonding", "chemistry", Level.JC1, "Structure and Properties", "Describe ionic, covalent and metallic bonding", "Predict molecular shapes using VSEPR theory"),
        t("J1-BI-CS", "Cell structure and function", "biology", Level.JC1, "Cells and Biomolecules", "Relate organelle structure to function", "Compare prokaryotic and eukaryotic cells"),
        // --- English Language ---
        t("P1-EL-RD", "Reading aloud with expression", "english", Level.P1, "Listening and Reading", "Read simple texts aloud with accuracy", "Use punctuation to read with expression"),
        t("P2-EL-SN", "Writing complete sentences", "english", Level.P2, "Writing and Representing", "Write sentences with a subject and a verb", "Use capital letters and full stops correctly"),
        t("P3-EL-PS", "Writing a picture composition", "english", Level.P3, "Writing and Representing", "Sequence events shown in pictures", "Use time connectors to link ideas"),
        t("P4-EL-TE", "Past and present tenses", "english", Level.P4, "Grammar", "Use the simple past and present tenses correctly", "Keep tenses consistent in a paragraph"),
        t("P6-EL-CO", "Comprehension: inferential questions", "english", Level.P6, "Reading and Viewing", "Make inferences using clues in a text", "Support answers with evidence from the text"),
        t("S2-EL-NA", "Narrative writing", "english", Level.SEC2, "Writing and Representing", "Build tension and a turning point in a narrative", "Use dialogue to reveal character"),
        t("S3-EL-SW", "Situational writing", "english", Level.SEC3, "Writing and Representing", "Write for a given purpose, audience and context", "Use an appropriate tone and format"),
        t("S4-EL-SU", "Summary writing", "english", Level.SEC4, "Reading and Viewing", "Identify relevant points from a text", "Paraphrase points concisely in continuous writing"),
        t("S5-EL-DW", "Discursive writing", "english", Level.SEC5, "Writing and Representing", "Present balanced arguments on an issue", "Organise ideas with clear topic sentences"),
        // --- Humanities / Pre-U ---
        t("S1-GE-WR", "Water as a resource", "geography", Level.SEC1, "Physical Geography", "Describe the water cycle and Singapore's water sources", "Evaluate strategies for managing water"),
        t("S2-GE-HO", "Housing", "geography", Level.SEC2, "Human Geography", "Explain why housing demand changes", "Evaluate Singapore's approach to public housing"),
        t("S4-GE-CC", "Climate change", "geography", Level.SEC4, "Physical Geography", "Explain the enhanced greenhouse effect", "Evaluate responses to climate change"),
        t("J2-EC-MF", "Market failure", "economics", Level.JC2, "Microeconomics", "Explain externalities and public goods", "Evaluate government measures to correct market failure"),
        t("J2-GP-CO", "Comprehension and application", "gp", Level.JC2, "Paper 2", "Identify the writer's argument and techniques", "Apply the writer's views to Singapore's context"),

    )

    override fun subjects(band: LevelBand) = subjects.filter { band in it.bands }
    /** Only subjects that have at least one topic at this level, so the picker never dead-ends. */
    fun subjectsWithTopics(level: Level) = subjects.filter { s -> topics.any { it.level == level && it.subjectId == s.id } }
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
