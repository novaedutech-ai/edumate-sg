@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package sg.edumate.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sg.edumate.app.ui.components.*
import sg.edumate.app.ui.theme.StatusColors
import sg.edumate.domain.live.GroupingStrategy
import sg.edumate.domain.live.InterventionPlanner
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.LessonPlanRepository
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val store: LiveSessionStore,
    plans: LessonPlanRepository,
) : ViewModel() {
    val session: StateFlow<LiveSessionState?> = store.state
        .sample(250) // UI refresh budget: ≤ 4 fps keeps updates well under 1 s with 45 students
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.state.value)

    /** Only approved plans can be taught live (students never see unreviewed AI content). */
    val approvedPlans = plans.observeAll().map { l -> l.filter { it.reviewStatus.shareableWithStudents } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val preselectedPlanId: String? = savedState["planId"]
    private var topic = "this topic"

    fun start(title: String) = viewModelScope.launch { topic = title; store.start(title, null) }
    fun end() = viewModelScope.launch { store.end() }
    fun suggestions(): List<InterventionPlanner.Suggestion> = store.suggestions(topic)
    fun send(i: Intervention) = viewModelScope.launch { store.sendIntervention(i) }
    fun group(strategy: GroupingStrategy, size: Int, task: String) = viewModelScope.launch { store.makeGroups(strategy, size, task.ifBlank { null }) }
}

@Composable
fun LiveScreen(vm: LiveViewModel = hiltViewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text(session?.activityTitle ?: "Live session") }, actions = {
            if (session != null) TextButton(onClick = vm::end) { Text("End session") }
        })
    }) { padding ->
        Box(Modifier.padding(padding)) {
            val s = session
            if (s == null) StartPanel(vm) else ActiveSession(s, vm)
        }
    }
}

@Composable
private fun StartPanel(vm: LiveViewModel) {
    val plans by vm.approvedPlans.collectAsStateWithLifecycle()
    var custom by rememberSaveable { mutableStateOf("Quick check: ") }
    ContentWidth {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Start a live activity", style = MaterialTheme.typography.titleLarge)
            Text("Students join with a code at the web link or app – no account needed. Up to 45 per session.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader("From an approved lesson plan")
            if (plans.isEmpty()) Text("Approve a lesson plan to teach it live.", style = MaterialTheme.typography.bodySmall)
            plans.forEach { p ->
                ListItem(
                    headlineContent = { Text(p.title) },
                    supportingContent = { Text("${p.flow.count { it.type.interactive }} interactive blocks") },
                    trailingContent = { Button(onClick = { vm.start(p.title) }) { Text(if (p.id == vm.preselectedPlanId) "Start ▶" else "Start") } },
                )
            }
            SectionHeader("Or a quick activity")
            OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth(), label = { Text("Prompt") })
            Button(onClick = { vm.start(custom.trim()) }, enabled = custom.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Outlined.Sensors, null); Text(" Start quick activity")
            }
            Text("Development build: student activity is simulated.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun ActiveSession(s: LiveSessionState, vm: LiveViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Join code", style = MaterialTheme.typography.labelMedium)
                Text(s.joinCode.chunked(3).joinToString(" "), style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = "Join code ${s.joinCode.toList().joinToString(" ")}" })
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${s.students.values.count { it.connected }}/${LiveSessionState.MAX_STUDENTS} joined", style = MaterialTheme.typography.titleMedium)
                s.averageConfidence?.let { Text("Avg confidence %.1f / 5".format(it), style = MaterialTheme.typography.bodySmall) }
            }
        }
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudentStatus.entries.forEach { st -> StatusCount(st, s.count(st)) }
        }
        val pastoral = s.flagged.filter { FlagReason.PASTORAL_CONCERN in it.flags }
        if (pastoral.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Pastoral check-in suggested for ${pastoral.joinToString { it.alias }}. A response may indicate distress. Follow your school's care protocol; this is visible only to you.",
                    modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
            listOf("Students", "Responses", "Groups", "Interventions (${s.flagged.size})").forEachIndexed { i, t ->
                Tab(tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
            }
        }
        when (tab) {
            0 -> StudentGrid(s)
            1 -> ResponsesPanel(s)
            2 -> GroupsPanel(s, vm)
            3 -> InterventionsPanel(s, vm)
        }
    }
}

private fun StudentStatus.color() = when (this) {
    StudentStatus.NOT_STARTED -> StatusColors.notStarted
    StudentStatus.WORKING -> StatusColors.working
    StudentStatus.STUCK -> StatusColors.stuck
    StudentStatus.DONE -> StatusColors.done
}

private fun StudentStatus.icon() = when (this) {
    StudentStatus.NOT_STARTED -> Icons.Outlined.HourglassEmpty
    StudentStatus.WORKING -> Icons.Outlined.Edit
    StudentStatus.STUCK -> Icons.Outlined.PanTool
    StudentStatus.DONE -> Icons.Outlined.CheckCircle
}

