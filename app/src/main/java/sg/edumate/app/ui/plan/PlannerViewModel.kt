package sg.edumate.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sg.edumate.app.data.curriculum.MockCurriculumRepository
import sg.edumate.app.data.settings.UserPreferencesRepository
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.LessonPlanRepository
import sg.edumate.domain.repository.ResourceRepository
import sg.edumate.domain.repository.WorksheetRepository
import sg.edumate.domain.usecase.DuplicateLessonPlanUseCase
import sg.edumate.domain.usecase.GenerateLessonPlanUseCase
import sg.edumate.domain.usecase.LessonPlanInput
import sg.edumate.domain.util.Outcome
import javax.inject.Inject

data class PlannerUiState(
    val selection: TopicSelection = TopicSelection(),
    val streams: Set<Stream> = emptySet(),
    val durationMin: Int = 60,
    val notes: String = "",
    val bookmarked: List<Resource> = emptyList(),
    val references: Set<String> = emptySet(),
    val generating: Boolean = false,
    val message: String? = null,
    val aiEnabled: Boolean = true,
)

sealed interface PlannerEvent { data class OpenPlan(val id: String) : PlannerEvent }

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val curriculum: MockCurriculumRepository,
    private val prefs: UserPreferencesRepository,
    private val generate: GenerateLessonPlanUseCase,
    private val duplicate: DuplicateLessonPlanUseCase,
    private val plans: LessonPlanRepository,
    worksheets: WorksheetRepository,
    private val resources: ResourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlannerUiState())
    val state: StateFlow<PlannerUiState> = _state
    private val _events = MutableSharedFlow<PlannerEvent>()
    val events: SharedFlow<PlannerEvent> = _events

    val plansList = plans.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val worksheetList = worksheets.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val p = prefs.prefs.first()
            val school = prefs.current()
            val sel = TopicSelection.initial(p, curriculum)
            _state.update { it.copy(selection = sel, streams = sel.streamsForLevel.toSet(), aiEnabled = school.aiFeaturesEnabled && p.aiConsent) }
            refreshBookmarks()
        }
    }

    private suspend fun refreshBookmarks() {
        val sel = _state.value.selection
        val list = resources.search(ResourceQuery(bookmarkedOnly = true, subjectIds = setOfNotNull(sel.subjectId)))
            .filterNot { it.isMock } // sample records are never offered as AI references
        _state.update { it.copy(bookmarked = list, references = it.references.intersect(list.map { r -> r.id }.toSet())) }
    }

    fun onLevel(l: Level) = _state.update { s ->
        val sel = s.selection.withLevel(l, curriculum); s.copy(selection = sel, streams = sel.streamsForLevel.toSet())
    }.also { viewModelScope.launch { refreshBookmarks() } }
    fun onSubject(id: String) = _state.update { it.copy(selection = it.selection.withSubject(id, curriculum)) }.also { viewModelScope.launch { refreshBookmarks() } }
    fun onTopic(id: String) = _state.update { it.copy(selection = it.selection.copy(topicId = id)) }
    fun toggleStream(s: Stream) = _state.update { st -> st.copy(streams = if (s in st.streams) st.streams - s else st.streams + s) }
    fun onDuration(m: Int) = _state.update { it.copy(durationMin = m) }
    fun onNotes(n: String) = _state.update { it.copy(notes = n) }
    fun toggleReference(id: String) = _state.update { s -> s.copy(references = if (id in s.references) s.references - id else s.references + id) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun generate() {
        val s = _state.value
        val topicId = s.selection.topicId ?: return
        if (s.streams.isEmpty()) { _state.update { it.copy(message = "Choose at least one stream") }; return }
        _state.update { it.copy(generating = true) }
        viewModelScope.launch {
            val result = generate(LessonPlanInput(topicId, s.streams.sortedBy { it.tier.ordinal }, s.durationMin, s.notes, s.references.toList()))
            _state.update { it.copy(generating = false) }
            when (result) {
                is Outcome.Ok -> _events.emit(PlannerEvent.OpenPlan(result.value.id))
                is Outcome.Blocked -> _state.update { it.copy(message = result.reason) }
                is Outcome.Failed -> _state.update { it.copy(message = result.message) }
            }
        }
    }

    fun duplicate(id: String) = viewModelScope.launch { duplicate.invoke(id); _state.update { it.copy(message = "Duplicated") } }
    fun delete(id: String) = viewModelScope.launch { plans.delete(id) }
}
