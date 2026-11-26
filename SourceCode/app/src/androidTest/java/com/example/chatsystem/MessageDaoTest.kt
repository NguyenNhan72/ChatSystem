package com.example.chatsystem

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chatsystem.data.local.database.AppDatabase
import com.example.chatsystem.data.local.entity.MessageEntity
import com.example.chatsystem.data.local.entity.MessageEntity.Companion.FAILED
import com.example.chatsystem.data.local.entity.MessageEntity.Companion.SENDING
import com.example.chatsystem.data.local.entity.MessageEntity.Companion.SENT
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Kiem tra Outbox + chong trung lap + thu tu tin nhan voi SQLite that tren thiet bi. */
@RunWith(AndroidJUnit4::class)
class MessageDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun m(id: String, seq: Long?, status: String = SENT, createdAt: Long = 0) = MessageEntity(id, 1, seq, 1, id, createdAt, status)

    @Test
    fun ordersBySeqWithUnsentMessagesLast() = runBlocking {
        val dao = db.messageDao()
        dao.upsert(listOf(m("p2", null, SENDING, createdAt = 20), m("s3", 3), m("p1", null, SENDING, createdAt = 10), m("s1", 1), m("s2", 2)))
        assertEquals(listOf("s1", "s2", "s3", "p1", "p2"), dao.observe(1).first().map { it.clientMsgId })
    }

    @Test
    fun sameMessageArrivingTwiceIsStoredOnce() = runBlocking {
        val dao = db.messageDao()
        dao.upsert(listOf(m("x", null, SENDING)))   // dong outbox cua toi
        dao.markSent("x", seq = 5, createdAt = 500) // nhan ack tu server
        dao.upsert(listOf(m("x", 5)))               // cung tin do nhan lai tu trang dong bo
        val rows = dao.observe(1).first()
        assertEquals(1, rows.size)
        assertEquals(5L, rows.single().seq)
        assertEquals(SENT, rows.single().status)
    }

    @Test
    fun outboxTransitions() = runBlocking {
        val dao = db.messageDao()
        dao.upsert(listOf(m("a", null, SENDING), m("b", null, SENDING)))
        dao.markSent("a", 1, 100)
        dao.markFailed("a") // ack da den: timeout den muon khong duoc ha cap trang thai tin
        dao.markFailed("b")
        assertEquals(SENT, dao.get("a")!!.status)
        assertEquals(FAILED, dao.get("b")!!.status)
        assertEquals(listOf("b"), dao.pending().map { it.clientMsgId })
        dao.markRetrying("b")
        assertEquals(SENDING, dao.get("b")!!.status)
    }

    @Test
    fun detectsAHoleBeforeANewMessage() = runBlocking {
        val dao = db.messageDao()
        dao.upsert(listOf(m("1", 1), m("2", 2), m("5", 5)))
        assertEquals(2L, dao.maxSeqBelow(1, 5)) // tin 3 va 4 bi thieu -> lay bu voi afterSeq=2
        assertEquals(5L, dao.maxSeq(1))
        assertEquals(1L, dao.minSeq(1))
    }
}
