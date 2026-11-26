package com.example.chatserver

import com.example.chat.protocol.*
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun env(name: String, default: String): String = System.getenv(name) ?: default

fun main() {
    val db = Database.fromEnv()
    db.migrate()
    val isProd = env("ENVIRONMENT", "development").equals("production", ignoreCase = true)
    val defaultSecret = "dev-only-secret-change-me"
    val jwtSecret = env("JWT_SECRET", defaultSecret)
    if (isProd && jwtSecret == defaultSecret) {
        error("LOI BAO MAT: Moi truong production khong duoc phep dung JWT_SECRET mac dinh!")
    }
    // Lang nghe tren 0.0.0.0 de emulator Android co the ket noi qua 10.0.2.2
    embeddedServer(Netty, port = env("PORT", "8080").toInt(), host = "0.0.0.0") {
        module(
            db = db,
            jwtSecret = jwtSecret,
            // Danh sach chat server cho service discovery, cach nhau boi dau phay
            chatServers = env("CHAT_SERVERS", env("PUBLIC_WS_URL", "ws://10.0.2.2:8080/ws")).split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
    }.start(wait = true)
}

fun Application.module(
    db: Database,
    jwtSecret: String,
    chatServers: List<String>,
    presenceTimeout: Duration = 30.seconds, // Hinh 12-18 trong sach: timeout presence = 30s
    presenceSweep: Duration = 5.seconds,
) {
    val jwt = JwtService(jwtSecret)
    val registry = ConnectionRegistry()
    val discovery = ServiceDiscovery(chatServers)
    val presence = PresenceService(db, registry, presenceTimeout)
    FcmService.init()
    launch {
        while (isActive) {
            delay(presenceSweep)
            runCatching { presence.sweep() }.onFailure { log.warn("Presence sweep failed", it) }
        }
    }

    install(ContentNegotiation) { json(Protocol.json) }
    install(WebSockets) {
        pingPeriod = 15.seconds  // ping/pong tang transport: phat hien ket noi TCP bi dut ngam
        timeout = 30.seconds
        maxFrameSize = 512L * 1024 // gioi han frame du cho 99.999 ky tu UTF-8 kem JSON
    }
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ErrorResponse(e.code, e.message)) }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Malformed or missing request body"))
        }
        exception<Throwable> { call, e ->
            call.application.log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL", "Internal server error"))
        }
    }
    install(Authentication) {
        jwt {
            verifier(jwt.verifier)
            validate { cred -> cred.payload.getClaim(JwtService.CLAIM_UID).asLong()?.let { JWTPrincipal(cred.payload) } }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "Missing or invalid token"))
            }
        }
    }

    routing {
        get("/health") {
            val dbUp = runCatching { db.tx { c -> c.createStatement().use { it.executeQuery("SELECT 1").next() } } }
                .getOrDefault(false)
            call.respond(
                if (dbUp) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                mapOf("status" to if (dbUp) "ok" else "degraded", "db" to if (dbUp) "up" else "down"),
            )
        }
        authRoutes(db, jwt, discovery)
        authenticate {
            userRoutes(db, presence)
            conversationRoutes(db, presence)
            syncRoutes(db, discovery)
            chatSocket(db, registry, presence)
        }
    }
}
