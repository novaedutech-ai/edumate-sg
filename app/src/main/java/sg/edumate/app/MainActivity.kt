package sg.edumate.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import sg.edumate.app.data.settings.TeacherPrefs
import sg.edumate.app.data.settings.UserPreferencesRepository
import sg.edumate.app.security.AppLockManager
import sg.edumate.app.ui.navigation.EduMateRoot
import sg.edumate.app.ui.onboarding.OnboardingScreen
import sg.edumate.app.ui.theme.EduMateTheme
import javax.inject.Inject

/** FragmentActivity (not ComponentActivity) because BiometricPrompt requires it. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var appLock: AppLockManager
    @Inject lateinit var prefsRepo: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep student data out of screenshots and the recent-apps thumbnail.
        if (!BuildConfig.DEBUG) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            EduMateTheme {
                val prefs by prefsRepo.prefs.collectAsStateWithLifecycle(initialValue = null as TeacherPrefs?)
                val locked by appLock.locked.collectAsStateWithLifecycle()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        prefs == null -> Box(Modifier.fillMaxSize())
                        !prefs!!.onboarded -> OnboardingScreen()
                        locked && prefs!!.appLockEnabled -> LockScreen(onUnlock = ::unlock)
                        else -> EduMateRoot()
                    }
                }
            }
        }
    }

    private fun unlock(onError: (String) -> Unit) {
        appLock.authenticate(this, getString(R.string.unlock_title), getString(R.string.unlock_subtitle), onError)
    }
}

@Composable
private fun LockScreen(onUnlock: ((String) -> Unit) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { onUnlock { error = it } }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
        Text("EduMate is locked", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
        Text("Unlock with your fingerprint, face or device PIN to protect student data.", textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onUnlock { error = it } }, modifier = Modifier.padding(top = 16.dp)) { Text("Unlock") }
    }
}
