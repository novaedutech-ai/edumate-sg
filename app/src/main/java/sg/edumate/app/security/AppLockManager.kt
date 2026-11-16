package sg.edumate.app.security

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Biometric / device-PIN app lock. Locks after [timeoutMs] in the background. */
@Singleton
class AppLockManager @Inject constructor() {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked
    var enabled: Boolean = true
    private var backgroundedAt: Long? = null
    private val timeoutMs = 60_000L

    fun onBackground() { backgroundedAt = SystemClock.elapsedRealtime() }

    fun onForeground() {
        val since = backgroundedAt ?: return
        if (enabled && SystemClock.elapsedRealtime() - since > timeoutMs) _locked.value = true
        backgroundedAt = null
    }

    fun deviceLockAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, title: String, subtitle: String, onError: (String) -> Unit) {
        if (!enabled || !deviceLockAvailable(activity)) { _locked.value = false; return }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { _locked.value = false }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onError(errString.toString()) }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    private companion object { const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL }
}
