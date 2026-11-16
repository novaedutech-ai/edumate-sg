package sg.edumate.app.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.Json
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import sg.edumate.app.BuildConfig
import sg.edumate.app.data.DevCurrentUser
import sg.edumate.app.data.curriculum.MockCurriculumRepository
import sg.edumate.app.data.live.SimulatedLiveSessionGateway
import sg.edumate.app.data.live.WebSocketLiveSessionGateway
import sg.edumate.app.data.local.*
import sg.edumate.app.data.remote.AuthInterceptor
import sg.edumate.app.data.remote.EduMateApi
import sg.edumate.app.data.remote.FallbackLlmProvider
import sg.edumate.app.data.remote.RemoteLlmProvider
import sg.edumate.app.data.repository.*
import sg.edumate.app.data.resource.*
import sg.edumate.app.data.settings.UserPreferencesRepository
import sg.edumate.app.security.SecretStore
import sg.edumate.domain.ai.*
import sg.edumate.domain.live.*
import sg.edumate.domain.repository.*
import sg.edumate.domain.usecase.CurrentUser
import sg.edumate.domain.usecase.SchoolSettingsProvider
import sg.edumate.domain.util.Clock
import sg.edumate.domain.util.IdGenerator
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides @Singleton fun json() = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    @Provides fun clock(): Clock = Clock.SYSTEM
    @Provides fun ids(): IdGenerator = IdGenerator.UUIDS
    @Provides @Singleton fun moderator(): ContentModerator = LocalContentModerator()
    @Provides fun reducer(m: ContentModerator) = LiveSessionReducer(m)
    @Provides fun grouping() = GroupingEngine()
    @Provides fun interventions() = InterventionPlanner()
    @Provides fun analytics() = SessionAnalytics()
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context, secrets: SecretStore): EduMateDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(ctx, EduMateDatabase::class.java, "edumate.db")
            .openHelperFactory(SupportOpenHelperFactory(secrets.databasePassphrase()))
            .build()
    }
    @Provides fun plans(db: EduMateDatabase) = db.lessonPlanDao()
    @Provides fun worksheets(db: EduMateDatabase) = db.worksheetDao()
    @Provides fun classes(db: EduMateDatabase) = db.classDao()
    @Provides fun resources(db: EduMateDatabase) = db.resourceDao()
    @Provides fun audit(db: EduMateDatabase) = db.auditDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun okHttp(auth: AuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS)) // TLS 1.2+ with modern ciphers only
        .certificatePinner(
            CertificatePinner.Builder()
                // Replace with the backend's SPKI pins (primary + backup) before release.
                // .add("api.edumate.example.sg", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .build(),
        )
        .addInterceptor(auth)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // generation can take a while
        .pingInterval(20, TimeUnit.SECONDS) // keeps live-session WebSockets warm
        .build()

    @Provides @Singleton
    fun api(client: OkHttpClient, json: Json): EduMateApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(EduMateApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides @Singleton
    fun llm(api: EduMateApi): LlmProvider =
        if (BuildConfig.USE_MOCK_BACKEND) OfflineTemplateProvider()
        else FallbackLlmProvider(RemoteLlmProvider(api), OfflineTemplateProvider())

    @Provides @Singleton
    fun gateway(provider: LlmProvider, moderator: ContentModerator, audit: AuditLogRepository, classes: ClassRepository,
                clock: Clock, ids: IdGenerator, user: CurrentUser): SafeAiGateway =
        SafeAiGateway(provider, moderator, audit, RosterNameSource { classes.allRealNames() }, clock, ids, user.role)

    @Provides @Singleton
    fun live(api: EduMateApi, client: OkHttpClient, json: Json, clock: Clock): LiveSessionGateway =
        if (BuildConfig.USE_MOCK_BACKEND) SimulatedLiveSessionGateway(clock)
        else WebSocketLiveSessionGateway(api, client, json, BuildConfig.LIVE_WS_URL)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun plans(impl: RoomLessonPlanRepository): LessonPlanRepository
    @Binds abstract fun worksheets(impl: RoomWorksheetRepository): WorksheetRepository
    @Binds abstract fun audit(impl: RoomAuditLogRepository): AuditLogRepository
    @Binds abstract fun auditLogger(impl: RoomAuditLogRepository): AuditLogger
    @Binds abstract fun classes(impl: ClassRepositoryImpl): ClassRepository
    @Binds abstract fun schedule(impl: ClassRepositoryImpl): ScheduleRepository
    @Binds abstract fun curriculum(impl: MockCurriculumRepository): CurriculumRepository
    @Binds abstract fun resources(impl: ResourceRepositoryImpl): ResourceRepository
    @Binds abstract fun user(impl: DevCurrentUser): CurrentUser
    @Binds abstract fun school(impl: UserPreferencesRepository): SchoolSettingsProvider
    @Binds @IntoSet abstract fun moe(a: MockMoeLibraryAdapter): ResourceAdapter
    @Binds @IntoSet abstract fun oer(a: OpenLicensedContentAdapter): ResourceAdapter
    @Binds @IntoSet abstract fun uploads(a: UserUploadAdapter): ResourceAdapter
}
