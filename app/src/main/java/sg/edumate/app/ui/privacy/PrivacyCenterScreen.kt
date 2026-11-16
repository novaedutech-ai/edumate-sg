@file:OptIn(ExperimentalMaterial3Api::class)

package sg.edumate.app.ui.privacy

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sg.edumate.app.BuildConfig
import sg.edumate.app.data.DevCurrentUser
import sg.edumate.app.data.PrivacyDataService
import sg.edumate.app.data.settings.TeacherPrefs
import sg.edumate.app.data.settings.UserPreferencesRepository
import sg.edumate.app.ui.components.SectionHeader
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.AuditLogRepository
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepo: UserPreferencesRepository,
    private val service: PrivacyDataService,
    audit: AuditLogRepository,
    private val user: DevCurrentUser,
) : ViewModel() {
    val prefs = prefsRepo.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeacherPrefs())
    val school = prefsRepo.schoolSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferencesRepository.DEFAULT_SCHOOL)
    val auditLog = audit.observeRecent(100).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val role = MutableStateFlow(user.role)
    val exportFile = MutableStateFlow<android.net.Uri?>(null)

    fun setAi(v: Boolean) = viewModelScope.launch { prefsRepo.setConsent(v, prefs.value.analyticsConsent) }
    fun setAnalytics(v: Boolean) = viewModelScope.launch { prefsRepo.setConsent(prefs.value.aiConsent, v) }
    fun setAppLock(v: Boolean) = viewModelScope.launch { prefsRepo.setAppLock(v) }
    fun updateSchool(f: (SchoolPrivacySettings) -> SchoolPrivacySettings) = viewModelScope.launch { prefsRepo.saveSchoolSettings(f(school.value)) }

    /** Debug-only role switch to exercise RBAC; production roles come from the IdP token. */
    fun toggleAdminRole() { user.role = if (user.role == Role.SCHOOL_ADMIN) Role.TEACHER else Role.SCHOOL_ADMIN; role.value = user.role }

    fun export() = viewModelScope.launch {
        val json = service.exportJson()
        val uri = withContext(Dispatchers.IO) {
            val f = File(File(context.cacheDir, "exports").apply { mkdirs() }, "edumate-my-data.json"); f.writeText(json)
            FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        }
        exportFile.value = uri
    }

    fun deleteAll() = viewModelScope.launch { service.deleteAllLocalData() }
}

private fun Long.fmt() = Instant.ofEpochMilli(this).atZone(ZoneId.of("Asia/Singapore")).format(DateTimeFormatter.ofPattern("d MMM HH:mm"))

@Composable
fun PrivacyCenterScreen(onBack: () -> Unit, vm: PrivacyViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val school by vm.school.collectAsStateWithLifecycle()
    val audit by vm.auditLog.collectAsStateWithLifecycle()
    val role by vm.role.collectAsStateWithLifecycle()
    val exportUri by vm.exportFile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(exportUri) {
        exportUri?.let { uri ->
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Export my data"))
            vm.exportFile.value = null
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Privacy & data") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Data Protection Officer", style = MaterialTheme.typography.titleMedium)
                        Text(school.dpoName); Text(school.dpoEmail); school.dpoPhone?.let { Text(it) }
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${school.dpoEmail}")).putExtra(Intent.EXTRA_SUBJECT, "Personal data request"))
                        }) { Text("Contact the DPO") }
                    }
                }
            }
            item { SectionHeader("Privacy notice (v${PrivacyNotice.VERSION})") }
            items(PrivacyNotice.sections) { (h, b) ->
                Text(h, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Text(b, style = MaterialTheme.typography.bodyMedium)
            }
            item { SectionHeader("Your choices") }
            item { ToggleRow("AI features", "Uses the AI service with identifiers removed", prefs.aiConsent, vm::setAi) }
            item { ToggleRow("Anonymous usage analytics", "Crash and feature usage, no personal data", prefs.analyticsConsent, vm::setAnalytics) }
            item { ToggleRow("App lock", "Fingerprint, face or device PIN", prefs.appLockEnabled, vm::setAppLock, enabled = !school.requireAppLock) }
            item { SectionHeader("Your data") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::export) { Icon(Icons.Outlined.Download, null); Text(" Export my data") }
                    OutlinedButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Outlined.DeleteForever, null); Text(" Delete all")
                    }
                }
            }
            if (role == Role.SCHOOL_ADMIN) {
                item { SectionHeader("School admin settings") }
                item { ToggleRow("Allow real student names", "Off = pseudonyms only (recommended)", school.allowRealStudentNames, { v -> vm.updateSchool { it.copy(allowRealStudentNames = v) } }) }
                item { ToggleRow("Allow direct teacher–student messages", "Off = class announcements only", school.allowDirectStudentMessaging, { v -> vm.updateSchool { it.copy(allowDirectStudentMessaging = v) } }) }
                item { ToggleRow("AI features for staff", null, school.aiFeaturesEnabled, { v -> vm.updateSchool { it.copy(aiFeaturesEnabled = v) } }) }
                item { ToggleRow("Leaderboards", "Team-based only by default", school.allowLeaderboards, { v -> vm.updateSchool { it.copy(allowLeaderboards = v) } }) }
                item { ToggleRow("Require app lock", null, school.requireAppLock, { v -> vm.updateSchool { it.copy(requireAppLock = v) } }) }
                item {
                    Text("Retention: live responses ${school.retention.liveResponsesDays}d · drafts ${school.retention.aiDraftsUnreviewedDays}d · " +
                        "rosters ${school.retention.rosterAfterClassEndDays}d after class ends · audit ${school.retention.auditLogDays}d",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            item { SectionHeader("Audit log (AI requests, exports, deletions)") }
            if (audit.isEmpty()) item { Text("No entries yet.", style = MaterialTheme.typography.bodySmall) }
            items(audit, key = { it.id }) { e ->
                ListItem(
                    headlineContent = { Text(e.action.name.lowercase().replace('_', ' ')) },
                    supportingContent = { Text("${e.atEpochMs.fmt()} · ${e.detail}${if (e.piiTokensReplaced > 0) " · ${e.piiTokensReplaced} identifier(s) removed" else ""}") },
                )
            }
            if (BuildConfig.DEBUG) item {
                TextButton(onClick = vm::toggleAdminRole) { Text(if (role == Role.SCHOOL_ADMIN) "Debug: act as teacher" else "Debug: act as school admin") }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete all data on this device?") },
        text = { Text("Lesson plans, worksheets, class lists and settings will be permanently removed. Export first if you want a copy.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteAll() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) ({ Text(subtitle) }) else null,
        trailingContent = { Switch(checked, onChange, enabled = enabled) },
    )
}
