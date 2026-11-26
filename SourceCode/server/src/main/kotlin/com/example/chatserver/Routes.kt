package com.example.chatserver

import com.example.chat.protocol.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

const val MAX_GROUP_MEMBERS = 100 // theo sach: nhom chat nho, toi da 100 nguoi

private val USERNAME = Regex("^[a-z0-9_]{3,32}$")

/** Route cong khai: dang ky va dang nhap. Ca hai tra ve JWT kem dia chi chat server de ket noi (Hinh 12-11 buoc 3). */
fun Route.authRoutes(db: Database, jwt: JwtService, discovery: ServiceDiscovery) = route("/api/auth") {
    post("/register") {
        val req = call.receive<RegisterRequest>()
        val username = req.username.trim().lowercase()
        val displayName = req.displayName.trim()
        if (!USERNAME.matches(username)) throw badRequest("INVALID_USERNAME", "Username: 3-32 chars, a-z 0-9 _")
        if (displayName.length !in 1..64) throw badRequest("INVALID_DISPLAY_NAME", "Display name: 1-64 chars")
        if (req.password.length !in 6..72) throw badRequest("INVALID_PASSWORD", "Password: 6-72 chars") // 72 = gioi han dau vao bcrypt
        val hash = Passwords.hash(req.password)
        val user = db.tx { UserRepository.create(it, username, displayName, hash) }
        call.respond(HttpStatusCode.Created, AuthResponse(jwt.issue(user.id), user, discovery.pick(user.id)))
    }
    post("/login") {
        val req = call.receive<LoginRequest>()
        val found = db.tx { UserRepository.findForLogin(it, req.username.trim().lowercase()) }
        // So sanh password voi thoi gian dong nhat de phong thu tan cong timing attack (user enumeration)
        val targetHash = found?.second ?: Passwords.DUMMY_HASH
        val passwordMatches = Passwords.matches(req.password, targetHash)
        if (found == null || !passwordMatches) {
            throw ApiException(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS", "Wrong username or password")
        }
        call.respond(AuthResponse(jwt.issue(found.first.id), found.first, discovery.pick(found.first.id)))
    }
}

/** Cac route can JWT hop le (duoc bao ve boi authenticate { }). */
fun Route.userRoutes(db: Database, presence: PresenceService) = route("/api/users") {
    get("/me") {
        val me = call.userId()
        val user = db.tx { UserRepository.byId(it, me) } ?: throw ApiException(HttpStatusCode.NotFound, "USER_NOT_FOUND", "User not found")
        call.respond(user.copy(online = presence.isOnline(user.id)))
    }
    get {
        val q = call.request.queryParameters["q"].orEmpty().trim()
        val users = db.tx { UserRepository.search(it, q, call.userId()) }
        call.respond(users.map { it.copy(online = presence.isOnline(it.id)) })
    }
    post("/me/device") {
        val req = call.receive<RegisterDeviceRequest>()
        val me = call.userId()
        if (req.fcmToken.isNotBlank()) {
            db.tx { DeviceTokenRepository.upsert(it, me, req.fcmToken.trim()) }
        }
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }
    delete("/me/device") {
        val me = call.userId()
        db.tx { DeviceTokenRepository.delete(it, me) }
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }
}

fun Route.conversationRoutes(db: Database, presence: PresenceService) = route("/api/conversations") {
    get {
        call.respond(db.tx { ConversationRepository.listForUser(it, call.userId(), presence::isOnline) })
    }
    get("/{id}") {
        val id = call.conversationId()
        val me = call.userId()
        call.respond(db.tx { c ->
            requireMember(c, id, me)
            ConversationRepository.listForUser(c, me, presence::isOnline, id).single()
        })
    }
    get("/{id}/messages") {
        val id = call.conversationId()
        val me = call.userId()
        val q = call.request.queryParameters
        val afterSeq = q["afterSeq"]?.let { it.toLongOrNull() ?: throw badRequest("INVALID_SEQ", "afterSeq must be a number") }
        val beforeSeq = q["beforeSeq"]?.let { it.toLongOrNull() ?: throw badRequest("INVALID_SEQ", "beforeSeq must be a number") }
        if (afterSeq != null && beforeSeq != null) throw badRequest("INVALID_SEQ", "Use afterSeq or beforeSeq, not both")
        val limit = (q["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
        call.respond(db.tx { c ->
            requireMember(c, id, me)
            MessageRepository.page(c, id, afterSeq, beforeSeq, limit)
        })
    }
    post("/direct") {
        val other = call.receive<CreateDirectRequest>().userId
        val me = call.userId()
        if (other == me) throw badRequest("INVALID_MEMBER", "Cannot start a direct chat with yourself")
        val dto = db.tx { c ->
            UserRepository.byId(c, other) ?: throw ApiException(HttpStatusCode.NotFound, "USER_NOT_FOUND", "User not found")
            val id = ConversationRepository.getOrCreateDirect(c, me, other)
            ConversationRepository.listForUser(c, me, presence::isOnline, id).single()
        }
        call.respond(dto)
    }
    post("/group") {
        val req = call.receive<CreateGroupRequest>()
        val me = call.userId()
        val name = req.name.trim()
        if (name.length !in 1..64) throw badRequest("INVALID_NAME", "Group name: 1-64 chars")
        val members = (req.memberIds + me).distinct()
        if (members.size < 2) throw badRequest("INVALID_MEMBER", "A group needs at least one other member")
        if (members.size > MAX_GROUP_MEMBERS) throw badRequest("GROUP_TOO_LARGE", "Max $MAX_GROUP_MEMBERS members")
        val dto = db.tx { c ->
            if (UserRepository.countExisting(c, members) != members.size) throw ApiException(HttpStatusCode.NotFound, "USER_NOT_FOUND", "Some users do not exist")
            val id = ConversationRepository.createGroup(c, name, members)
            ConversationRepository.listForUser(c, me, presence::isOnline, id).single()
        }
        call.respond(HttpStatusCode.Created, dto)
    }
    post("/{id}/members") {
        val id = call.conversationId()
        val me = call.userId()
        val newIds = call.receive<AddMembersRequest>().userIds.distinct()
        if (newIds.isEmpty()) throw badRequest("INVALID_MEMBER", "userIds is empty")
        val dto = db.tx { c ->
            requireMember(c, id, me)
            if (ConversationRepository.type(c, id) != "GROUP") throw badRequest("NOT_A_GROUP", "Members can only be added to a group")
            if (UserRepository.countExisting(c, newIds) != newIds.size) throw ApiException(HttpStatusCode.NotFound, "USER_NOT_FOUND", "Some users do not exist")
            if ((ConversationRepository.memberIds(c, id) + newIds).distinct().size > MAX_GROUP_MEMBERS) {
                throw badRequest("GROUP_TOO_LARGE", "Max $MAX_GROUP_MEMBERS members")
            }
            ConversationRepository.addMembers(c, id, newIds)
            ConversationRepository.listForUser(c, me, presence::isOnline, id).single()
        }
        call.respond(dto)
    }
}

/** Service discovery (Hinh 12-11) va dong bo inbox (Hinh 12-13), deu can JWT. */
fun Route.syncRoutes(db: Database, discovery: ServiceDiscovery) {
    get("/api/discovery/chat-server") {
        call.respond(ChatServerDto(discovery.pick(call.userId(), call.request.queryParameters["exclude"])))
    }
    get("/api/sync") {
        val q = call.request.queryParameters
        val after = q["afterInboxId"]?.let { it.toLongOrNull() ?: throw badRequest("INVALID_INBOX_ID", "afterInboxId must be a number") } ?: 0
        val limit = (q["limit"]?.toIntOrNull() ?: 200).coerceIn(1, 500)
        call.respond(db.tx { MessageRepository.inboxAfter(it, call.userId(), after, limit) })
    }
}

/** Access control for every conversation-scoped request: only members may read or write. */
fun requireMember(c: java.sql.Connection, conversationId: Long, userId: Long) {
    if (!ConversationRepository.isMember(c, conversationId, userId)) {
        throw ApiException(HttpStatusCode.Forbidden, "NOT_A_MEMBER", "You are not a member of this conversation")
    }
}

private fun RoutingCall.conversationId(): Long =
    parameters["id"]?.toLongOrNull() ?: throw badRequest("INVALID_ID", "Conversation id must be a number")
