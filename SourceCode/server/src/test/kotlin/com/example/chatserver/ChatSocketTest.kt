package com.example.chatserver

import com.example.chat.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.send
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Kiem thu luong chat WebSocket: socket that, database that. Tung test map voi mot luong trong Chapter 12. */
class ChatSocketTest {

    @BeforeTest
    fun clean() = TestDb.clean()

    private fun newId() = UUID.randomUUID().toString()

    private suspend fun HttpClient.direct(me: AuthResponse, other: AuthResponse): ConversationDto =
        postJson("/api/conversations/direct", CreateDirectRequest(other.user.id), me.token).body()

    private suspend fun HttpClient.history(conversationId: Long, token: String, query: String = ""): List<MessageDto> =
        getAuth("/api/conversations/$conversationId/messages$query", token).body()

    private fun lastSeq(conversationId: Long): Long = kotlinx.coroutines.runBlocking {
        TestDb.db.tx { c -> c.query("SELECT last_seq FROM conversations WHERE id = ?", conversationId) { it.getLong(1) }.single() }
    }

    /** Doc event cho den khi nhan du [n] ack, bo qua tin nhan push tu nguoi khac. */
    private suspend fun DefaultClientWebSocketSession.collectAcks(n: Int): List<AckEvent> = buildList {
        while (size < n) (next() as? AckEvent)?.let { add(it) }
    }

    @Test
    fun `1-1 message is persisted, acked to the sender and pushed to the online recipient`() = chatTest { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        val conv = client.direct(alice, bob)
        val a = client.connect(alice.token)
        val b = client.connect(bob.token)

        val id = newId()
        a.emit(SendEvent(id, conv.id, "hello bob"))

        val ack = a.expect<AckEvent>()
        assertEquals(id, ack.clientMsgId)
        assertEquals(1, ack.seq)
        val pushed = b.expect<MessageEvent>().message
        assertEquals(MessageDto(conv.id, 1, id, alice.user.id, "hello bob", ack.createdAt), pushed)
        a.expectSilence() // thiet bi gui chi nhan ack, khong nhan lai chinh tin nhan cua no

        assertEquals(listOf("hello bob"), client.history(conv.id, bob.token).map { it.content })
    }

    @Test
    fun `resending the same clientMsgId stores and delivers the message once`() = chatTest { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        val conv = client.direct(alice, bob)
        val a = client.connect(alice.token)
        val b = client.connect(bob.token)

        val id = newId()
        a.emit(SendEvent(id, conv.id, "only once"))
        a.emit(SendEvent(id, conv.id, "only once")) // thu lai sau khi mat ack
        val acks = a.collectAcks(2)
        assertEquals(listOf(1L, 1L), acks.map { it.seq })

        b.expect<MessageEvent>()
        b.expectSilence()
        assertEquals(1, client.history(conv.id, bob.token).size)

        // Hai thiet bi gui cung mot id (kich hoat nhanh loi UNIQUE): van chi luu mot dong, khong co khoang trong seq.
        val a2 = client.connect(alice.token)
        val raceId = newId()
        coroutineScope {
            listOf(a, a2).map { s -> async { s.emit(SendEvent(raceId, conv.id, "race")) } }.awaitAll()
        }
        assertEquals(setOf(2L), (a.collectAcks(1) + a2.collectAcks(1)).map { it.seq }.toSet())
        assertEquals(2, client.history(conv.id, bob.token).size)
        assertEquals(2, lastSeq(conv.id))
    }

