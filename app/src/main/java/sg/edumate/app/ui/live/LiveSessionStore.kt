package sg.edumate.app.ui.live

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import sg.edumate.domain.live.*
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.LiveSessionGateway
import sg.edumate.domain.repository.LiveSessionHandle
import sg.edumate.domain.util.Clock
import sg.edumate.domain.util.IdGenerator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide holder of the active live session so it survives navigation between tabs.
 * Events are reduced on a single coroutine (no lock needed) and conflated to the UI.
 */
@Singleton
class LiveSessionStore @Inject constructor(
    private val gateway: LiveSessionGateway,
    private val reducer: LiveSessionReducer,
    private val grouping: GroupingEngine,
    private val planner: InterventionPlanner,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var handle: LiveSessionHandle? = null
    private var jobs: List<Job> = emptyList()

    private val _state = MutableStateFlow<LiveSessionState?>(null)
    val state: StateFlow<LiveSessionState?> = _state

    val pendingInterventions: Flow<Int> = _state.map { it?.flagged?.size ?: 0 }.distinctUntilChanged()

    suspend fun start(activityTitle: String, classId: String?) {
        end()
        val h = gateway.start(activityTitle, classId)
        handle = h
        _state.value = LiveSessionState(h.sessionId, h.joinCode, activityTitle, clock.nowMs())
        jobs = listOf(
            scope.launch { h.events.collect { e -> _state.update { s -> s?.let { reducer.reduce(it, e) } } } },
            scope.launch { while (isActive) { delay(5_000); _state.update { s -> s?.let { reducer.tick(it, clock.nowMs()) } } } },
        )
    }

    fun suggestions(topic: String): List<InterventionPlanner.Suggestion> = _state.value?.let { planner.suggest(it, topic) }.orEmpty()

    suspend fun sendIntervention(i: Intervention) {
        val sent = i.copy(id = ids.newId(), sentAtMs = clock.nowMs())
        handle?.pushIntervention(sent)
        _state.update { s -> s?.let { reducer.clearFlags(it, sent.targetStudentIds).copy(interventions = it.interventions + sent) } }
    }

    suspend fun makeGroups(strategy: GroupingStrategy, size: Int, task: String?) {
        val s = _state.value ?: return
        val groups = grouping.group(s.students.values.toList(), strategy, size, seed = clock.nowMs()).map { it.copy(task = task) }
        handle?.assignGroups(groups)
        _state.update { it?.copy(groups = groups) }
    }

    suspend fun end() {
        jobs.forEach { it.cancel() }; jobs = emptyList()
        handle?.end(); handle = null
        _state.value = null
    }
}
