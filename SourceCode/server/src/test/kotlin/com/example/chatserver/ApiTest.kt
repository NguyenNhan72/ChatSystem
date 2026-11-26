package com.example.chatserver

import com.example.chat.protocol.*
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test tich hop REST API (auth, users, conversations). Khoi tao nam trong TestSupport.kt. */
class ApiTest {
    private val db get() = TestDb.db

    @BeforeTest
    fun clean() = TestDb.clean()

    private fun api(block: suspend (io.ktor.client.HttpClient) -> Unit) = chatTest(block = block)

    @Test
    fun `register, login and me`() = api { client ->
        val alice = client.register("alice")
        assertEquals("ws://test/ws", alice.chatServerUrl) // Hinh 12-11 buoc 3
        // Wrap up: server loi => hoi lai service discovery (chi co 1 server nen van tra ve server do)
        assertEquals("ws://test/ws", client.getAuth("/api/discovery/chat-server?exclude=ws://test/ws", alice.token).body<ChatServerDto>().chatServerUrl)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/discovery/chat-server").status)

        val wrongPassword = client.postJson("/api/auth/login", LoginRequest("alice", "nope-nope"))
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals("INVALID_CREDENTIALS", wrongPassword.errorCode())

        val unknownUser = client.postJson("/api/auth/login", LoginRequest("nobody", "secret123"))
        assertEquals("INVALID_CREDENTIALS", unknownUser.errorCode())

        val login = client.postJson("/api/auth/login", LoginRequest(" ALICE ", "secret123"))
        assertEquals(HttpStatusCode.OK, login.status)
        val token = login.body<AuthResponse>().token

        assertEquals(alice.user, client.getAuth("/api/users/me", token).body<UserDto>())
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/users/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.getAuth("/api/users/me", "not-a-jwt").status)
    }

    @Test
    fun `register rejects duplicates and invalid input`() = api { client ->
        client.register("alice")
        val dup = client.postJson("/api/auth/register", RegisterRequest("Alice", "Other", "secret123"))
        assertEquals(HttpStatusCode.Conflict, dup.status)
        assertEquals("USERNAME_TAKEN", dup.errorCode())

        assertEquals("INVALID_USERNAME", client.postJson("/api/auth/register", RegisterRequest("a!", "A", "secret123")).errorCode())
        assertEquals("INVALID_PASSWORD", client.postJson("/api/auth/register", RegisterRequest("bob", "Bob", "123")).errorCode())
        assertEquals("INVALID_DISPLAY_NAME", client.postJson("/api/auth/register", RegisterRequest("bob", "  ", "secret123")).errorCode())
        assertEquals("BAD_REQUEST", client.postJson("/api/auth/register", mapOf("username" to "bob")).errorCode())
    }

    @Test
    fun `user search excludes the caller`() = api { client ->
        val alice = client.register("alice")
        client.register("bob")
        client.register("bobby")
        val found = client.getAuth("/api/users?q=bob", alice.token).body<List<UserDto>>()
        assertEquals(listOf("bob", "bobby"), found.map { it.username })
        assertTrue(client.getAuth("/api/users", alice.token).body<List<UserDto>>().none { it.username == "alice" })
    }

    @Test
    fun `direct conversation is unique per pair, even under concurrent creates`() = api { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")

        val ids = coroutineScope {
            (1..10).map { i ->
                async {
                    val (me, other) = if (i % 2 == 0) alice to bob else bob to alice
                    client.postJson("/api/conversations/direct", CreateDirectRequest(other.user.id), me.token).body<ConversationDto>().id
                }
            }.awaitAll()
        }
        assertEquals(1, ids.distinct().size)

        val forAlice = client.getAuth("/api/conversations", alice.token).body<List<ConversationDto>>().single()
        assertEquals("DIRECT", forAlice.type)
        assertEquals("Bob", forAlice.title)
        assertEquals(2, forAlice.members.size)
        assertNull(forAlice.lastMessage)

        assertEquals("INVALID_MEMBER", client.postJson("/api/conversations/direct", CreateDirectRequest(alice.user.id), alice.token).errorCode())
        assertEquals(HttpStatusCode.NotFound, client.postJson("/api/conversations/direct", CreateDirectRequest(999), alice.token).status)
    }

    @Test
    fun `group creation, membership and access control`() = api { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        val carol = client.register("carol")

        val created = client.postJson("/api/conversations/group", CreateGroupRequest(" Team ", listOf(bob.user.id)), alice.token)
        assertEquals(HttpStatusCode.Created, created.status)
        val group = created.body<ConversationDto>()
        assertEquals("Team", group.title)
        assertEquals(setOf(alice.user.id, bob.user.id), group.members.map { it.user.id }.toSet())

        // carol khong phai thanh vien: khong the doc tin hoac them nguoi
        assertEquals(HttpStatusCode.Forbidden, client.getAuth("/api/conversations/${group.id}", carol.token).status)
        assertEquals("NOT_A_MEMBER",
            client.postJson("/api/conversations/${group.id}/members", AddMembersRequest(listOf(carol.user.id)), carol.token).errorCode())

        val updated = client.postJson("/api/conversations/${group.id}/members", AddMembersRequest(listOf(carol.user.id, bob.user.id)), bob.token)
            .body<ConversationDto>()
        assertEquals(3, updated.members.size)
        assertEquals(group.id, client.getAuth("/api/conversations", carol.token).body<List<ConversationDto>>().single().id)

        val direct = client.postJson("/api/conversations/direct", CreateDirectRequest(bob.user.id), alice.token).body<ConversationDto>()
        assertEquals("NOT_A_GROUP",
            client.postJson("/api/conversations/${direct.id}/members", AddMembersRequest(listOf(carol.user.id)), alice.token).errorCode())

        assertEquals("GROUP_TOO_LARGE",
            client.postJson("/api/conversations/group", CreateGroupRequest("Big", (1L..101L).toList()), alice.token).errorCode())
        assertEquals(HttpStatusCode.NotFound,
            client.postJson("/api/conversations/group", CreateGroupRequest("Ghost", listOf(999)), alice.token).status)
        assertEquals("INVALID_MEMBER",
            client.postJson("/api/conversations/group", CreateGroupRequest("Solo", emptyList()), alice.token).errorCode())
    }

    @Test
    fun `conversation list shows last message and unread count`() = api { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        val conv = client.postJson("/api/conversations/direct", CreateDirectRequest(bob.user.id), alice.token).body<ConversationDto>()

        // Tin nhan se duoc ghi qua WebSocket o phase 3; insert truc tiep o day de kiem tra truy van doc.
        db.tx { c ->
            listOf("hi bob", "are you there?").forEachIndexed { i, text ->
                c.update("INSERT INTO messages (conversation_id, seq, client_msg_id, sender_id, content) VALUES (?, ?, ?, ?, ?)",
                    conv.id, i + 1L, UUID.randomUUID(), alice.user.id, text)
            }
            c.update("UPDATE conversations SET last_seq = 2 WHERE id = ?", conv.id)
        }

        val forBob = client.getAuth("/api/conversations/${conv.id}", bob.token).body<ConversationDto>()
        assertEquals(2, forBob.lastSeq)
        assertEquals("are you there?", forBob.lastMessage?.content)
        assertEquals(2, forBob.unreadCount)
        assertEquals("Alice", forBob.title)

        // tin nhan do chinh nguoi gui viet khong bao gio bi tinh la chua doc
        assertEquals(0, client.getAuth("/api/conversations/${conv.id}", alice.token).body<ConversationDto>().unreadCount)
    }
}
