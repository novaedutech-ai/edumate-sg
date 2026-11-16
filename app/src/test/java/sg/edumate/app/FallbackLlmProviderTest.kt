package sg.edumate.app

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import sg.edumate.app.data.remote.EduMateApi
import sg.edumate.app.data.remote.FallbackLlmProvider
import sg.edumate.app.data.remote.RemoteLlmProvider
import sg.edumate.domain.ai.LessonPlanRequest
import sg.edumate.domain.ai.OfflineTemplateProvider
import sg.edumate.domain.model.Stream

class FallbackLlmProviderTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun api(server: MockWebServer) = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(EduMateApi::class.java)

    private val req = LessonPlanRequest("r1", TOPIC.level, "Mathematics", TOPIC, listOf(Stream.G2), 60)

    @Test fun usesBackendWhenOnline() = runTest {
        val server = MockWebServer().apply { start() }
        val draft = OfflineTemplateProvider().lessonPlan(req).copy(title = "From backend")
        server.enqueue(MockResponse().setBody(json.encodeToString(sg.edumate.domain.ai.LessonPlanDraft.serializer(), draft)))
        val p = FallbackLlmProvider(RemoteLlmProvider(api(server)), OfflineTemplateProvider())
        assertEquals("From backend", p.lessonPlan(req).title)
        assertEquals("edumate-backend-v1", p.id)
        val sent = server.takeRequest()
        assertEquals("/v1/ai/lesson-plans", sent.path)
        assertTrue(sent.body.readUtf8().contains("\"requestId\":\"r1\""))
        server.shutdown()
    }

    @Test fun fallsBackToTemplateWhenOffline() = runTest {
        val server = MockWebServer().apply { start() }
        val api = api(server); server.shutdown() // connection refused -> IOException
        val p = FallbackLlmProvider(RemoteLlmProvider(api), OfflineTemplateProvider())
        val out = p.lessonPlan(req)
        assertEquals(OfflineTemplateProvider.ID, p.id)
        assertTrue(out.title.contains("Algebraic expressions"))
    }
}
