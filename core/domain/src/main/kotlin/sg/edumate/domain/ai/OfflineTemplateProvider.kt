package sg.edumate.domain.ai

import sg.edumate.domain.model.*

/**
 * Deterministic, on-device fallback used when offline (and in demos/tests). It produces a
 * well-structured *skeleton* from the syllabus outcomes. Output is clearly marked as a template and
 * answer keys say "Teacher to confirm"; it never invents facts or citations.
 */
class OfflineTemplateProvider : LlmProvider {
    override val id: String = ID

    override suspend fun lessonPlan(request: LessonPlanRequest): LessonPlanDraft {
        val t = request.topic
        val outcomes = t.outcomes.ifEmpty { listOf(LearningOutcome(t.code, t.title)) }
        val easier = request.refinement == Refinement.MAKE_EASIER
        val extension = request.refinement == Refinement.ADD_EXTENSION
        val ctx = SG_CONTEXTS[(t.title.length) % SG_CONTEXTS.size]

        val blocks = buildList {
            add(block("hook", BlockType.EXPLAIN, "Hook: $ctx", "Open with a short discussion linking ${t.title.lowercase()} to $ctx. Ask: \"Where have you seen this before?\"", 5))
            add(block("prior", BlockType.POLL, "Prior knowledge check", "Quick poll on the prerequisite idea. Use results to decide pace.", 3, cfu = true,
                options = listOf("I can explain it", "I sort of know", "Not yet")))
            add(block("teach", BlockType.EXPLAIN, "Teach: ${outcomes.first().description}",
                if (easier) "Model step by step with a concrete example, then a second near-identical example done together."
                else "Explain the key idea with one worked example; think aloud about common errors.", 10))
            add(block("cfu1", BlockType.MCQ, "Concept check", "One MCQ hinge question targeting the most common misconception. Reteach if under 70% correct.", 3, cfu = true,
                options = listOf("Option A", "Option B", "Option C", "Option D")))
            add(block("practice-s", BlockType.SHORT_ANSWER, "Guided practice (Support)", "Scaffolded questions with worked example and hints.", 12, tier = ScaffoldTier.SUPPORT))
            add(block("practice-c", BlockType.SHORT_ANSWER, "Practice (Core)", "Independent practice with mixed difficulty.", 12, tier = ScaffoldTier.CORE))
            add(block("practice-e", BlockType.SHORT_ANSWER, "Practice (Extension)", "Non-routine and open-ended problems; justify reasoning.", 12, tier = ScaffoldTier.EXTENSION))
            if (request.refinement == Refinement.MORE_INTERACTIVE || extension)
                add(block("group", BlockType.GROUP_TASK, "Group challenge", "Mixed-ability groups solve a $ctx problem and present on a shared board.", 8))
            add(block("exit", BlockType.EXIT_TICKET, "Exit ticket", "Two quick questions plus a confidence rating (1–5).", 5, cfu = true))
            add(block("reflect", BlockType.REFLECTION, "Reflection", "\"One thing I can now do… one thing I'm still unsure about…\"", 2))
        }
        val scaled = scaleToDuration(blocks, if (request.refinement == Refinement.SHORTER) request.durationMin * 3 / 4 else request.durationMin)

        return LessonPlanDraft(
            title = "${t.title} (${request.level.label})",
            objectives = outcomes.map { "Students will be able to ${it.description.replaceFirstChar { c -> c.lowercase() }}" },
            priorKnowledge = listOf("Prerequisite concepts for ${t.strand} from the previous level (teacher to confirm)"),
            successCriteria = outcomes.map { "I can ${it.description.replaceFirstChar { c -> c.lowercase() }}" } +
                if (extension) listOf("I can apply this to an unfamiliar problem and explain my reasoning") else emptyList(),
            flow = scaled,
            differentiation = listOf(
                DifferentiatedActivity(ScaffoldTier.SUPPORT, "Concrete examples, worked example on the worksheet, word bank and sentence starters; teacher check-in after Q2."),
                DifferentiatedActivity(ScaffoldTier.CORE, "Standard practice set; hints available on request; peer check before submission."),
                DifferentiatedActivity(ScaffoldTier.EXTENSION, "Fewer routine items; open-ended and real-world $ctx problems requiring justification."),
            ).filter { tier -> request.streams.isEmpty() || request.streams.any { it.tier == tier.tier } },
            aflChecks = listOf("Prior knowledge poll", "Hinge MCQ after teaching", "Exit ticket with confidence rating"),
            competencies = listOf(Competency21.CRITICAL_ADAPTIVE_INVENTIVE, Competency21.COMMUNICATION_COLLABORATION_INFO),
            outcomesCited = outcomes.map { it.code },
            citations = emptyList(),
        )
    }

