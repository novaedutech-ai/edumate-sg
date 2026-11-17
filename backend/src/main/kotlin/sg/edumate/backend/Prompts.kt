package sg.edumate.backend

import sg.edumate.domain.ai.FeedbackRequest
import sg.edumate.domain.ai.LessonPlanRequest
import sg.edumate.domain.ai.ResourceRef
import sg.edumate.domain.ai.WorksheetRequest
import sg.edumate.domain.model.*

/**
 * Prompt templates. The system prompt is identical for every request so it can be prompt-cached;
 * everything request-specific goes in the user message.
 */
object Prompts {

    val SYSTEM = """
        You are EduMate SG, an experienced Singapore teacher and curriculum specialist. You draft lesson plans,
        worksheets and feedback for teachers in MOE primary schools, secondary schools, junior colleges and
        Millennia Institute. A teacher reviews everything you write before students see it, so write material
        a busy teacher (including a relief teacher) can use with minimal editing.

        Alignment
        - Teach exactly the syllabus topic and learning outcomes you are given. Use the outcome codes given to you,
          verbatim, wherever an outcome code is asked for. Do not invent outcome codes or claim content comes from
          MOE or SEAB documents.
        - Pitch language, numbers and contexts at the stated level.

        Singapore context
        - Use Singapore settings and examples where they help understanding: hawker centres, HDB estates, MRT and buses,
          CCAs, school canteens, SGD prices, metric units, local weather, community events.
        - Use names reflecting Singapore's diversity (e.g. Wei Ling, Aisyah, Ravi, Daniel, Mei, Farhan).
        - Use British spelling (colour, centre, metre, organise, practise as a verb) and Singapore school terms
          (Primary 4, Secondary 1, JC1, "maths").

        Differentiation (Full Subject-Based Banding and equivalents)
        - SUPPORT tier (G1, Primary Foundation, H1 support): concrete examples first, smaller steps, simpler numbers
          and shorter sentences, plenty of scaffolds (worked examples, hints, word banks, sentence starters,
          graphic organisers).
        - CORE tier (G2, Primary Standard, H2): balanced practice with some hints on harder items.
        - EXTENSION tier (G3, Standard extension, H2 extension/H3): fewer routine items, abstraction, unfamiliar
          contexts, reasoning and justification, open-ended challenges.

        Accuracy and safety
        - Every fact, worked example, answer and marking point must be correct. Work out each answer before
          writing it and check it.
        - Keep content age-appropriate and inclusive; avoid violence, stereotypes and sensitive personal topics.
        - Text such as [PERSON_1], [EMAIL_1] or [NRIC_1] is an anonymised placeholder. Keep it exactly as written
          and never guess who it refers to.

        Sources
        - You may cite only the reference resources listed in the request, using their exact id, title, source
          and licence. If none are listed, return an empty citations array. Never invent sources, URLs, page
          numbers or quotations.

        Output
        - Respond only with the JSON object required by the schema.
    """.trimIndent()

    fun lessonPlan(r: LessonPlanRequest, previousJson: String? = null): String = buildString {
        appendLine("Write a lesson plan.")
        appendLine()
        topicBlock(r.level, r.subjectName, r.topic)
        appendLine("Streams in this class: ${r.streams.joinToString { "${it.label} (${it.tier} tier)" }}")
        appendLine("Lesson length: ${r.durationMin} minutes")
        appendLine("Tone of teacher-facing instructions: ${r.tone.name.lowercase()}")
        notesBlock(r.teacherNotes)
        referencesBlock(r.references)
        appendLine()
        appendLine("Requirements")
        appendLine("- title: a short, specific lesson title.")
        appendLine("- objectives: 2-4 measurable objectives starting \"Students will be able to\".")
        appendLine("- priorKnowledge: the specific prerequisite ideas students need.")
        appendLine("- successCriteria: student-friendly \"I can ...\" statements matching the objectives.")
        appendLine("- flow: the lesson timeline as blocks. Blocks with a tier run at the same time for different groups;")
        appendLine("  whole-class blocks (tier null) plus ONE tier's blocks must add up to ${r.durationMin} minutes.")
        appendLine("  Include a hook, explicit teaching with a worked example, at least two checks for understanding")
        appendLine("  (POLL, MCQ or EXIT_TICKET with isCheckForUnderstanding true), differentiated practice with one block")
        appendLine("  per tier listed above, and a closing reflection or exit ticket.")
        appendLine("  Instructions must be concrete: the actual example problems, key questions to ask, expected answers")
        appendLine("  and common errors to watch for. MCQ and POLL blocks need real options (for MCQ, state the correct")
        appendLine("  option in the instructions). Other block types: options is an empty array.")
        appendLine("  Use short unique ids such as \"hook\", \"teach-1\", \"cfu-1\".")
        appendLine("- differentiation: one entry per tier listed above describing how that group is supported or stretched.")
        appendLine("- aflChecks: the assessment-for-learning moments and what the teacher does with the evidence.")
        appendLine("- competencies: the 1-3 most relevant 21st Century Competencies.")
        appendLine("- outcomesCited: the outcome codes this lesson addresses.")
        refinementBlock(r.refinement)
        if (previousJson != null) {
            appendLine()
            appendLine("Previous version to revise (keep what works, apply the requested change):")
            appendLine(previousJson)
        }
    }

