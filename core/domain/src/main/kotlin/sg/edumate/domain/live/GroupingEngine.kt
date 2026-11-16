package sg.edumate.domain.live

import sg.edumate.domain.model.StudentGroup
import sg.edumate.domain.model.StudentLiveState
import kotlin.random.Random

enum class GroupingStrategy(val label: String) { BY_STREAM("By stream"), MIXED_ABILITY("Mixed ability"), RANDOM("Random") }

class GroupingEngine {
    /**
     * @param groupSize target size; groups differ in size by at most one.
     * Mixed-ability uses a snake draft over accuracy so each group gets a spread of attainment.
     */
    fun group(students: List<StudentLiveState>, strategy: GroupingStrategy, groupSize: Int, seed: Long = 0L): List<StudentGroup> {
        require(groupSize >= 2) { "Groups need at least 2 students" }
        val present = students.filter { it.connected }
        if (present.isEmpty()) return emptyList()
        return when (strategy) {
            GroupingStrategy.RANDOM -> chunkEvenly(present.shuffled(Random(seed)), groupSize)
            GroupingStrategy.BY_STREAM -> present.groupBy { it.stream?.label ?: "Unassigned" }.toSortedMap()
                .flatMap { (stream, members) -> chunkEvenly(members.sortedBy { it.alias }, groupSize).map { stream to it } }
                .mapIndexed { i, (stream, members) -> members.copy(id = "g${i + 1}", name = "$stream · Group ${i + 1}") }
            GroupingStrategy.MIXED_ABILITY -> {
                val n = groupCount(present.size, groupSize)
                val ranked = present.sortedWith(compareByDescending<StudentLiveState> { it.accuracy ?: 0.5 }.thenBy { it.alias })
                val buckets = List(n) { mutableListOf<StudentLiveState>() }
                ranked.forEachIndexed { i, s ->
                    val round = i / n; val pos = i % n
                    buckets[if (round % 2 == 0) pos else n - 1 - pos] += s
                }
                buckets.mapIndexed { i, b -> StudentGroup("g${i + 1}", "Group ${i + 1}", b.map { it.studentId }) }
            }
        }
    }

    private fun groupCount(size: Int, groupSize: Int) = maxOf(1, Math.round(size.toDouble() / groupSize).toInt())

    private fun chunkEvenly(list: List<StudentLiveState>, groupSize: Int): List<StudentGroup> {
        val n = groupCount(list.size, groupSize)
        val buckets = List(n) { mutableListOf<String>() }
        list.forEachIndexed { i, s -> buckets[i % n] += s.studentId }
        return buckets.mapIndexed { i, b -> StudentGroup("g${i + 1}", "Group ${i + 1}", b) }
    }
}
