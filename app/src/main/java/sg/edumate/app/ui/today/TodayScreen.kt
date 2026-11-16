@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package sg.edumate.app.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Grading
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import sg.edumate.app.ui.components.*
import sg.edumate.app.ui.live.LiveSessionStore
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.ClassRepository
import sg.edumate.domain.repository.LessonPlanRepository
import sg.edumate.domain.repository.ScheduleRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TodayUiState(
    val lessons: List<LessonSlot> = emptyList(),
    val toMark: List<Assignment> = emptyList(),
    val drafts: List<LessonPlan> = emptyList(),
    val pendingInterventions: Int = 0,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    schedule: ScheduleRepository, classes: ClassRepository, plans: LessonPlanRepository, live: LiveSessionStore,
) : ViewModel() {
    val state: StateFlow<TodayUiState> = combine(
        schedule.observeToday(), classes.observeAssignments(), plans.observeAll(), live.pendingInterventions,
    ) { slots, assignments, allPlans, pending ->
        TodayUiState(
            lessons = slots,
            toMark = assignments.filter { it.toMark > 0 }.sortedBy { it.dueEpochMs },
            drafts = allPlans.filter { it.reviewStatus == ReviewStatus.DRAFT_AI || it.reviewStatus == ReviewStatus.TEACHER_EDITED },
            pendingInterventions = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())
}

private val SG = ZoneId.of("Asia/Singapore")
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private fun Long.sgTime() = Instant.ofEpochMilli(this).atZone(SG).format(timeFmt)
private fun Long.sgDay() = Instant.ofEpochMilli(this).atZone(SG).format(DateTimeFormatter.ofPattern("EEE d MMM"))

@Composable
fun TodayScreen(
    onNewLesson: () -> Unit, onNewWorksheet: () -> Unit, onStartLive: () -> Unit,
    onOpenPlan: (String) -> Unit, onOpenPrivacy: () -> Unit,
    vm: TodayViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text("Today"); Text(Instant.now().atZone(SG).format(dayFmt), style = MaterialTheme.typography.bodySmall) } },
            actions = { IconButton(onClick = onOpenPrivacy) { Icon(Icons.Outlined.Shield, contentDescription = "Privacy and data") } },
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onNewLesson) { Icon(Icons.Outlined.AutoAwesome, null); Text(" Lesson plan") }
                    FilledTonalButton(onClick = onNewWorksheet) { Icon(Icons.Outlined.Description, null); Text(" Worksheet") }
                    FilledTonalButton(onClick = onStartLive) { Icon(Icons.Outlined.Sensors, null); Text(" Go live") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Lessons", s.lessons.size.toString(), Modifier.weight(1f))
                    StatCard("To mark", s.toMark.sumOf { it.toMark }.toString(), Modifier.weight(1f))
                    StatCard("Interventions", s.pendingInterventions.toString(), Modifier.weight(1f))
                }
            }
            item { SectionHeader("Today's lessons") }
            if (s.lessons.isEmpty()) item { Text("No lessons scheduled.", style = MaterialTheme.typography.bodyMedium) }
            items(s.lessons, key = { it.id }) { l ->
                ListItem(
                    headlineContent = { Text(l.className) },
                    supportingContent = { Text("${l.startsAtEpochMs.sgTime()} · ${l.durationMin} min${l.room?.let { " · $it" } ?: ""}") },
                    leadingContent = { Icon(Icons.Outlined.Schedule, null) },
                )
            }
            item { SectionHeader("Assignments to mark") }
            if (s.toMark.isEmpty()) item { Text("You're all caught up.", style = MaterialTheme.typography.bodyMedium) }
            items(s.toMark, key = { it.id }) { a ->
                ListItem(
                    headlineContent = { Text(a.title) },
                    supportingContent = {
                        Column {
                            Text("${a.toMark} to mark · ${a.submitted}/${a.total} submitted · due ${a.dueEpochMs.sgDay()}")
                            LinearProgressIndicator(progress = { a.submitted.toFloat() / a.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.Grading, null) },
                )
            }
            if (s.drafts.isNotEmpty()) {
                item { SectionHeader("AI drafts awaiting your review") }
                items(s.drafts.take(5), key = { it.id }) { p ->
                    ListItem(
                        headlineContent = { Text(p.title) },
                        supportingContent = { Text("${p.level.label} · v${p.version}") },
                        leadingContent = { Icon(Icons.Outlined.RateReview, null) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingContent = { TextButton(onClick = { onOpenPlan(p.id) }) { Text("Review") } },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.Start) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