@Composable
private fun StatusCount(st: StudentStatus, n: Int) {
    AssistChip(onClick = {}, label = { Text("${st.label}: $n") }, leadingIcon = { Icon(st.icon(), null, tint = st.color()) })
}

@Composable
private fun StudentGrid(s: LiveSessionState) {
    val students = s.students.values.filter { it.connected }
        .sortedWith(compareByDescending<StudentLiveState> { it.flagged }.thenBy { it.alias })
    LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(students, key = { it.studentId }) { st ->
            OutlinedCard(Modifier.semantics(mergeDescendants = true) {}) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(st.status.color(), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(st.alias, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
                        if (st.flagged) Icon(Icons.Outlined.Flag, contentDescription = "Flagged", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                    Text(st.status.label + (st.stream?.let { " · ${it.label}" } ?: ""), style = MaterialTheme.typography.bodySmall)
                    st.accuracy?.let { Text("Accuracy ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall) }
                    st.latestConfidence?.let { c -> LinearProgressIndicator(progress = { c / 5f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
                    if (st.flags.isNotEmpty()) Text(st.flags.joinToString { it.label }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ResponsesPanel(s: LiveSessionState) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader("Word cloud")
        val max = s.wordCloud.maxOfOrNull { it.count } ?: 1
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            s.wordCloud.forEach { w ->
                Text(w.word, fontSize = (14 + 18 * w.count / max).sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { contentDescription = "${w.word}, ${w.count} mentions" })
            }
        }
        SectionHeader("Live response feed")
        s.students.values.flatMap { st -> st.responses.takeLast(1).map { st.alias to it } }.take(30).forEach { (alias, text) ->
            ListItem(headlineContent = { Text(text) }, supportingContent = { Text(alias) })
        }
        Text("Open responses: AI feedback suggestions are reviewed by you before release (v1).", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GroupsPanel(s: LiveSessionState, vm: LiveViewModel) {
    var strategy by rememberSaveable { mutableStateOf(GroupingStrategy.MIXED_ABILITY) }
    var size by rememberSaveable { mutableIntStateOf(4) }
    var task by rememberSaveable { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        ChipSelector(GroupingStrategy.entries, setOf(strategy), { it.label }, { strategy = it })
        Text("Group size: $size", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Slider(size.toFloat(), { size = it.toInt() }, valueRange = 2f..6f, steps = 3)
        OutlinedTextField(task, { task = it }, Modifier.fillMaxWidth(), label = { Text("Group task (shown on each group's shared board)") })
        Button(onClick = { vm.group(strategy, size, task) }, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Outlined.Groups, null); Text(" Create groups") }
        s.groups.forEach { g ->
            OutlinedCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(g.name, style = MaterialTheme.typography.titleSmall)
                    Text(g.memberIds.mapNotNull { s.students[it]?.alias }.joinToString(), style = MaterialTheme.typography.bodySmall)
                    g.task?.let { Text("Task: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun InterventionsPanel(s: LiveSessionState, vm: LiveViewModel) {
    val suggestions = remember(s.flagged.map { it.studentId to it.flags }) { vm.suggestions() }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (suggestions.isEmpty()) EmptyState("No one flagged", "Students who ask for help, show low confidence or repeated errors appear here.")
        suggestions.forEach { sug -> SuggestionCard(sug, s, vm) }
        if (s.interventions.isNotEmpty()) {
            SectionHeader("Sent")
            s.interventions.forEach { i -> Text("• ${i.title} → ${i.targetStudentIds.size} student(s)", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SuggestionCard(sug: InterventionPlanner.Suggestion, s: LiveSessionState, vm: LiveViewModel) {
    var selected by remember(sug.studentIds) { mutableStateOf(sug.studentIds.toSet()) }
    val pastoral = sug.reason == FlagReason.PASTORAL_CONCERN
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(sug.reason.label, style = MaterialTheme.typography.labelLarge, color = if (pastoral) MaterialTheme.colorScheme.error else Color.Unspecified)
            Text(sug.intervention.title, style = MaterialTheme.typography.titleMedium)
            Text(sug.intervention.content, style = MaterialTheme.typography.bodyMedium)
            ChipSelector(sug.studentIds, selected, { id -> s.students[id]?.alias ?: id }, { id -> selected = if (id in selected) selected - id else selected + id })
            if (!pastoral) {
                Button(onClick = { vm.send(sug.intervention.copy(targetStudentIds = selected.toList())) }, enabled = selected.isNotEmpty(),
                    modifier = Modifier.padding(top = 6.dp)) { Text("Send to ${selected.size} selected only") }
            } else {
                Text("Not sent to devices. Speak with the student privately.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
