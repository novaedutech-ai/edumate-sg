@file:OptIn(ExperimentalLayoutApi::class)

package sg.edumate.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import sg.edumate.app.data.curriculum.MockCurriculumRepository
import sg.edumate.app.data.settings.UserPreferencesRepository
import sg.edumate.app.ui.components.ChipSelector
import sg.edumate.app.ui.components.ContentWidth
import sg.edumate.app.ui.privacy.PrivacyNotice
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.AuditLogger
import sg.edumate.domain.util.Clock
import sg.edumate.domain.util.IdGenerator
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    val curriculum: MockCurriculumRepository,
    private val audit: AuditLogger,
    private val clock: Clock,
    private val ids: IdGenerator,
) : ViewModel() {
    fun finish(levels: Set<Level>, subjects: Set<String>, ai: Boolean, analytics: Boolean) = viewModelScope.launch {
        audit.log(AuditEntry(ids.newId(), clock.nowMs(), Role.TEACHER, AuditAction.CONSENT_GIVEN,
            "notice v${PrivacyNotice.VERSION} ai=$ai analytics=$analytics"))
        prefs.completeOnboarding(levels, subjects, PrivacyNotice.VERSION, ai, analytics)
    }
}

@Composable
fun OnboardingScreen(vm: OnboardingViewModel = hiltViewModel()) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var band by rememberSaveable { mutableStateOf(LevelBand.SECONDARY) }
    var levels by remember { mutableStateOf(setOf(Level.SEC1)) }
    var subjects by remember { mutableStateOf(setOf("math")) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var ai by rememberSaveable { mutableStateOf(true) }
    var analytics by rememberSaveable { mutableStateOf(false) }

    ContentWidth(Modifier.safeDrawingPadding()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            when (step) {
                0 -> {
                    Text("Welcome to EduMate SG", style = MaterialTheme.typography.headlineMedium)
                    Text("Plan lessons, create differentiated worksheets and run live checks for understanding – aligned to the syllabus you teach.",
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 12.dp))
                    Text("What do you teach?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    ChipSelector(LevelBand.entries, setOf(band), { when (it) { LevelBand.PRIMARY -> "Primary"; LevelBand.SECONDARY -> "Secondary"; LevelBand.PRE_U -> "JC / MI" } },
                        { band = it; levels = emptySet(); subjects = emptySet() })
                    Text("Levels", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    ChipSelector(Level.entries.filter { it.band == band }, levels, { it.label }, { l -> levels = if (l in levels) levels - l else levels + l })
                    Text("Subjects", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    ChipSelector(vm.curriculum.subjects(band), vm.curriculum.subjects(band).filter { it.id in subjects }.toSet(), { it.name },
                        { s -> subjects = if (s.id in subjects) subjects - s.id else subjects + s.id })
                    Button(onClick = { step = 1 }, enabled = levels.isNotEmpty() && subjects.isNotEmpty(), modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) { Text("Continue") }
                }
                1 -> {
                    Text("How we protect your students", style = MaterialTheme.typography.headlineMedium)
                    PrivacyPoint(Icons.Outlined.Badge, "Pseudonyms by default", "Students appear as 'Student 07'. We never collect NRIC/FIN or student contact details.")
                    PrivacyPoint(Icons.Outlined.AutoAwesome, "AI never sees identities", "Names and identifiers are removed on your device before anything is sent to AI. Every AI draft needs your approval before students see it.")
                    PrivacyPoint(Icons.Outlined.Lock, "Encrypted and in Singapore", "Encrypted on this device and in transit; stored in Singapore. App lock keeps data safe if your phone is lost.")
                    PrivacyPoint(Icons.Outlined.ManageAccounts, "You're in control", "Export or delete your data anytime from Privacy & data. Contact your school's DPO for any request.")
                    Row(Modifier.padding(top = 24.dp)) {
                        OutlinedButton(onClick = { step = 0 }) { Text("Back") }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { step = 2 }, modifier = Modifier.weight(1f)) { Text("Continue") }
                    }
                }
                else -> {
                    Text("Privacy notice & consent", style = MaterialTheme.typography.headlineMedium)
                    PrivacyNotice.sections.forEach { (h, b) ->
                        Text(h, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                        Text(b, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    CheckRow(acknowledged, { acknowledged = it }, "I have read the privacy notice (required)")
                    CheckRow(ai, { ai = it }, "Enable AI features (identifiers removed before sending)")
                    CheckRow(analytics, { analytics = it }, "Share anonymous usage analytics")
                    Row(Modifier.padding(top = 24.dp)) {
                        OutlinedButton(onClick = { step = 1 }) { Text("Back") }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { vm.finish(levels, subjects, ai, analytics) }, enabled = acknowledged, modifier = Modifier.weight(1f)) { Text("Start using EduMate") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyPoint(icon: ImageVector, title: String, body: String) {
    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column { Text(title, style = MaterialTheme.typography.titleSmall); Text(body, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChange); Text(label) }
}
