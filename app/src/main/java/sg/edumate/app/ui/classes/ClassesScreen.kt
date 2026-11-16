@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package sg.edumate.app.ui.classes

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sg.edumate.app.data.settings.UserPreferencesRepository
import sg.edumate.app.ui.components.*
import sg.edumate.app.work.WorkScheduler
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.ClassRepository
import sg.edumate.domain.usecase.ImportRosterUseCase
import sg.edumate.domain.util.Clock
import sg.edumate.domain.util.IdGenerator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClassesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ClassRepository,
    private val importRoster: ImportRosterUseCase,
    prefs: UserPreferencesRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
) : ViewModel() {
    val classes = repo.observeClasses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selectedId = MutableStateFlow<String?>(null)
    private val effectiveId = combine(selectedId, classes) { sel, cls -> sel ?: cls.firstOrNull()?.id }
    val students = effectiveId.flatMapLatest { id -> id?.let(repo::observeStudents) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val assignments = combine(repo.observeAssignments(), effectiveId) { a, id -> a.filter { it.classId == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val announcements = effectiveId.flatMapLatest { id -> id?.let(repo::observeAnnouncements) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val school = prefs.schoolSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferencesRepository.DEFAULT_SCHOOL)
    val message = MutableStateFlow<String?>(null)
    val currentClassId get() = selectedId.value ?: classes.value.firstOrNull()?.id

    fun select(id: String) { selectedId.value = id }

    fun importCsv(uri: Uri) = viewModelScope.launch {
        val classId = currentClassId ?: return@launch
        val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.orEmpty()
        val r = importRoster(text, classId)
        message.value = buildString {
            append("Imported ${r.students.size} students as pseudonyms")
            if (r.skippedRows > 0) append(" · ${r.skippedRows} rows skipped")
            if (r.rejectedColumns.isNotEmpty()) append(" · ignored columns: ${r.rejectedColumns.joinToString()} (not collected)")
        }
    }

    fun addAssignment(title: String, daysFromNow: Int) = viewModelScope.launch {
        val classId = currentClassId ?: return@launch
        val total = students.value.size
        val a = Assignment(ids.newId(), classId, title, clock.nowMs() + daysFromNow * 86_400_000L, submitted = 0, total = total, toMark = 0)
        repo.upsertAssignment(a)
        WorkScheduler.scheduleReminder(context, a, clock.nowMs())
        message.value = "Assignment added · reminder set for 24h before due"
    }

    fun postAnnouncement(body: String, audience: Audience, scheduleTomorrow: Boolean) = viewModelScope.launch {
        val classId = currentClassId ?: return@launch
        val tomorrow7am = java.time.LocalDate.now(SG).plusDays(1).atTime(7, 0).atZone(SG).toInstant().toEpochMilli()
        repo.upsertAnnouncement(Announcement(ids.newId(), classId, audience, body,
            scheduledForEpochMs = if (scheduleTomorrow) tomorrow7am else null,
            sentAtEpochMs = if (scheduleTomorrow) null else clock.nowMs(), recipientCount = students.value.size))
        message.value = if (scheduleTomorrow) "Scheduled for 7:00 am tomorrow" else "Posted"
    }
}

private val SG = ZoneId.of("Asia/Singapore")
private fun Long.fmt(p: String) = Instant.ofEpochMilli(this).atZone(SG).format(DateTimeFormatter.ofPattern(p))

val ANNOUNCEMENT_TEMPLATES = listOf(
    "Reminder: Please bring your calculator and file for tomorrow's lesson.",
    "Homework for this week has been posted. Please submit by Friday.",
    "Dear parents/guardians, your child's class will be having a learning journey next week. Details to follow.",
    "Well done, class! Our quiz results showed great improvement as a team.",
)

@Composable
fun ClassesScreen(vm: ClassesViewModel = hiltViewModel()) {
    val classes by vm.classes.collectAsStateWithLifecycle()
    val selected by vm.selectedId.collectAsStateWithLifecycle()
    val students by vm.students.collectAsStateWithLifecycle()
    val assignments by vm.assignments.collectAsStateWithLifecycle()
    val announcements by vm.announcements.collectAsStateWithLifecycle()
    val school by vm.school.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importCsv) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    val currentId = selected ?: classes.firstOrNull()?.id

    Scaffold(topBar = { TopAppBar(title = { Text("Classes") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding)) {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                classes.forEach { c -> FilterChip(c.id == currentId, { vm.select(c.id) }, label = { Text(c.name) }) }
            }
            TabRow(selectedTabIndex = tab) {
                listOf("Roster", "Assignments", "Announcements").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (tab) {
                    0 -> {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) }) {
                                    Icon(Icons.Outlined.UploadFile, null); Text(" Import CSV")
                                }
                            }
                            Text(
                                if (school.allowRealStudentNames) "Your school allows real names. They stay encrypted on this device and are never sent to AI."
                                else "Students are shown as pseudonyms. NRIC/FIN, emails and phone numbers are never imported.",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        items(students, key = { it.pseudonymId }) { st ->
                            ListItem(
                                headlineContent = { Text(if (school.allowRealStudentNames) st.displayName else st.alias) },
                                supportingContent = { Text("${st.pseudonymId}${st.stream?.let { " · ${it.label}" } ?: ""}") },
                                leadingContent = { Icon(Icons.Outlined.Person, null) },
                            )
                        }
                    }
                    1 -> {
                        item { Button(onClick = { dialog = "assignment" }) { Icon(Icons.Outlined.Add, null); Text(" New assignment") } }
                        if (assignments.isEmpty()) item { EmptyState("No assignments", "Create one and EduMate will remind you before it's due.") }
                        items(assignments, key = { it.id }) { a ->
                            ListItem(
                                headlineContent = { Text(a.title) },
                                supportingContent = {
                                    Column {
                                        Text("Due ${a.dueEpochMs.fmt("EEE d MMM, h:mm a")} · ${a.submitted}/${a.total} submitted · ${a.toMark} to mark")
                                        a.rubric?.let { r -> Text("Rubric: ${r.criteria.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall) }
                                    }
                                },
                                leadingContent = { Icon(Icons.AutoMirrored.Outlined.Assignment, null) },
                            )
                        }
                    }
                    else -> {
                        item {
                            Button(onClick = { dialog = "announcement" }) { Icon(Icons.Outlined.Campaign, null); Text(" New announcement") }
                            if (!school.allowDirectStudentMessaging) Text("Class-wide announcements only. Direct messages to individual students are disabled by your school.",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        items(announcements, key = { it.id }) { a ->
                            ListItem(
                                headlineContent = { Text(a.body) },
                                supportingContent = {
                                    Text("${a.audience.name.lowercase().replaceFirstChar { it.uppercase() }} · " +
                                        (a.sentAtEpochMs?.let { "sent ${it.fmt("d MMM h:mm a")} · read ${a.readCount}/${a.recipientCount}" }
                                            ?: "scheduled ${a.scheduledForEpochMs?.fmt("d MMM h:mm a")}"))
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        "assignment" -> AssignmentDialog(onDismiss = { dialog = null }) { t, d -> vm.addAssignment(t, d); dialog = null }
        "announcement" -> AnnouncementDialog(onDismiss = { dialog = null }) { b, a, s -> vm.postAnnouncement(b, a, s); dialog = null }
    }
}

@Composable
private fun AssignmentDialog(onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(3) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New assignment") }, text = {
        Column {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
            Text("Due in $days day(s)", modifier = Modifier.padding(top = 8.dp))
            Slider(days.toFloat(), { days = it.toInt() }, valueRange = 1f..14f, steps = 12)
        }
    }, confirmButton = { TextButton(onClick = { onCreate(title, days) }, enabled = title.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun AnnouncementDialog(onDismiss: () -> Unit, onPost: (String, Audience, Boolean) -> Unit) {
    var body by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf(Audience.STUDENTS) }
    var schedule by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New announcement") }, text = {
        Column {
            Text("Templates", style = MaterialTheme.typography.labelLarge)
            ANNOUNCEMENT_TEMPLATES.forEach { t -> TextButton(onClick = { body = t }) { Text(t, style = MaterialTheme.typography.bodySmall) } }
            OutlinedTextField(body, { body = it }, label = { Text("Message") }, minLines = 3)
            ChipSelector(Audience.entries, setOf(audience), { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, { audience = it })
            Row { Checkbox(schedule, { schedule = it }); Text("Schedule for 7:00 am tomorrow", Modifier.padding(top = 12.dp)) }
        }
    }, confirmButton = { TextButton(onClick = { onPost(body, audience, schedule) }, enabled = body.isNotBlank()) { Text(if (schedule) "Schedule" else "Post") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
