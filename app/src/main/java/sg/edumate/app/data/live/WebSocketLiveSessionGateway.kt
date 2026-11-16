package sg.edumate.app.data.live

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import sg.edumate.app.data.remote.CreateSessionRequest
import sg.edumate.app.data.remote.EduMateApi
import sg.edumate.domain.model.*
import sg.edumate.domain.repository.LiveSessionGateway
import sg.edumate.domain.repository.LiveSessionHandle

/**
 * Production transport: REST to create the session, then a WebSocket (wss, TLS 1.2+) for events.
 * Message schema: docs/05-api/openapi.yaml `components.schemas.LiveMessage`. Sized for 45 students;
 * the server fans out per-session and targets < 1 s end-to-end latency.
 */
class WebSocketLiveSessionGateway(
    private val api: EduMateApi,
    private val client: OkHttpClient,
    private val json: Json,
    private val wsUrl: String,
) : LiveSessionGateway {

    override suspend fun start(activityTitle: String, classId: String?): LiveSessionHandle {
        val created = api.createSession(CreateSessionRequest(activityTitle, classId))
        return Handle(created.sessionId, created.joinCode, created.wsTicket)
    }

    private inner class Handle(override val sessionId: String, override val joinCode: String, private val ticket: String) : LiveSessionHandle {
        private var socket: WebSocket? = null

        override val events: Flow<LiveEvent> = callbackFlow {
            val request = Request.Builder().url("$wsUrl/$sessionId?role=teacher").header("Sec-WebSocket-Protocol", "edumate.v1, $ticket").build()
            val ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { json.decodeFromString(WireEvent.serializer(), text).toDomain() }.getOrNull()?.let { trySend(it) }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close(t) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
            })
            socket = ws
            awaitClose { ws.close(1000, "teacher left") }
        }

        override suspend fun pushIntervention(intervention: Intervention) {
            socket?.send(json.encodeToString(OutIntervention.serializer(),
                OutIntervention(intervention.id, intervention.title, intervention.content, intervention.targetStudentIds)))
        }
        override suspend fun assignGroups(groups: List<StudentGroup>) {
            socket?.send(json.encodeToString(OutGroups.serializer(), OutGroups(groups.map { OutGroup(it.id, it.name, it.memberIds, it.task) })))
        }
        override suspend fun end() { socket?.send("""{"type":"end"}"""); socket?.close(1000, "ended") }
    }

    @Serializable
    private data class WireEvent(
        val type: String, val studentId: String, val atMs: Long,
        val alias: String? = null, val stream: Stream? = null, val status: StudentStatus? = null,
        val blockId: String? = null, val text: String? = null, val confidence: Int? = null, val correct: Boolean? = null,
    ) {
        fun toDomain(): LiveEvent? = when (type) {
            "joined" -> LiveEvent.Joined(studentId, alias ?: "Student", stream, atMs)
            "status" -> status?.let { LiveEvent.StatusChanged(studentId, it, atMs) }
            "response" -> LiveEvent.ResponseSubmitted(studentId, blockId ?: "", text.orEmpty(), confidence ?: 3, correct, atMs)
            "help" -> LiveEvent.HelpRequested(studentId, atMs)
            "left" -> LiveEvent.Left(studentId, atMs)
            else -> null
        }
    }

    @Serializable private data class OutIntervention(val id: String, val title: String, val content: String, val targets: List<String>,
                                                     @SerialName("type") val type: String = "intervention")
    @Serializable private data class OutGroup(val id: String, val name: String, val members: List<String>, val task: String?)
    @Serializable private data class OutGroups(val groups: List<OutGroup>, @SerialName("type") val type: String = "groups")
}
