package sg.edumate.domain

import sg.edumate.domain.model.*
import sg.edumate.domain.quality.ReadabilityChecker
import sg.edumate.domain.quality.SgEnglishChecker
import kotlin.test.*

class QualityTest {
    @Test fun `fixes US spelling preserving case`() {
        val c = SgEnglishChecker()
        assertEquals("Colour the centre of the Organisation's logo grey", c.fix("Color the center of the Organization's logo gray"))
        assertEquals(listOf("favorite"), c.check("My favorite favorite").map { it.found })
    }

    @Test fun `readability higher for complex text`() {
        val r = ReadabilityChecker()
        val easy = r.gradeLevel("The cat sat on the mat. It was a big cat. The mat was red.")!!
        val hard = r.gradeLevel("Photosynthesis constitutes the biochemical mechanism whereby chlorophyll-containing organisms synthesise carbohydrates utilising electromagnetic radiation.")!!
        assertTrue(hard > easy + 5, "easy=$easy hard=$hard")
    }

    @Test fun `every persisted field of core entities carries a privacy classification`() {
        // Check the primary constructor (skip Kotlin's synthetic default-args constructor).
        listOf(LessonPlan::class.java, Worksheet::class.java, StudentRef::class.java, SchoolClass::class.java).forEach { cls ->
            val ctor = cls.declaredConstructors.filterNot { it.isSynthetic }.maxBy { it.parameterCount }
            val annotated = ctor.parameterAnnotations.count { a -> a.any { it is Classified } }
            assertEquals(ctor.parameterCount, annotated, "${cls.simpleName} has unclassified fields")
        }
    }

    @Test fun `streams map to three tiers per band`() {
        LevelBand.entries.forEach { b -> assertEquals(ScaffoldTier.entries.toSet(), Stream.forBand(b).map { it.tier }.toSet()) }
    }
}