    @Test
    fun `offline recipient catches up with afterSeq sync and paginates with beforeSeq`() = chatTest { client ->
        val alice = client.register("alice")
        val bob = client.register("bob") // bob khong bao gio mo socket: offline
        val carol = client.register("carol")
        val conv = client.direct(alice, bob)
        val a = client.connect(alice.token)

        listOf("one", "two", "three").forEach { a.emit(SendEvent(newId(), conv.id, it)) }
        assertEquals(listOf(1L, 2L, 3L), a.collectAcks(3).map { it.seq })

        // Hinh 12-13: thiet bi yeu cau tat ca tin moi hon cur_max cua no (o day: max seq cuc bo).
        assertEquals(listOf(1L, 2L, 3L), client.history(conv.id, bob.token, "?afterSeq=0").map { it.seq })
        assertEquals(listOf("three"), client.history(conv.id, bob.token, "?afterSeq=2").map { it.content })
        // Cuon len tren: phan trang cac tin nhan cu hon, tra ve thu tu cu nhat truoc.
        assertEquals(listOf(2L), client.history(conv.id, bob.token, "?beforeSeq=3&limit=1").map { it.seq })
        assertEquals(listOf(2L, 3L), client.history(conv.id, bob.token, "?limit=2").map { it.seq })

        assertEquals(HttpStatusCode.Forbidden, client.getAuth("/api/conversations/${conv.id}/messages", carol.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.getAuth("/api/conversations/${conv.id}/messages?afterSeq=1&beforeSeq=2", bob.token).status)
    }

    @Test
    fun `concurrent senders in one conversation get unique, gap-free sequence numbers`() = chatTest { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        val conv = client.direct(alice, bob)
        val a = client.connect(alice.token)
        val b = client.connect(bob.token)

        coroutineScope {
            listOf(a, b).map { s -> async { repeat(10) { s.emit(SendEvent(newId(), conv.id, "msg $it")) } } }.awaitAll()
        }
        val seqs = (a.collectAcks(10) + b.collectAcks(10)).map { it.seq }
        assertEquals((1L..20L).toList(), seqs.sorted())
        assertEquals((1L..20L).toList(), client.history(conv.id, alice.token, "?afterSeq=0").map { it.seq })
    }

    @Test
    fun `invalid sends are rejected and the connection stays usable`() = chatTest { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        val carol = client.register("carol")
        val conv = client.direct(alice, bob)
        val c = client.connect(carol.token)

        val id = newId()
        c.emit(SendEvent(id, conv.id, "let me in"))
        val notMember = c.expect<ErrorEvent>()
        assertEquals("NOT_A_MEMBER", notMember.code)
        assertEquals(id, notMember.clientMsgId)

        assertEquals("INVALID_CONTENT", c.run { emit(SendEvent(newId(), conv.id, "   ")); expect<ErrorEvent>().code })
        assertEquals("INVALID_CONTENT", c.run { emit(SendEvent(newId(), conv.id, "x".repeat(100_000))); expect<ErrorEvent>().code })
        assertEquals("INVALID_CLIENT_MSG_ID", c.run { emit(SendEvent("not-a-uuid", conv.id, "hi")); expect<ErrorEvent>().code })
        c.send("this is not json")
        assertEquals("BAD_EVENT", c.expect<ErrorEvent>().code)

        // van ket noi va hoat dong binh thuong sau cac loi
        val own = client.postJson("/api/conversations/direct", CreateDirectRequest(alice.user.id), carol.token).body<ConversationDto>()
        c.emit(SendEvent(newId(), own.id, "hi alice"))
        assertEquals(1, c.expect<AckEvent>().seq)
        assertEquals(0, client.history(conv.id, alice.token).size)
    }

    @Test
    fun `delivered and read receipts reach the sender and move forward only`() = chatTest { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        val conv = client.direct(alice, bob)
        val a = client.connect(alice.token)
        val b = client.connect(bob.token)

        repeat(2) { a.emit(SendEvent(newId(), conv.id, "m$it")) }
        a.collectAcks(2)
        repeat(2) { b.expect<MessageEvent>() }

        b.emit(DeliveredEvent(conv.id, 2))
        assertEquals(ReceiptEvent(conv.id, bob.user.id, deliveredSeq = 2, readSeq = 0), a.expect<ReceiptEvent>())

        b.emit(ReadEvent(conv.id, 999)) // bi gioi han o tin nhan thuc te cuoi cung
        assertEquals(ReceiptEvent(conv.id, bob.user.id, deliveredSeq = 2, readSeq = 2), a.expect<ReceiptEvent>())

        b.emit(DeliveredEvent(conv.id, 1)) // ack cu khong duoc phep lui con tro ve truoc
        assertEquals(ReceiptEvent(conv.id, bob.user.id, deliveredSeq = 2, readSeq = 2), a.expect<ReceiptEvent>())

        assertEquals(0, client.getAuth("/api/conversations/${conv.id}", bob.token).body<ConversationDto>().unreadCount)
        assertEquals(2, client.getAuth("/api/conversations/${conv.id}", alice.token).body<ConversationDto>()
            .members.single { it.user.id == bob.user.id }.lastReadSeq)
    }

    @Test
    fun `a message reaches the sender's other device too`() = chatTest { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        val conv = client.direct(alice, bob)
        val phone = client.connect(alice.token)
        val laptop = client.connect(alice.token)
        val b = client.connect(bob.token)

        phone.emit(SendEvent(newId(), conv.id, "from my phone"))
        phone.expect<AckEvent>()
        assertEquals("from my phone", laptop.expect<MessageEvent>().message.content) // Hinh 12-13: dong bo thiet bi khac cua nguoi gui
        assertEquals("from my phone", b.expect<MessageEvent>().message.content)
    }

    @Test
    fun `group message fans out to every online member, receipts stay private`() = chatTest { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        val carol = client.register("carol")
        val group = client.postJson("/api/conversations/group", CreateGroupRequest("Team", listOf(bob.user.id, carol.user.id)), alice.token)
            .body<ConversationDto>()
        val a = client.connect(alice.token)
        val b = client.connect(bob.token)
        val c = client.connect(carol.token)

        a.emit(SendEvent(newId(), group.id, "hi team"))
        assertEquals(1, a.expect<AckEvent>().seq)
        assertEquals("hi team", b.expect<MessageEvent>().message.content)
        assertEquals("hi team", c.expect<MessageEvent>().message.content)

        b.emit(DeliveredEvent(group.id, 1))
        a.expectSilence() // khong ban receipt tung thanh vien trong nhom
        assertEquals(1, client.getAuth("/api/conversations/${group.id}", alice.token).body<ConversationDto>()
            .members.single { it.user.id == bob.user.id }.lastDeliveredSeq)
    }

    private suspend fun HttpClient.inbox(token: String, after: Long = 0): List<InboxItemDto> =
        getAuth("/api/sync?afterInboxId=$after", token).body()

    @Test
    fun `every message is copied to each member's inbox, devices sync past cur_max (Figure 12-13, 12-14, 12-15)`() = chatTest { client ->
        val alice = client.register("alice")
        val bob = client.register("bob") // bob offline: chi lay tin qua inbox
        val carol = client.register("carol")
        val group = client.postJson("/api/conversations/group", CreateGroupRequest("Team", listOf(bob.user.id, carol.user.id)), alice.token)
            .body<ConversationDto>()
        val direct = client.direct(carol, bob)
        val a = client.connect(alice.token)
        val c = client.connect(carol.token)

        a.emit(SendEvent(newId(), group.id, "hi team"))
        a.expect<AckEvent>()
        c.expect<MessageEvent>()
        c.emit(SendEvent(newId(), direct.id, "hi bob"))
        c.expect<AckEvent>()

        // Hinh 12-15: inbox cua bob chua tin tu nhieu nguoi gui, theo thu tu inbox_id 1, 2, ...
        val bobInbox = client.inbox(bob.token)
        assertEquals(listOf(1L, 2L), bobInbox.map { it.inboxId })
        assertEquals(listOf("hi team", "hi bob"), bobInbox.map { it.message.content })
        assertEquals(listOf(group.id to 1L, direct.id to 1L), bobInbox.map { it.message.conversationId to it.message.seq })
        // Hinh 12-13: thiet bi co cur_max = 1 chi nhan phan con thieu
        assertEquals(listOf("hi bob"), client.inbox(bob.token, after = 1).map { it.message.content })
        // Hinh 12-14: ban sao rieng cho tung thanh vien (ke ca nguoi gui, de dong bo thiet bi khac cua ho)
        assertEquals(listOf("hi team"), client.inbox(alice.token).map { it.message.content })
        assertEquals(listOf("hi team", "hi bob"), client.inbox(carol.token).map { it.message.content })
        assertEquals(HttpStatusCode.BadRequest, client.getAuth("/api/sync?afterInboxId=x", bob.token).status)
    }

    @Test
    fun `concurrent group sends keep every inbox gap-free`() = chatTest { client ->
        val users = (1..4).map { client.register("user$it") }
        val ids = users.drop(1).map { it.user.id }
        val g1 = client.postJson("/api/conversations/group", CreateGroupRequest("G1", ids), users[0].token).body<ConversationDto>()
        val g2 = client.postJson("/api/conversations/group", CreateGroupRequest("G2", ids), users[0].token).body<ConversationDto>()
        val sockets = users.map { client.connect(it.token) }
        coroutineScope {
            sockets.flatMap { s -> (1..5).map { i -> async { s.emit(SendEvent(newId(), if (i % 2 == 0) g1.id else g2.id, "m$i")) } } }.awaitAll()
        }
        sockets.forEach { it.collectAcks(5) }
        for (u in users) assertEquals((1L..20L).toList(), client.inbox(u.token).map { it.inboxId })
    }

    @Test
    fun `a 99,999-character message is the longest accepted, end to end`() = chatTest { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        val conv = client.direct(alice, bob)
        val a = client.connect(alice.token)
        val b = client.connect(bob.token)

        // Chu co dau: 3 byte UTF-8 moi ky tu => ~300 KB, kiem tra luon gioi han frame WebSocket 512 KB va CHECK cua DB
        val longest = "ấ".repeat(MAX_CONTENT_LENGTH)
        a.emit(SendEvent(newId(), conv.id, longest))
        assertEquals(1, a.expect<AckEvent>().seq)
        assertEquals(longest, b.expect<MessageEvent>().message.content)
        assertEquals(longest, client.history(conv.id, bob.token).single().content)

        a.emit(SendEvent(newId(), conv.id, longest + "x")) // 100.000 ky tu
        assertEquals("INVALID_CONTENT", a.expect<ErrorEvent>().code)
    }

    @Test
    fun `socket without a valid token is refused`() = chatTest { client ->
        assertFailsWith<Exception> { client.webSocketSession("/ws") }
        assertFailsWith<Exception> { client.connect("not-a-jwt") }
    }
}
