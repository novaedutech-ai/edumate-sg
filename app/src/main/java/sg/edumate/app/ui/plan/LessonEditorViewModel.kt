package sg.edumate.app.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.LessonPlanRepository
import sg.edumate.domain.usecase.RefineLessonPlanUseCase
import sg.edumate.domain.usecase.SaveLessonPlanEditsUseCase
import sg.edumate.domain.usecase.SetReviewStatusUseCase
import sg.edumate.domain.util.Outcome
import java.util.UUID
import javax.inject.Inject

data class EditorUiState(
    val plan: LessonPlan? = null,
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val tone: Tone = Tone.WARM,
    val message: String? = null,
    val newVersionId: String? = null,
)

@HiltViewModel
class LessonEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val plans: LessonPlanRepository,
    private val save: SaveLessonPlanEditsUseCase,
    private val refine: RefineLessonPlanUseCase,
    private val setStatus: SetReviewStatusUseCase,
) : ViewModel() {
    private val planId: String = checkNotNull(savedState["planId"])
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state

    init {
        viewModelScope.launch {
            var first = true
            plans.observe(planId).collect { p ->
                if (!_state.value.dirty) _state.update { it.copy(plan = p) }
                if (first && p != null && p.provenance?.isOfflineTemplate == true && p.reviewStatus == ReviewStatus.DRAFT_AI) {
                    _state.update { it.copy(message = "This is an offline template, not AI. Check the AI backend is running, then tap Regenerate.") }
                }
                if (p != null) first = false
            }
        }
    }

    private fun edit(f: (LessonPlan) -> LessonPlan) = _state.update { s -> s.plan?.let { s.copy(plan = f(it), dirty = true) } ?: s }

    fun title(v: String) = edit { it.copy(title = v) }
    fun objectives(v: List<String>) = edit { it.copy(objectives = v) }
    fun priorKnowledge(v: List<String>) = edit { it.copy(priorKnowledge = v) }
    fun successCriteria(v: List<String>) = edit { it.copy(successCriteria = v) }
    fun aflChecks(v: List<String>) = edit { it.copy(aflChecks = v) }
    fun differentiation(tier: ScaffoldTier, text: String) = edit { p ->
        p.copy(differentiation = p.differentiation.filterNot { it.tier == tier } + DifferentiatedActivity(tier, text))
    }
    fun toggleCompetency(c: Competency21) = edit { p -> p.copy(competencies = if (c in p.competencies) p.competencies - c else p.competencies + c) }
    fun setTags(raw: String) = edit { p -> p.copy(tags = raw.split(',', ' ').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.toSet()) }
    fun toggleShare() = edit { it.copy(sharedWithDepartment = !it.sharedWithDepartment) }

    // Lesson timeline ("interactive modules")
    fun updateBlock(b: LessonBlock) = edit { p -> p.copy(flow = p.flow.map { if (it.id == b.id) b else it }) }
    fun removeBlock(id: String) = edit { p -> p.copy(flow = p.flow.filterNot { it.id == id }) }
    fun moveBlock(id: String, delta: Int) = edit { p ->
        val i = p.flow.indexOfFirst { it.id == id }; val j = i + delta
        if (i < 0 || j !in p.flow.indices) p else p.copy(flow = p.flow.toMutableList().apply { add(j, removeAt(i)) })
    }
    fun addBlock(type: BlockType) = edit { p ->
        p.copy(flow = p.flow + LessonBlock(UUID.randomUUID().toString(), type, type.label, "", 5,
            isCheckForUnderstanding = type in setOf(BlockType.POLL, BlockType.MCQ, BlockType.EXIT_TICKET),
            options = if (type == BlockType.MCQ || type == BlockType.POLL) listOf("", "") else emptyList()))
    }

    fun setTone(t: Tone) = _state.update { it.copy(tone = t) }

    fun saveEdits() {
        val p = _state.value.plan ?: return
        // Clear dirty first so the repository emission (with the new review status) flows back into the UI.
        _state.update { it.copy(dirty = false) }
        viewModelScope.launch { save(p); _state.update { it.copy(message = "Saved") } }
    }

    fun approve() = changeStatus(ReviewStatus.APPROVED)
    fun reject() = changeStatus(ReviewStatus.REJECTED)

    private fun changeStatus(s: ReviewStatus) {
        val pending = _state.value.plan?.takeIf { _state.value.dirty }
        _state.update { it.copy(dirty = false) }
        viewModelScope.launch {
            pending?.let { save(it) }
            setStatus(planId, s)
            _state.update { it.copy(message = if (s == ReviewStatus.APPROVED) "Approved – ready to share" else "Marked as rejected") }
        }
    }

    /** null refinement = regenerate. Creates v+1 linked to this plan. */
    fun refine(r: Refinement?) {
        val pending = _state.value.plan?.takeIf { _state.value.dirty }
        _state.update { it.copy(busy = true, dirty = false) }
        viewModelScope.launch {
            pending?.let { save(it) }
            when (val res = refine(planId, r, _state.value.tone)) {
                is Outcome.Ok -> _state.update { it.copy(busy = false, newVersionId = res.value.id, dirty = false) }
                is Outcome.Blocked -> _state.update { it.copy(busy = false, message = res.reason) }
                is Outcome.Failed -> _state.update { it.copy(busy = false, message = res.message) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeNavigation() = _state.update { it.copy(newVersionId = null) }
}
