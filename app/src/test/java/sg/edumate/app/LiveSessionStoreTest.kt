package sg.edumate.app

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import sg.edumate.app.data.live.SimulatedLiveSessionGateway
import sg.edumate.app.ui.live.LiveSessionStore
import sg.edumate.domain.ai.LocalContentModerator
import sg.edumate.domain.live.*
import sg.edumate.domain.model.StudentStatus
import sg.edumate.domain.util.Clock

class LiveSessionStoreTest {
    @Test fun simulatedClassJoinsAndGroupsCoverEveryone() = runBlocking {
        val store = LiveSessionStore(SimulatedLiveSessionGateway(Clock.SYSTEM, classSize = 12), LiveSessionReducer(LocalContentModerator()),
            GroupingEngine(), InterventionPlanner(), Clock.SYSTEM, TestIds())
        store.start("Fractions check", null)
        val s = withTimeout(10_000) { store.state.first { (it?.students?.size ?: 0) == 12 } }!!
        assertEquals(6, s.joinCode.length)
        store.makeGroups(GroupingStrategy.RANDOM, 4, "Solve together")
        val groups = store.state.value!!.groups
        assertEquals(12, groups.sumOf { it.memberIds.size })
        assertTrue(groups.all { it.task == "Solve together" })
        withTimeout(15_000) { store.state.first { st -> st!!.students.values.any { it.status != StudentStatus.NOT_STARTED } } }
        store.end()
        assertNull(store.state.value)
    }
}
