@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package sg.edumate.app.ui.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sg.edumate.app.ui.components.*
import sg.edumate.domain.model.*

@Composable
fun LessonEditorScreen(
    onBack: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onLaunchLive: (String) -> Unit,
    vm: LessonEditorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    LaunchedEffect(state.newVersionId) { state.newVersionId?.let { vm.consumeNavigation(); onOpenPlan(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.plan?.let { "Lesson plan · v${it.version}" } ?: "Lesson plan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = vm::saveEdits, enabled = state.dirty) { Text("Save") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { state.plan?.let { ReviewBar(it, state.dirty, vm, onLaunchLive) } },
    ) { padding ->
        val plan = state.plan ?: run { Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }; return@Scaffold }
        ContentWidth(Modifier.padding(padding)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                AiProvenanceCard(plan.provenance, plan.reviewStatus)
                RefineRow(state, vm)

                OutlinedTextField(plan.title, vm::title, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Title") })
                Text("${plan.level.label} · ${plan.streams.joinToString(" / ") { it.label }} · ${plan.durationMin} min planned · ${plan.totalMinutes} min in timeline",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))

                SectionHeader("Learning objectives"); EditableList(plan.objectives, vm::objectives, "Objective")
                SectionHeader("Prior knowledge"); EditableList(plan.priorKnowledge, vm::priorKnowledge, "Item")
                SectionHeader("Success criteria"); EditableList(plan.successCriteria, vm::successCriteria, "Criterion")

                SectionHeader("Lesson flow") { AddBlockMenu(vm::addBlock) }
                plan.flow.forEachIndexed { i, b -> BlockCard(b, i, plan.flow.size, vm) }

                SectionHeader("Differentiation by stream")
                plan.streams.map { it.tier }.distinct().forEach { tier ->
                    val stream = plan.streams.first { it.tier == tier }
                    OutlinedTextField(
                        value = plan.differentiation.firstOrNull { it.tier == tier }?.description.orEmpty(),
                        onValueChange = { vm.differentiation(tier, it) }, minLines = 2,
                        label = { Text("${stream.label} (${tier.name.lowercase().replaceFirstChar { it.uppercase() }})") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }

                SectionHeader("Assessment for learning checks"); EditableList(plan.aflChecks, vm::aflChecks, "Check")

                SectionHeader("21st Century Competencies")
                ChipSelector(Competency21.entries, plan.competencies.toSet(), { it.label }, vm::toggleCompetency)

                SectionHeader("Tags & sharing")
                var tagText by remember(plan.id) { mutableStateOf(plan.tags.joinToString(", ")) }
                OutlinedTextField(tagText, { tagText = it; vm.setTags(it) }, Modifier.fillMaxWidth(), label = { Text("Tags (comma separated)") })
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Switch(plan.sharedWithDepartment, onCheckedChange = { vm.toggleShare() })
                    Spacer(Modifier.width(8.dp))
                    Text("Share with my department (colleagues can view and duplicate)")
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun RefineRow(state: EditorUiState, vm: LessonEditorViewModel) {
    Column(Modifier.padding(top = 8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.refine(null) }, enabled = !state.busy, label = { Text("Regenerate") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) })
            Refinement.entries.forEach { r -> AssistChip(onClick = { vm.refine(r) }, enabled = !state.busy, label = { Text(r.label) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            Text("Tone:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically))
            Tone.entries.forEach { t -> FilterChip(t == state.tone, onClick = { vm.setTone(t) }, label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Refining creates a new version; this one is kept.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddBlockMenu(onAdd: (BlockType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { open = true }) { Icon(Icons.Outlined.Add, null); Text("Add block") }
        DropdownMenu(open, { open = false }) {
            BlockType.entries.forEach { t -> DropdownMenuItem(text = { Text(t.label) }, onClick = { open = false; onAdd(t) }) }
        }
    }
}

/**
 * One block in the lesson timeline. Reordering uses explicit move buttons, which also work with
 * TalkBack and switch access (a drag gesture is planned as an additional affordance).
 */
@Composable
private fun BlockCard(b: LessonBlock, index: Int, count: Int, vm: LessonEditorViewModel) {
    OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(b.type.label)
                b.tier?.let { Spacer(Modifier.width(6.dp)); StatusPill(it.name.lowercase(), color = MaterialTheme.colorScheme.primaryContainer) }
                if (b.isCheckForUnderstanding) { Spacer(Modifier.width(6.dp)); StatusPill("Check for understanding", color = MaterialTheme.colorScheme.tertiaryContainer) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.moveBlock(b.id, -1) }, enabled = index > 0, modifier = Modifier.semantics { contentDescription = "Move ${b.title} up" }) {
                    Icon(Icons.Outlined.ArrowUpward, null)
                }
                IconButton(onClick = { vm.moveBlock(b.id, 1) }, enabled = index < count - 1, modifier = Modifier.semantics { contentDescription = "Move ${b.title} down" }) {
                    Icon(Icons.Outlined.ArrowDownward, null)
                }
                IconButton(onClick = { vm.removeBlock(b.id) }, modifier = Modifier.semantics { contentDescription = "Remove ${b.title}" }) {
                    Icon(Icons.Outlined.Delete, null)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(b.title, { vm.updateBlock(b.copy(title = it)) }, Modifier.weight(1f), label = { Text("Block title") }, singleLine = true)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(b.durationMin.toString(), { v -> v.toIntOrNull()?.let { vm.updateBlock(b.copy(durationMin = it.coerceIn(1, 120))) } },
                    Modifier.width(88.dp), label = { Text("min") }, singleLine = true)
            }
            OutlinedTextField(b.instructions, { vm.updateBlock(b.copy(instructions = it)) }, Modifier.fillMaxWidth().padding(top = 4.dp),
                label = { Text("Instructions") }, minLines = 2)
            if (b.type == BlockType.MCQ || b.type == BlockType.POLL || b.type == BlockType.SORTING) {
                b.options.forEachIndexed { i, o ->
                    OutlinedTextField(o, { v -> vm.updateBlock(b.copy(options = b.options.toMutableList().also { it[i] = v })) },
                        Modifier.fillMaxWidth(), label = { Text("Option ${'A' + i}") }, singleLine = true)
                }
                TextButton(onClick = { vm.updateBlock(b.copy(options = b.options + "")) }) { Text("+ Add option") }
            }
        }
    }
}

@Composable
private fun ReviewBar(plan: LessonPlan, dirty: Boolean, vm: LessonEditorViewModel, onLaunchLive: (String) -> Unit) {
    BottomAppBar {
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = vm::reject, enabled = plan.reviewStatus != ReviewStatus.REJECTED) { Icon(Icons.Outlined.Cancel, null); Text(" Reject") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = vm::approve, enabled = plan.reviewStatus != ReviewStatus.APPROVED || dirty) { Icon(Icons.Outlined.CheckCircle, null); Text(" Approve") }
        Spacer(Modifier.weight(1f))
        // Students only ever see approved content.
        FilledTonalButton(onClick = { onLaunchLive(plan.id) }, enabled = plan.reviewStatus.shareableWithStudents && !dirty) {
            Icon(Icons.Outlined.Sensors, null); Text(" Teach live")
        }
        Spacer(Modifier.width(8.dp))
    }
}
