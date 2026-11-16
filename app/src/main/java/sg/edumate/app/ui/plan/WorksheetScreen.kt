@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package sg.edumate.app.ui.plan

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sg.edumate.app.ui.components.*
import sg.edumate.domain.model.*

@Composable
fun WorksheetScreen(onBack: () -> Unit, vm: WorksheetViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    LaunchedEffect(state.shareUri) {
        state.shareUri?.let { uri ->
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share worksheet"))
            vm.consumeShare()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.worksheet == null) "New worksheet" else "Worksheet") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { if (state.worksheet != null) TextButton(onClick = vm::newWorksheet) { Text("New") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        ContentWidth(Modifier.padding(padding)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                val ws = state.worksheet
                if (ws == null) WorksheetForm(state, vm) else WorksheetResult(ws, state, vm)
            }
        }
    }
}

@Composable
private fun WorksheetForm(state: WorksheetUiState, vm: WorksheetViewModel) {
    val sel = state.selection
    Text("1 · Topic", style = MaterialTheme.typography.titleMedium)
    TopicPicker(sel.levels, sel.level, vm::onLevel, sel.subjects, sel.subjectId, vm::onSubject, sel.topics, sel.topicId, vm::onTopic)

    Text("2 · Streams (one coordinated version each)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    ChipSelector(sel.streamsForLevel, state.streams, { it.label }, vm::toggleStream)

    Text("Questions: ${state.questionCount}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
    Slider(state.questionCount.toFloat(), { vm.onCount(it.toInt()) }, valueRange = 3f..20f, steps = 16)
    Text("Question types", style = MaterialTheme.typography.labelLarge)
    ChipSelector(QuestionType.entries, state.types, { it.label }, vm::toggleType)

    Text("Difficulty mix (core version): ${state.foundational}% foundational · ${state.intermediate}% intermediate · ${state.advanced}% advanced",
        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
    Text("Foundational", style = MaterialTheme.typography.bodySmall)
    Slider(state.foundational.toFloat(), { vm.onFoundational((it / 10).toInt() * 10) }, valueRange = 0f..100f, steps = 9)
    Text("Intermediate", style = MaterialTheme.typography.bodySmall)
    Slider(state.intermediate.toFloat(), { vm.onIntermediate((it / 10).toInt() * 10) }, valueRange = 0f..100f, steps = 9)
    Text("Support versions shift toward foundational items; extension versions toward advanced.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)

    Button(onClick = { vm.generate() }, enabled = !state.generating && sel.topicId != null, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        if (state.generating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("3 · Generate ${state.streams.size} versions") }
    }
    if (state.generating) Text("Generating with AI. This usually takes 30 seconds to 2 minutes.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun WorksheetResult(ws: Worksheet, state: WorksheetUiState, vm: WorksheetViewModel) {
    Text(ws.title, style = MaterialTheme.typography.headlineSmall)
    AiProvenanceCard(ws.provenance, ws.reviewStatus, Modifier.padding(vertical = 8.dp))

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = { vm.generate() }, label = { Text("Regenerate") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) })
        AssistChip(onClick = { vm.generate(Refinement.MAKE_EASIER) }, label = { Text("Make easier") })
        AssistChip(onClick = { vm.generate(Refinement.ADD_EXTENSION) }, label = { Text("Add extension") })
        AssistChip(onClick = { vm.generate(Refinement.MORE_SG_CONTEXT) }, label = { Text("More SG context") })
    }

    TabRow(selectedTabIndex = state.versionIndex, modifier = Modifier.padding(top = 8.dp)) {
        ws.versions.forEachIndexed { i, v -> Tab(i == state.versionIndex, onClick = { vm.selectVersion(i) }, text = { Text(v.stream.label) }) }
    }
    val v = ws.versions[state.versionIndex.coerceIn(0, ws.versions.lastIndex)]

    SectionHeader("Quality checks")
    v.checks.forEach { c ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            Icon(if (c.passed) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, contentDescription = if (c.passed) "Passed" else "Needs attention",
                tint = if (c.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Column { Text(c.name, style = MaterialTheme.typography.labelLarge); Text(c.detail, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (v.checks.any { it.name == "SG English spelling" && !it.passed }) TextButton(onClick = vm::applySpellingFixes) { Text("Apply British spelling fixes") }

    SectionHeader("Questions · ${v.totalMarks} marks") {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Answer key", style = MaterialTheme.typography.labelMedium); Switch(state.showAnswers, { vm.toggleAnswers() }) }
    }
    v.questions.forEach { q ->
        OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill("Q${q.number}"); StatusPill(q.type.label); StatusPill(q.difficulty.name.lowercase())
                    if (q.isChallenge) StatusPill("Challenge", color = MaterialTheme.colorScheme.tertiaryContainer)
                    StatusPill("${q.marks}m")
                }
                Text(q.prompt, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
                q.options.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                q.scaffolds.forEach { s ->
                    Text("${s.kind.label}: ${s.content}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp))
                }
                Text("Outcome ${q.outcomeCode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.showAnswers) v.answerKey.firstOrNull { it.questionId == q.id }?.let { a ->
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Answer: ${a.answer}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    a.markingPoints.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }

    if (ws.misconceptions.isNotEmpty()) {
        SectionHeader("Common misconceptions")
        ws.misconceptions.forEach { m -> Text("• ${m.description}\n   Remedy: ${m.remedy}", style = MaterialTheme.typography.bodyMedium) }
    }

    SectionHeader("Review & export")
    val approved = ws.reviewStatus.shareableWithStudents
    if (!approved) {
        Text("Review every version, then approve. Export and sharing unlock after approval.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.approve() }, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Outlined.CheckCircle, null); Text(" Approve all versions") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = { vm.exportPdf(false) }, enabled = approved) { Icon(Icons.Outlined.PictureAsPdf, null); Text(" PDF (${v.stream.label})") }
        OutlinedButton(onClick = { vm.exportPdf(true) }, enabled = approved) { Text("PDF + answer key") }
        // Google Docs/Slides and DOCX exports go through POST /v1/exports once Workspace is connected.
        OutlinedButton(onClick = {}, enabled = false) { Text("Google Docs (connect Workspace)") }
        OutlinedButton(onClick = {}, enabled = false) { Text("DOCX (v1)") }
    }
    Spacer(Modifier.height(32.dp))
}
