package sg.edumate.app.ui.plan

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sg.edumate.app.data.curriculum.MockCurriculumRepository
import sg.edumate.app.data.export.WorksheetPdfExporter
import sg.edumate.app.data.settings.UserPreferencesRepository
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.WorksheetRepository
import sg.edumate.domain.usecase.GenerateWorksheetUseCase
import sg.edumate.domain.util.Clock
import sg.edumate.domain.util.Outcome
import javax.inject.Inject

data class WorksheetUiState(
    val selection: TopicSelection = TopicSelection(),
    val questionCount: Int = 8,
    val types: Set<QuestionType> = setOf(QuestionType.MCQ, QuestionType.SHORT_ANSWER, QuestionType.STRUCTURED),
    val foundational: Int = 40,
    val intermediate: Int = 40,
    val streams: Set<Stream> = emptySet(),
    val generating: Boolean = false,
    val worksheet: Worksheet? = null,
    val versionIndex: Int = 0,
    val showAnswers: Boolean = false,
    val message: String? = null,
    val shareUri: Uri? = null,
) {
    val advanced: Int get() = 100 - foundational - intermediate
}

@HiltViewModel
class WorksheetViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val curriculum: MockCurriculumRepository,
    private val prefs: UserPreferencesRepository,
    private val generate: GenerateWorksheetUseCase,
    private val repo: WorksheetRepository,
    private val exporter: WorksheetPdfExporter,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(WorksheetUiState())
    val state: StateFlow<WorksheetUiState> = _state

    init {
        val existingId: String? = savedState["id"]
        viewModelScope.launch {
            val sel = TopicSelection.initial(prefs.prefs.first(), curriculum)
            _state.update { it.copy(selection = sel, streams = sel.streamsForLevel.toSet()) }
            existingId?.let { id -> repo.get(id)?.let { ws -> _state.update { it.copy(worksheet = ws) } } }
        }
    }

    fun onLevel(l: Level) = _state.update { s -> val sel = s.selection.withLevel(l, curriculum); s.copy(selection = sel, streams = sel.streamsForLevel.toSet()) }
    fun onSubject(id: String) = _state.update { it.copy(selection = it.selection.withSubject(id, curriculum)) }
    fun onTopic(id: String) = _state.update { it.copy(selection = it.selection.copy(topicId = id)) }
    fun onCount(n: Int) = _state.update { it.copy(questionCount = n) }
    fun toggleType(t: QuestionType) = _state.update { s -> s.copy(types = if (t in s.types && s.types.size > 1) s.types - t else s.types + t) }
    fun toggleStream(st: Stream) = _state.update { s -> s.copy(streams = if (st in s.streams && s.streams.size > 1) s.streams - st else s.streams + st) }
    fun onFoundational(v: Int) = _state.update { s -> val f = v.coerceIn(0, 100); s.copy(foundational = f, intermediate = s.intermediate.coerceAtMost(100 - f)) }
    fun onIntermediate(v: Int) = _state.update { s -> s.copy(intermediate = v.coerceIn(0, 100 - s.foundational)) }
    fun selectVersion(i: Int) = _state.update { it.copy(versionIndex = i) }
    fun toggleAnswers() = _state.update { it.copy(showAnswers = !it.showAnswers) }
    fun newWorksheet() = _state.update { it.copy(worksheet = null, versionIndex = 0) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeShare() = _state.update { it.copy(shareUri = null) }

    fun generate(refinement: Refinement? = null) {
        val s = _state.value
        val topic = s.selection.topic ?: return
        _state.update { it.copy(generating = true) }
        viewModelScope.launch {
            val spec = WorksheetSpec(
                topic = topic, objectives = topic.outcomes.map { it.description }, questionCount = s.questionCount,
                questionTypes = s.types, difficultyMix = DifficultyMix(s.foundational, s.intermediate, s.advanced), streams = s.streams.toList(),
            )
            when (val r = generate(spec, refinement)) {
                is Outcome.Ok -> _state.update { it.copy(generating = false, worksheet = r.value, versionIndex = 0) }
                is Outcome.Blocked -> _state.update { it.copy(generating = false, message = r.reason) }
                is Outcome.Failed -> _state.update { it.copy(generating = false, message = r.message) }
            }
        }
    }

    fun approve() = _state.value.worksheet?.let { ws ->
        viewModelScope.launch {
            val updated = ws.copy(reviewStatus = ReviewStatus.APPROVED, updatedAtEpochMs = clock.nowMs(), syncState = SyncState.PENDING_UPLOAD)
            repo.upsert(updated); _state.update { it.copy(worksheet = updated, message = "Approved – export unlocked") }
        }
    }

    /** Applies the SG-spelling fix suggested by the quality check to every version. */
    fun applySpellingFixes() = _state.value.worksheet?.let { ws ->
        val fixer = sg.edumate.domain.quality.SgEnglishChecker()
        val fixed = ws.copy(
            versions = ws.versions.map { v -> v.copy(questions = v.questions.map { q -> q.copy(prompt = fixer.fix(q.prompt), scaffolds = q.scaffolds.map { it.copy(content = fixer.fix(it.content)) }) },
                checks = v.checks.map { if (it.name == "SG English spelling") it.copy(passed = true, detail = "Fixed") else it }) },
            reviewStatus = if (ws.reviewStatus == ReviewStatus.DRAFT_AI) ReviewStatus.TEACHER_EDITED else ws.reviewStatus,
        )
        viewModelScope.launch { repo.upsert(fixed); _state.update { it.copy(worksheet = fixed) } }
    }

    fun exportPdf(includeAnswers: Boolean) {
        val s = _state.value
        val ws = s.worksheet ?: return
        if (!ws.reviewStatus.shareableWithStudents) { _state.update { it.copy(message = "Approve the worksheet before exporting") }; return }
        viewModelScope.launch {
            val uri = exporter.export(ws, ws.versions[s.versionIndex], includeAnswers)
            _state.update { it.copy(shareUri = uri) }
        }
    }
}
