package sg.edumate.domain

import sg.edumate.domain.ai.PiiTokeniser
import kotlin.test.*

class PiiTokeniserTest {
    private val tok = PiiTokeniser(listOf("Tan Wei Ming", "Nur Aisyah"))

    @Test fun `replaces NRIC email phone and roster names`() {
        val input = "Wei Ming (S1234567D) emailed tan.wm@school.edu.sg, call 9123 4567. Nur Aisyah helped."
        val r = tok.tokenise(input)
        listOf("S1234567D", "tan.wm@school.edu.sg", "9123 4567", "Wei", "Ming", "Aisyah").forEach {
            assertFalse(r.text.contains(it), "leaked $it in: ${r.text}")
        }
        assertTrue(r.text.contains("[NRIC_1]") && r.text.contains("[EMAIL_1]") && r.text.contains("[PHONE_1]"))
        assertEquals(input, tok.detokenise(r.text, r.vault))
    }

    @Test fun `same name maps to same token`() {
        val r = tok.tokenise("Aisyah said hi. Later AISYAH left.")
        assertEquals(2, Regex("\\[PERSON_1]").findAll(r.text).count())
    }

    @Test fun `does not match name inside other word`() {
        val r = PiiTokeniser(listOf("Ali")).tokenise("Alignment of Ali's work")
        assertTrue(r.text.startsWith("Alignment"))
        assertTrue(r.text.contains("[PERSON_1]'s"))
    }

    @Test fun `FIN and +65 numbers detected`() {
        val r = PiiTokeniser().tokenise("FIN G7654321K, +65 8123-4567")
        assertEquals("FIN [NRIC_1], [PHONE_1]", r.text)
    }

    @Test fun `plain numbers are not phones`() {
        assertEquals("Answer is 12345678901", PiiTokeniser().tokenise("Answer is 12345678901").text)
        assertEquals("Find 3/4 of 120", PiiTokeniser().tokenise("Find 3/4 of 120").text)
    }
}
