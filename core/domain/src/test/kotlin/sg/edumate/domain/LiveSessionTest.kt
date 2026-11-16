package sg.edumate.domain

import sg.edumate.domain.ai.LocalContentModerator
import sg.edumate.domain.live.*
import sg.edumate.domain.model.*
import kotlin.test.*

class LiveSessionTest {
    private val reducer = LiveSessionReducer(LocalContentModerator(), inactivityMs = 60_000)
    private val empty = LiveSessionState("s1", "ABC123", "Fractions", 0)

    private fun joined(n: Int): LiveSessionState = (1..n).fold(empty) { s, i ->
        reducer.reduce(s, LiveEvent.Joined("p$i", "Student $i", if (i % 3 == 0) Stream.G3 else Stream.G1, 0))
    }

    @Test fun `caps at 45 students`() { assertEquals(45, joined(50).students.size) }

    @Test fun `flags low confidence, repeated incorrect and help`() {
        var s = joined(3)
        s = reducer.reduce(s, LiveEvent.ResponseSubmitted("p1", "b", "half", 1, false, 1000))
        s = reducer.reduce(s, LiveEvent.ResponseSubmitted("p2", "b", "x", 4, false, 1000))
        s = reducer.reduce(s, LiveEvent.ResponseSubmitted("p2", "b", "y", 4, false, 2000))
        s = reducer.reduce(s, LiveEvent.HelpRequested("p3", 3000))
        assertEquals(setOf(FlagReason.LOW_CONFIDENCE), s.students["p1"]!!.flags)
        assertEquals(setOf(FlagReason.REPEATED_INCORRECT), s.students["p2"]!!.flags)
        assertEquals(StudentStatus.STUCK, s.students["p3"]!!.status)
        assertTrue(FlagReason.HELP_REQUESTED in s.students["p3"]!!.flags)
        assertEquals(StudentStatus.WORKING, s.students["p1"]!!.status)
    }

    @Test fun `inactivity flagged on tick but not for done students`() {
        var s = joined(2)
        s = reducer.reduce(s, LiveEvent.StatusChanged("p2", StudentStatus.DONE, 0))
        s = reducer.tick(s, 61_000)
        assertTrue(FlagReason.INACTIVE in s.students["p1"]!!.flags)
        assertFalse(s.students["p2"]!!.flagged)
    }

    @Test fun `pastoral concern is sticky and survives clearFlags`() {
        var s = joined(1)
        s = reducer.reduce(s, LiveEvent.ResponseSubmitted("p1", "b", "I want to die this is hard", 1, null, 10))
        s = reducer.clearFlags(s, listOf("p1"))
        assertEquals(setOf(FlagReason.PASTORAL_CONCERN), s.students["p1"]!!.flags)
    }

    @Test fun `blocked student text is hidden from feed and word cloud`() {
        var s = joined(1)
        s = reducer.reduce(s, LiveEvent.ResponseSubmitted("p1", "b", "this is shit", 3, null, 10))
        assertEquals("[hidden by safety filter]", s.students["p1"]!!.responses.single())
        assertTrue(s.wordCloud.none { it.word == "shit" })
    }

    @Test fun `word cloud counts and drops stopwords`() {
        var s = joined(3)
        listOf("denominator stays the same", "same denominator", "add the numerators").forEachIndexed { i, t ->
            s = reducer.reduce(s, LiveEvent.ResponseSubmitted("p${i + 1}", "b", t, 3, null, 1))
        }
        assertEquals(WordFreq("denominator", 2), s.wordCloud.first())
        assertTrue(s.wordCloud.none { it.word == "the" })
    }

    @Test fun `grouping strategies cover everyone with balanced sizes`() {
        val students = joined(29).students.values.toList()
        GroupingStrategy.entries.forEach { strat ->
            val groups = GroupingEngine().group(students, strat, 4, seed = 42)
            assertEquals(students.map { it.studentId }.toSet(), groups.flatMap { it.memberIds }.toSet(), strat.name)
            assertEquals(29, groups.sumOf { it.memberIds.size })
            if (strat != GroupingStrategy.BY_STREAM) assertTrue(groups.maxOf { it.memberIds.size } - groups.minOf { it.memberIds.size } <= 1)
        }
    }

    @Test fun `mixed ability spreads top performers`() {
        var s = joined(8)
        (1..8).forEach { i -> s = reducer.reduce(s, LiveEvent.ResponseSubmitted("p$i", "b", "a", 3, i > 4, 1)) }
        val groups = GroupingEngine().group(s.students.values.toList(), GroupingStrategy.MIXED_ABILITY, 4)
        assertEquals(2, groups.size)
        groups.forEach { g -> assertEquals(2, g.memberIds.count { it.removePrefix("p").toInt() > 4 }) }
    }

    @Test fun `interventions target only flagged students`() {
        var s = joined(5)
        s = reducer.reduce(s, LiveEvent.HelpRequested("p2", 1))
        val suggestions = InterventionPlanner().suggest(s, "Fractions")
        assertEquals(listOf("p2"), suggestions.single().intervention.targetStudentIds)
    }

    @Test fun `analytics clusters misconceptions without ids`() {
        val recs = (1..4).map { ResponseRecord("p$it", Stream.G1, "O1", " 2/6 ", false) } + ResponseRecord("p5", Stream.G3, "O1", "3/4", true)
        val r = SessionAnalytics().report(recs)
        assertEquals(0.2, r.masteryByOutcome["O1"]!!, 1e-9)
        assertEquals(MisconceptionCluster("O1", "2/6", 4), r.misconceptionClusters.single())
        assertEquals(1.0, r.masteryByStream["G3"])
    }
}