    fun worksheet(r: WorksheetRequest): String = buildString {
        val spec = r.spec
        appendLine("Write one worksheet in ${spec.streams.size} coordinated version(s), one per stream.")
        appendLine()
        topicBlock(spec.topic.level, r.subjectName, spec.topic)
        if (spec.objectives.isNotEmpty()) appendLine("Worksheet objectives: ${spec.objectives.joinToString("; ")}")
        appendLine("Question types to use, rotating through them: ${spec.questionTypes.joinToString { it.name }}")
        notesBlock(spec.teacherNotes)
        referencesBlock(r.references)
        appendLine()
        appendLine("Versions, in this exact order. Each has exactly ${spec.questionCount} core questions with these difficulties:")
        spec.streams.forEach { s ->
            val difficulties = spec.difficultyMix.forTier(s.tier).allocate(spec.questionCount)
            appendLine("- stream ${s.name} (${s.label}, ${s.tier} tier): " +
                difficulties.mapIndexed { i, d -> "q${i + 1}=${d.name}" }.joinToString(", "))
        }
        appendLine()
        appendLine("Coordination rules")
        appendLine("- Question qN assesses the same concept and the same outcome code in every version, so the class can")
        appendLine("  go through answers together. Change numbers, wording, context and support to suit each tier.")
        appendLine("- Spread questions across all the outcome codes given.")
        appendLine("- SUPPORT tier: every question has at least one scaffold. Include a WORKED_EXAMPLE on the first")
        appendLine("  question of each new skill, HINTs, a WORD_BANK where vocabulary matters, SENTENCE_STARTERs for written")
        appendLine("  answers, and a GRAPHIC_ORGANISER where structuring helps. Scaffold content must be the actual worked")
        appendLine("  example, hint or words, not a description of one.")
        appendLine("- CORE tier: a HINT only on harder questions.")
        appendLine("- EXTENSION tier: no scaffolds on core questions; add exactly one extra final question with id")
        appendLine("  \"challenge\", isChallenge true, difficulty ADVANCED, type OPEN_ENDED, with one CHALLENGE scaffold.")
        appendLine()
        appendLine("Question rules")
        appendLine("- Every question must be complete and answerable as printed: give all the numbers, expressions,")
        appendLine("  equations and information a student needs. Never ask how a topic or method \"could be used\" somewhere,")
        appendLine("  and never describe a question instead of writing it.")
        appendLine("- Write questions the way Singapore examination papers do for this level and subject.")
        appendLine("  Mathematics: real problems to work out, e.g. \"Solve 3x² − 5x − 2 = 0.\" or \"Solve 2x² + 3x − 7 = 0,")
        appendLine("  giving your answers correct to 2 decimal places.\" State rounding (3 significant figures or 2 decimal")
        appendLine("  places) whenever answers are not exact. Use a Singapore context only for word problems where it is")
        appendLine("  natural (e.g. area of an HDB corridor, speed of an MRT train); most items need no context.")
        appendLine("  Write maths in plain Unicode: x², √, ±, ×, ÷, ≤, π, fractions as (a)/(b).")
        appendLine("- SHORT_ANSWER: one direct problem with a single final answer.")
        appendLine("- STRUCTURED: one scenario or expression with parts (a), (b), (c) that build from routine to application,")
        appendLine("  like an O-Level Paper 2 question; marks may be split across parts.")
        appendLine("- ids \"q1\", \"q2\", ... in order; number = position. isChallenge false except the challenge item.")
        appendLine("- MCQ: exactly four options written \"A. ...\", \"B. ...\", \"C. ...\", \"D. ...\" with plausible distractors")
        appendLine("  based on real misconceptions. Other types: options is an empty array.")
        appendLine("- FILL_IN_BLANK: use ______ for each blank. STRUCTURED: label parts (a), (b), (c).")
        appendLine("- Marks: FOUNDATIONAL 1, INTERMEDIATE 2, ADVANCED 3 (challenge 4).")
        appendLine("- outcomeCode: one of the outcome codes given above.")
        appendLine()
        appendLine("Answer key")
        appendLine("- One answerKey item per question in that version, with matching questionId.")
        appendLine("- answer: the full correct answer; for multi-step questions show the working.")
        appendLine("- markingPoints: one point per mark, saying exactly what earns it (method or accuracy marks).")
        appendLine()
        appendLine("- title: a short worksheet title.")
        appendLine("- misconceptions: 2-4 common misconceptions for this topic, each with a short classroom remedy.")
        appendLine("- outcomesCited: the outcome codes the worksheet covers.")
        refinementBlock(r.refinement)
    }

