package com.example.chatserver

import com.example.chat.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Cau hinh dung chung cho cac test tich hop: Ktor routing that + PostgreSQL that
 * (CSDL chat_test trong docker — tach biet voi database dev). Yeu cau docker compose up -d.
 */
object TestDb {
    val db: Database by lazy {
        DriverManager.getConnection("jdbc:postgresql://localhost:5433/chat", "chat", "chat").use { c ->
            val exists = c.query("SELECT 1 FROM pg_database WHERE datname = 'chat_test'") { true }.isNotEmpty()
            if (!exists) c.createStatement().use { it.execute("CREATE DATABASE chat_test") }
        }
        Database.connect("jdbc:postgresql://localhost:5433/chat_test", "chat", "chat").also { it.migrate() }
    }

    fun clean() = runBlocking {
        db.tx { it.update("TRUNCATE messages, conversation_members, conversations, users RESTART IDENTITY CASCADE") }
        Unit
    }
}

/** Khoi dong toan bo server module trong tien trinh test va cung cap HttpClient ho tro JSON va WebSocket. */
fun chatTest(
    presenceTimeout: Duration = 30.seconds,
    presenceSweep: Duration = 5.seconds,
    block: suspend (HttpClient) -> Unit,
) = testApplication {
    application { module(TestDb.db, jwtSecret = "test-secret-at-least-16-chars", chatServers = listOf("ws://test/ws"), presenceTimeout, presenceSweep) }
    block(createClient {
        install(ContentNegotiation) { json(Protocol.json) }
        install(WebSockets)
    })
}

suspend fun HttpClient.postJson(path: String, body: Any, token: String? = null): HttpResponse = post(path) {
    contentType(ContentType.Application.Json)
    setBody(body)
    token?.let { bearerAuth(it) }
}

suspend fun HttpClient.getAuth(path: String, token: String): HttpResponse = get(path) { bearerAuth(token) }

suspend fun HttpClient.register(username: String, displayName: String = username.replaceFirstChar { it.uppercase() }): AuthResponse {
    val res = postJson("/api/auth/register", RegisterRequest(username, displayName, "secret123"))
    assertEquals(HttpStatusCode.Created, res.status)
    return res.body()
}

suspend fun HttpResponse.errorCode(): String = body<ErrorResponse>().error

// ---- Cac ham tro giup WebSocket ----

/** Mo mot socket chat duoi danh nghia user, giong nhu mot thiet bi, va cho den khi server dang ky thanh cong. */
suspend fun HttpClient.connect(token: String): DefaultClientWebSocketSession =
    webSocketSession("/ws") { bearerAuth(token) }.also { it.expect<ConnectedEvent>() }

suspend fun DefaultClientWebSocketSession.emit(event: ClientEvent) = send(Protocol.encode(event))

/**
 * Event tiep theo tu server, bao loi neu khong co event nao trong [timeoutMs].
 * Mac dinh bo qua presence tru khi [presence] = true: moi ket noi deu sinh presence, ma phan lon test chi quan tam toi tin nhan.
 */
suspend fun DefaultClientWebSocketSession.next(presence: Boolean = false, timeoutMs: Long = 5_000): ServerEvent = withTimeout(timeoutMs) {
    while (true) {
        val frame = incoming.receive()
        if (frame !is Frame.Text) continue
        val ev = Protocol.decodeServer(frame.readText())
        if (ev is PresenceEvent && !presence) continue
        return@withTimeout ev
    }
    @Suppress("UNREACHABLE_CODE") error("unreachable")
}

suspend inline fun <reified T : ServerEvent> DefaultClientWebSocketSession.expect(timeoutMs: Long = 5_000): T {
    val ev = next(presence = T::class == PresenceEvent::class, timeoutMs = timeoutMs)
    check(ev is T) { "Expected ${T::class.simpleName} but got $ev" }
    return ev
}

/** Xac nhan khong co event nao den trong [ms] — vi du xac nhan khong bi giao tin trung lap lan 2. */
suspend fun DefaultClientWebSocketSession.expectSilence(ms: Long = 500, presence: Boolean = false) {
    val ev = withTimeoutOrNull(ms) { next(presence, timeoutMs = ms + 1_000) }
    check(ev == null) { "Expected no event but got $ev" }
}
