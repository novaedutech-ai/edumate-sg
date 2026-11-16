@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package sg.edumate.app.ui.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sg.edumate.app.ui.components.*
import sg.edumate.domain.model.LessonPlan
import sg.edumate.domain.model.ReviewStatus

@Composable
fun PlannerScreen(
    onOpenPlan: (String) -> Unit,
    onOpenWorksheet: (String) -> Unit,
    onNewWorksheet: () -> Unit,
    vm: PlannerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val plans by vm.plansList.collectAsStateWithLifecycle()
    val worksheets by vm.worksheetList.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.events.collect { if (it is PlannerEvent.OpenPlan) onOpenPlan(it.id) } }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Plan") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == 1) ExtendedFloatingActionButton(onClick = onNewWorksheet, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("New worksheet") })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Lesson plans") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Worksheets") })
            }
            if (tab == 0) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { QuickGenerateCard(state, vm) }
                    item { SectionHeader("My lesson plans") }
                    if (plans.isEmpty()) item { EmptyState("No plans yet", "Generate your first plan above. It's saved offline.") }
                    items(plans, key = { it.id }) { p -> PlanRow(p, onClick = { onOpenPlan(p.id) }, onDuplicate = { vm.duplicate(p.id) }, onDelete = { vm.delete(p.id) }) }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (worksheets.isEmpty()) item { EmptyState("No worksheets yet", "Create a worksheet with G1/G2/G3 versions in one go.") }
                    items(worksheets, key = { it.id }) { w ->
                        ElevatedCard(onClick = { onOpenWorksheet(w.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(w.title, style = MaterialTheme.typography.titleMedium)
                                Text("${w.level.label} · ${w.versions.joinToString(" / ") { it.stream.label }}", style = MaterialTheme.typography.bodySmall)
                                ReviewPill(w.reviewStatus)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Three steps: (1) topic, (2) streams, (3) Generate. Everything else is optional. */
@Composable
private fun QuickGenerateCard(state: PlannerUiState, vm: PlannerViewModel) {
    var showOptions by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("New lesson plan", style = MaterialTheme.typography.titleLarge)
            Text("1 · Choose a topic", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            val sel = state.selection
            TopicPicker(sel.levels, sel.level, vm::onLevel, sel.subjects, sel.subjectId, vm::onSubject, sel.topics, sel.topicId, vm::onTopic)
            Text("2 · Streams to differentiate for", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            ChipSelector(sel.streamsForLevel, state.streams, { it.label }, vm::toggleStream)

            TextButton(onClick = { showOptions = !showOptions }) { Text(if (showOptions) "Hide options" else "More options (duration, notes, resources)") }
            if (showOptions) {
                Text("Duration: ${state.durationMin} min", style = MaterialTheme.typography.labelLarge)
                Slider(value = state.durationMin.toFloat(), onValueChange = { vm.onDuration(it.toInt()) }, valueRange = 30f..120f, steps = 5)
                OutlinedTextField(state.notes, vm::onNotes, Modifier.fillMaxWidth(), label = { Text("Notes for the AI (optional)") },
                    supportingText = { Text("Don't include student names. Any identifiers are removed before sending.") }, minLines = 2)
                Text("Reference resources", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                if (state.bookmarked.isEmpty()) Text("Bookmark your own or licensed resources in Library to cite them here.", style = MaterialTheme.typography.bodySmall)
                ChipSelector(state.bookmarked.map { it.id }, state.references, { id -> state.bookmarked.first { it.id == id }.title }, vm::toggleReference)
            }

            Spacer(Modifier.height(12.dp))
            if (!state.aiEnabled) {
                Text("AI features are off (consent or school setting). An offline template will be used.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = vm::generate, enabled = !state.generating && sel.topicId != null, modifier = Modifier.fillMaxWidth()) {
                if (state.generating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("3 · Generate lesson plan") }
            }
            if (state.generating) Text("Generating with AI. This usually takes 30 seconds to 2 minutes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun PlanRow(p: LessonPlan, onClick: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.titleMedium)
                Text("${p.level.label} · ${p.streams.joinToString(" / ") { it.label }} · v${p.version} · ${p.totalMinutes} min",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    ReviewPill(p.reviewStatus)
                    if (p.sharedWithDepartment) StatusPill("Shared with dept")
                    p.tags.take(3).forEach { StatusPill("#$it") }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More actions for ${p.title}") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                }
            }
        }
    }
}

@Composable
fun ReviewPill(status: ReviewStatus) = when (status) {
    ReviewStatus.DRAFT_AI -> StatusPill("AI draft", color = MaterialTheme.colorScheme.tertiaryContainer)
    ReviewStatus.TEACHER_EDITED -> StatusPill("Edited · needs approval", color = MaterialTheme.colorScheme.tertiaryContainer)
    ReviewStatus.APPROVED -> StatusPill("Approved", color = MaterialTheme.colorScheme.primaryContainer)
    ReviewStatus.REJECTED -> StatusPill("Rejected", color = MaterialTheme.colorScheme.errorContainer)
}
