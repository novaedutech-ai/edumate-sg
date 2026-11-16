package sg.edumate.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import sg.edumate.domain.model.*
import sg.edumate.domain.usecase.SchoolSettingsProvider
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("edumate_prefs")

data class TeacherPrefs(
    val onboarded: Boolean = false,
    val levels: Set<Level> = emptySet(),
    val subjectIds: Set<String> = emptySet(),
    val consentNoticeVersion: Int = 0,
    val aiConsent: Boolean = false,
    val analyticsConsent: Boolean = false,
    val appLockEnabled: Boolean = true,
) {
    val primaryLevel: Level? get() = levels.minOrNull()
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : SchoolSettingsProvider {

    val prefs: Flow<TeacherPrefs> = context.dataStore.data.map { p ->
        TeacherPrefs(
            onboarded = p[ONBOARDED] ?: false,
            levels = p[LEVELS].orEmpty().mapNotNull { runCatching { Level.valueOf(it) }.getOrNull() }.toSet(),
            subjectIds = p[SUBJECTS].orEmpty(),
            consentNoticeVersion = p[CONSENT_VERSION] ?: 0,
            aiConsent = p[AI_CONSENT] ?: false,
            analyticsConsent = p[ANALYTICS_CONSENT] ?: false,
            appLockEnabled = p[APP_LOCK] ?: true,
        )
    }

    val schoolSettings: Flow<SchoolPrivacySettings> = context.dataStore.data.map { p ->
        p[SCHOOL]?.let { runCatching { json.decodeFromString(SchoolPrivacySettings.serializer(), it) }.getOrNull() } ?: DEFAULT_SCHOOL
    }

    override suspend fun current(): SchoolPrivacySettings = schoolSettings.first()

    suspend fun completeOnboarding(levels: Set<Level>, subjects: Set<String>, noticeVersion: Int, ai: Boolean, analytics: Boolean) {
        context.dataStore.edit {
            it[ONBOARDED] = true; it[LEVELS] = levels.map(Level::name).toSet(); it[SUBJECTS] = subjects
            it[CONSENT_VERSION] = noticeVersion; it[AI_CONSENT] = ai; it[ANALYTICS_CONSENT] = analytics
        }
    }

    suspend fun setConsent(ai: Boolean, analytics: Boolean) = context.dataStore.edit { it[AI_CONSENT] = ai; it[ANALYTICS_CONSENT] = analytics }
    suspend fun setAppLock(enabled: Boolean) = context.dataStore.edit { it[APP_LOCK] = enabled }
    suspend fun saveSchoolSettings(s: SchoolPrivacySettings) = context.dataStore.edit { it[SCHOOL] = json.encodeToString(SchoolPrivacySettings.serializer(), s) }
    suspend fun clear() = context.dataStore.edit { it.clear() }

    companion object {
        private val ONBOARDED = booleanPreferencesKey("onboarded")
        private val LEVELS = stringSetPreferencesKey("levels")
        private val SUBJECTS = stringSetPreferencesKey("subjects")
        private val CONSENT_VERSION = intPreferencesKey("consent_version")
        private val AI_CONSENT = booleanPreferencesKey("ai_consent")
        private val ANALYTICS_CONSENT = booleanPreferencesKey("analytics_consent")
        private val APP_LOCK = booleanPreferencesKey("app_lock")
        private val SCHOOL = stringPreferencesKey("school_settings")

        /** Placeholder until provisioned by the school admin console. */
        val DEFAULT_SCHOOL = SchoolPrivacySettings(schoolId = "school-demo", dpoName = "School Data Protection Officer", dpoEmail = "dpo@yourschool.edu.sg")
    }
}