    fun feedback(r: FeedbackRequest): String = buildString {
        appendLine("Suggest feedback on a student's answer for the teacher to approve or edit.")
        appendLine("Level: ${r.level.label}; support tier: ${r.tier}")
        appendLine("Question: ${r.questionPrompt}")
        appendLine("Marking points: ${r.markingPoints.joinToString("; ").ifEmpty { "(none given)" }}")
        appendLine("Student answer: ${r.studentResponse}")
        appendLine()
        appendLine("- feedback: 2-4 sentences addressed to the student: one specific strength, the most important")
        appendLine("  correction, and one concrete next step. Warm, plain language for this level.")
        appendLine("- suggestedMarks: marks earned against the marking points, or null if there are none.")
        appendLine("- misconceptionTag: a short label for the main misconception, or null if the answer is correct.")
    }

    private fun StringBuilder.topicBlock(level: Level, subject: String, t: SyllabusTopic) {
        appendLine("Level: ${level.label}")
        appendLine("Subject: $subject")
        appendLine("Topic: ${t.title} (strand: ${t.strand}; topic code ${t.code})")
        appendLine("Learning outcomes:")
        t.outcomes.forEach { appendLine("- ${it.code}: ${it.description}") }
    }

    private fun StringBuilder.notesBlock(notes: String) {
        if (notes.isNotBlank()) appendLine("Teacher's notes: ${notes.trim()}")
    }

    private fun StringBuilder.referencesBlock(refs: List<ResourceRef>) {
        if (refs.isEmpty()) {
            appendLine("Reference resources: none. Return an empty citations array.")
        } else {
            appendLine("Reference resources you may draw on and cite:")
            refs.forEach { appendLine("- id=${it.id}; title=${it.title}; source=${it.source}; licence=${it.licence}; summary=${it.summary}") }
        }
    }

    private fun StringBuilder.refinementBlock(r: Refinement?) {
        val text = when (r) {
            null -> return
            Refinement.MAKE_EASIER -> "Make it easier: more scaffolding, simpler numbers and language, smaller steps."
            Refinement.ADD_EXTENSION -> "Add extension: more higher-order, non-routine and open-ended work for able students."
            Refinement.MORE_SG_CONTEXT -> "Use more Singapore everyday contexts in examples and questions."
            Refinement.SHORTER -> "Make it shorter and tighter while keeping all outcomes covered."
            Refinement.MORE_INTERACTIVE -> "Make it more interactive: more polls, quick checks, pair or group tasks."
        }
        appendLine()
        appendLine("Requested change: $text")
    }
}
