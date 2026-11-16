package sg.edumate.app.data.live

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.LiveSessionGateway
import sg.edumate.domain.repository.LiveSessionHandle
import sg.edumate.domain.util.Clock
import kotlin.random.Random

/**
 * Development stand-in for the WebSocket transport. Simulates [classSize] students joining and
 * working through an activity, with some getting stuck, so the monitoring UI can be exercised.
 */
class SimulatedLiveSessionGateway(
    private val clock: Clock,
    private val classSize: Int = 32,
    private val seed: Int = 7,
) : LiveSessionGateway {

    override suspend fun start(activityTitle: String, classId: String?): LiveSessionHandle {
        val rnd = Random(seed)
        val code = (1..6).map { "ABCDEFGHJKMNPQRSTUVWXYZ23456789"[rnd.nextInt(31)] }.joinToString("")
        return Handle("sim-${clock.nowMs()}", code, rnd)
    }

    private inner class Handle(override val sessionId: String, override val joinCode: String, private val rnd: Random) : LiveSessionHandle {
        @Volatile private var ended = false

        override val events: Flow<LiveEvent> = flow {
            val ids = (1..classSize).map { "p%02d".format(it) }
            val streams = listOf(Stream.G1, Stream.G2, Stream.G3)
            // Students who will struggle in this simulation.
            val strugglers = ids.shuffled(rnd).take(classSize / 6).toSet()
            ids.forEach { id ->
                if (ended) return@flow
                emit(LiveEvent.Joined(id, "Student ${id.drop(1)}", streams[rnd.nextInt(streams.size)], clock.nowMs()))
                delay(rnd.nextLong(40, 120))
            }
            var tick = 0
            while (!ended && tick < 400) {
                val id = ids[rnd.nextInt(ids.size)]
                val struggling = id in strugglers
                val now = clock.nowMs()
                val event: LiveEvent = when (rnd.nextInt(10)) {
                    0, 1 -> LiveEvent.StatusChanged(id, StudentStatus.WORKING, now)
                    2 -> if (struggling) LiveEvent.HelpRequested(id, now) else LiveEvent.StatusChanged(id, StudentStatus.DONE, now)
                    else -> LiveEvent.ResponseSubmitted(
                        id, "block-1",
                        (if (struggling) WRONG else RIGHT).random(rnd),
                        confidence = if (struggling) rnd.nextInt(1, 3) else rnd.nextInt(3, 6),
                        correct = if (struggling) rnd.nextInt(4) == 0 else rnd.nextInt(6) != 0,
                        atMs = now,
                    )
                }
                emit(event)
                tick++
                delay(rnd.nextLong(150, 600))
            }
        }

        override suspend fun pushIntervention(intervention: Intervention) { /* Simulated: delivered instantly. */ }
        override suspend fun assignGroups(groups: List<StudentGroup>) {}
        override suspend fun end() { ended = true }
    }

    private companion object {
        val RIGHT = listOf("Make the denominators the same first", "Convert to equivalent fractions then add numerators",
            "Use a bar model to show the parts", "Common denominator then simplify", "Equivalent fractions with the same denominator")
        val WRONG = listOf("Add the tops and add the bottoms", "1/2 + 1/4 = 2/6", "Just add the numbers", "I don't know", "Bigger denominator means bigger fraction")
    }
}
