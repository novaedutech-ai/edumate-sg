package sg.edumate.domain.live

import sg.edumate.domain.model.Stream

data class ResponseRecord(val studentId: String, val stream: Stream?, val outcomeCode: String, val answer: String, val correct: Boolean)

data class MisconceptionCluster(val outcomeCode: String, val answer: String, val count: Int)

data class SessionReport(
    val masteryByOutcome: Map<String, Double>,
    val masteryByStream: Map<String, Double>,
    val misconceptionClusters: List<MisconceptionCluster>,
)

/** Post-session analytics. Operates on pseudonymous IDs only; output contains no student identifiers. */
class SessionAnalytics {
    fun report(records: List<ResponseRecord>, minClusterSize: Int = 3): SessionReport = SessionReport(
        masteryByOutcome = records.groupBy { it.outcomeCode }.mapValues { (_, r) -> r.count { it.correct }.toDouble() / r.size },
        masteryByStream = records.groupBy { it.stream?.label ?: "Unassigned" }.mapValues { (_, r) -> r.count { it.correct }.toDouble() / r.size },
        misconceptionClusters = records.filterNot { it.correct }
            .groupBy { it.outcomeCode to it.answer.trim().lowercase() }
            .filter { it.value.size >= minClusterSize }
            .map { (k, v) -> MisconceptionCluster(k.first, k.second, v.size) }
            .sortedByDescending { it.count },
    )
}
