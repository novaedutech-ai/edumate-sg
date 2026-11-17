package sg.edumate.app.ui.plan

import sg.edumate.app.data.curriculum.MockCurriculumRepository
import sg.edumate.app.data.settings.TeacherPrefs
import sg.edumate.domain.model.*

/** Immutable topic-picker state shared by the planner and worksheet generator. */
data class TopicSelection(
    val levels: List<Level> = emptyList(),
    val level: Level? = null,
    val subjects: List<Subject> = emptyList(),
    val subjectId: String? = null,
    val topics: List<SyllabusTopic> = emptyList(),
    val topicId: String? = null,
) {
    val topic: SyllabusTopic? get() = topics.firstOrNull { it.id == topicId }
    val streamsForLevel: List<Stream> get() = level?.let { Stream.forBand(it.band) }.orEmpty()

    fun withLevel(l: Level, c: MockCurriculumRepository): TopicSelection {
        val subjects = c.subjectsWithTopics(l)
        val subject = subjectId?.takeIf { id -> subjects.any { it.id == id } } ?: subjects.firstOrNull()?.id
        return copy(level = l, subjects = subjects).withSubject(subject, c)
    }

    fun withSubject(id: String?, c: MockCurriculumRepository): TopicSelection {
        val topics = if (id != null && level != null) c.topics(level, id) else emptyList()
        return copy(subjectId = id, topics = topics, topicId = topics.firstOrNull()?.id)
    }

    companion object {
        /** Preselect the teacher's own level/subject so generation is usually one tap from here. */
        fun initial(prefs: TeacherPrefs, c: MockCurriculumRepository): TopicSelection {
            val levels = Level.entries.filter { l -> c.allTopics().any { it.level == l } }
            val preferredSubject = prefs.subjectIds.firstOrNull()
            val level = prefs.levels.sorted().firstOrNull { l -> c.allTopics().any { it.level == l && (preferredSubject == null || it.subjectId == preferredSubject) } }
                ?: prefs.levels.firstOrNull { it in levels } ?: levels.first()
            val base = TopicSelection(levels = levels, subjectId = preferredSubject).withLevel(level, c)
            return base
        }
    }
}