    override suspend fun worksheet(request: WorksheetRequest): WorksheetDraft {
        val spec = request.spec
        val outcomes = spec.topic.outcomes.ifEmpty { listOf(LearningOutcome(spec.topic.code, spec.topic.title)) }
        val types = spec.questionTypes.toList()
        val versions = spec.streams.map { stream ->
            val difficulties = spec.difficultyMix.forTier(stream.tier).allocate(spec.questionCount)
            val questions = difficulties.mapIndexed { i, d ->
                val outcome = outcomes[i % outcomes.size]
                val type = types[i % types.size]
                WorksheetQuestion(
                    id = "q${i + 1}",
                    number = i + 1,
                    type = type,
                    difficulty = d,
                    prompt = promptFor(type, d, outcome, spec.topic.title, i),
                    options = if (type == QuestionType.MCQ) listOf("A. …", "B. …", "C. …", "D. …") else emptyList(),
                    scaffolds = scaffoldsFor(stream.tier, type, spec.topic.title),
                    marks = when (d) { Difficulty.FOUNDATIONAL -> 1; Difficulty.INTERMEDIATE -> 2; Difficulty.ADVANCED -> 3 },
                    outcomeCode = outcome.code,
                )
            } + if (stream.tier == ScaffoldTier.EXTENSION) listOf(
                WorksheetQuestion(
                    id = "challenge", number = spec.questionCount + 1, type = QuestionType.OPEN_ENDED, difficulty = Difficulty.ADVANCED,
                    prompt = "Challenge: Design your own problem about ${spec.topic.title.lowercase()} set in ${SG_CONTEXTS[1]}. Solve it and explain why your method works.",
                    scaffolds = listOf(Scaffold(ScaffoldKind.CHALLENGE, "Consider what changes if one quantity is doubled.")),
                    marks = 4, outcomeCode = outcomes.last().code, isChallenge = true,
                )
            ) else emptyList()
            WorksheetVersion(
                stream = stream,
                questions = questions,
                answerKey = questions.map { q ->
                    AnswerKeyItem(q.id, "Teacher to confirm model answer.", listOf("Award ${q.marks} mark(s): correct method and answer for ${q.outcomeCode}"))
                },
            )
        }
        return WorksheetDraft(
            title = "${spec.topic.title}: Practice",
            versions = versions,
            misconceptions = listOf(
                Misconception("Students may apply a procedure without checking whether it fits the context.", "Ask students to estimate first and compare with their answer."),
            ),
            outcomesCited = outcomes.map { it.code },
        )
    }

    override suspend fun feedback(request: FeedbackRequest): FeedbackDraft = FeedbackDraft(
        feedback = "Thanks for your answer. Check it against: ${request.markingPoints.firstOrNull() ?: "the success criteria"}. What is one step you could explain more clearly?",
        suggestedMarks = null,
        misconceptionTag = null,
    )

    private fun block(id: String, type: BlockType, title: String, instr: String, min: Int, tier: ScaffoldTier? = null,
                      cfu: Boolean = false, options: List<String> = emptyList()) =
        LessonBlock(id, type, title, instr, min, tier, options, cfu)

    /** Differentiated blocks run in parallel, so only count one of them toward the timeline. */
    private fun scaleToDuration(blocks: List<LessonBlock>, target: Int): List<LessonBlock> {
        val parallelExtra = blocks.filter { it.tier != null }.drop(1).sumOf { it.durationMin }
        val current = blocks.sumOf { it.durationMin } - parallelExtra
        if (current == 0) return blocks
        val factor = target.toDouble() / current
        return blocks.map { it.copy(durationMin = maxOf(1, Math.round(it.durationMin * factor).toInt())) }
    }

    private fun promptFor(type: QuestionType, d: Difficulty, o: LearningOutcome, topic: String, i: Int): String {
        val ctx = SG_CONTEXTS[i % SG_CONTEXTS.size]
        val verb = when (d) { Difficulty.FOUNDATIONAL -> "State"; Difficulty.INTERMEDIATE -> "Explain"; Difficulty.ADVANCED -> "Evaluate" }
        return when (type) {
            QuestionType.MCQ -> "Which of the following best shows: ${o.description.lowercase()}?"
            QuestionType.FILL_IN_BLANK -> "Complete the statement about $topic: ________ ."
            QuestionType.OPEN_ENDED -> "$verb how ${o.description.lowercase()} could be used at $ctx."
            QuestionType.STRUCTURED -> "(a) $verb the key idea of ${o.description.lowercase()}. (b) Apply it to a situation at $ctx."
            QuestionType.SHORT_ANSWER -> "$verb: ${o.description}."
        }
    }

    private fun scaffoldsFor(tier: ScaffoldTier, type: QuestionType, topic: String): List<Scaffold> = when (tier) {
        ScaffoldTier.SUPPORT -> listOfNotNull(
            Scaffold(ScaffoldKind.WORKED_EXAMPLE, "See the worked example in the box above before you start."),
            Scaffold(ScaffoldKind.HINT, "Underline the key words in the question first."),
            if (type == QuestionType.OPEN_ENDED || type == QuestionType.STRUCTURED) Scaffold(ScaffoldKind.SENTENCE_STARTER, "I think … because …") else null,
            Scaffold(ScaffoldKind.WORD_BANK, "Key terms for $topic (teacher to fill)"),
            if (type == QuestionType.STRUCTURED) Scaffold(ScaffoldKind.GRAPHIC_ORGANISER, "Use the Know / Need / Plan table.") else null,
        )
        ScaffoldTier.CORE -> listOf(Scaffold(ScaffoldKind.HINT, "Show your working clearly."))
        ScaffoldTier.EXTENSION -> emptyList()
    }

    companion object {
        const val ID = "offline-template"
        val SG_CONTEXTS = listOf("a hawker centre", "an MRT station", "the HDB void deck", "Gardens by the Bay", "a CCA training session", "the school canteen")
    }
}
