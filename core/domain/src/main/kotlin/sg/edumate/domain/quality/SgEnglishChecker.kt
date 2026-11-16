package sg.edumate.domain.quality

/** Singapore schools follow British spelling. Flags and fixes common American spellings. */
class SgEnglishChecker {
    data class Issue(val found: String, val suggestion: String)

    fun check(text: String): List<Issue> = WORD.findAll(text).mapNotNull { m ->
        US_TO_UK[m.value.lowercase()]?.let { Issue(m.value, matchCase(m.value, it)) }
    }.distinctBy { it.found.lowercase() }.toList()

    fun fix(text: String): String = WORD.replace(text) { m -> US_TO_UK[m.value.lowercase()]?.let { matchCase(m.value, it) } ?: m.value }

    private fun matchCase(original: String, replacement: String) = when {
        original.all { !it.isLetter() || it.isUpperCase() } && original.length > 1 -> replacement.uppercase()
        original.first().isUpperCase() -> replacement.replaceFirstChar { it.uppercase() }
        else -> replacement
    }

    companion object {
        private val WORD = Regex("[A-Za-z]+")
        val US_TO_UK = mapOf(
            "color" to "colour", "colors" to "colours", "colored" to "coloured", "favorite" to "favourite",
            "favorites" to "favourites", "behavior" to "behaviour", "behaviors" to "behaviours", "neighbor" to "neighbour",
            "neighbors" to "neighbours", "center" to "centre", "centers" to "centres", "meter" to "metre", "meters" to "metres",
            "centimeter" to "centimetre", "centimeters" to "centimetres", "kilometer" to "kilometre", "kilometers" to "kilometres",
            "liter" to "litre", "liters" to "litres", "organize" to "organise", "organized" to "organised", "organizing" to "organising",
            "organization" to "organisation", "analyze" to "analyse", "analyzed" to "analysed", "recognize" to "recognise",
            "summarize" to "summarise", "realize" to "realise", "apologize" to "apologise", "gray" to "grey", "math" to "maths",
            "labeled" to "labelled", "labeling" to "labelling", "traveled" to "travelled", "traveling" to "travelling",
            "program" to "programme", "defense" to "defence", "fiber" to "fibre", "theater" to "theatre", "catalog" to "catalogue",
            "dialog" to "dialogue", "practicing" to "practising", "mom" to "mum", "fulfill" to "fulfil", "enrollment" to "enrolment",
        )
    }
}
