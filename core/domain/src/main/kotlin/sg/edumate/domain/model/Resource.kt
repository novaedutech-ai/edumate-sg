package sg.edumate.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ResourceType(val label: String) {
    SLIDES("Slides"), WORKSHEET("Worksheet"), VIDEO("Video"), INTERACTIVE("Interactive"),
    READING("Reading"), ASSESSMENT("Assessment"), LESSON_PLAN("Lesson plan"),
}

@Serializable
enum class ResourceSourceKind(val label: String) {
    MOE_LIBRARY("MOE library"), USER_UPLOAD("My uploads"), OPEN_LICENSED("Open licensed"),
}

@Serializable
data class Licence(
    val name: String,
    val url: String? = null,
    val allowsModification: Boolean,
    val requiresAttribution: Boolean,
)

@Serializable
data class Resource(
    val id: String,
    val title: String,
    val summary: String,
    val sourceKind: ResourceSourceKind,
    val sourceName: String,
    val licence: Licence,
    val attribution: String,
    val level: Level,
    val subjectId: String,
    val topicIds: List<String>,
    val type: ResourceType,
    val streams: List<Stream>,
    val url: String? = null,
    /** True for sample/demo data. The UI badges these and the AI never cites them as real sources. */
    val isMock: Boolean = false,
    val bookmarked: Boolean = false,
    val offlineAvailable: Boolean = false,
) {
    fun toCitation() = Citation(id, title, sourceName, licence.name)
}

@Serializable
data class ResourceQuery(
    val text: String = "",
    val levels: Set<Level> = emptySet(),
    val subjectIds: Set<String> = emptySet(),
    val topicIds: Set<String> = emptySet(),
    val types: Set<ResourceType> = emptySet(),
    val streams: Set<Stream> = emptySet(),
    val sources: Set<ResourceSourceKind> = emptySet(),
    val bookmarkedOnly: Boolean = false,
) {
    fun matches(r: Resource): Boolean =
        (text.isBlank() || listOf(r.title, r.summary).any { it.contains(text.trim(), ignoreCase = true) }) &&
            (levels.isEmpty() || r.level in levels) &&
            (subjectIds.isEmpty() || r.subjectId in subjectIds) &&
            (topicIds.isEmpty() || r.topicIds.any { it in topicIds }) &&
            (types.isEmpty() || r.type in types) &&
            (streams.isEmpty() || r.streams.isEmpty() || r.streams.any { it in streams }) &&
            (sources.isEmpty() || r.sourceKind in sources) &&
            (!bookmarkedOnly || r.bookmarked)
}
