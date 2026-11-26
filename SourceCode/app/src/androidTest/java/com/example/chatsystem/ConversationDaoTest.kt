package com.example.chatsystem

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chatsystem.data.local.database.AppDatabase
import com.example.chatsystem.data.local.entity.ConversationEntity
import com.example.chatsystem.data.local.entity.MemberEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Chay tren thiet bi/emulator voi SQLite (in-memory) that thong qua Room. */
@RunWith(AndroidJUnit4::class)
class ConversationDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun conv(id: Long, activity: Long) = ConversationEntity(id, "DIRECT", "c$id", 0, 0, null, null, activity)
    private fun member(conv: Long, user: Long) = MemberEntity(conv, user, "u$user", "U$user", 0, 0, false, null)

    @Test
    fun replaceAllMirrorsTheServerListAndSortsByActivity() = runBlocking {
        val dao = db.conversationDao()
        dao.replaceAll(listOf(conv(1, 100), conv(2, 300), conv(3, 200)), listOf(member(1, 10), member(2, 10), member(3, 10)))
        assertEquals(listOf(2L, 3L, 1L), dao.observeAll().first().map { it.id })

        // Server khong con tra ve hoi thoai 3: hoi thoai va thanh vien cua no bi xoa o local.
        dao.replaceAll(listOf(conv(1, 100), conv(2, 300)), listOf(member(1, 10), member(2, 10)))
        assertEquals(listOf(2L, 1L), dao.observeAll().first().map { it.id })
        assertEquals(emptyList<MemberEntity>(), dao.observeMembers(3).first())
    }

    @Test
    fun upsertOneReplacesTheMemberList() = runBlocking {
        val dao = db.conversationDao()
        dao.upsertOne(listOf(conv(1, 100)), listOf(member(1, 10), member(1, 11)))
        dao.upsertOne(listOf(conv(1, 500)), listOf(member(1, 10), member(1, 12)))
        assertEquals(listOf(10L, 12L), dao.observeMembers(1).first().map { it.userId }.sorted())
        assertEquals(500L, dao.observe(1).first()!!.lastActivityAt)
    }

    @Test
    fun presenceUpdatesTheUserInEveryConversation() = runBlocking {
        val dao = db.conversationDao()
        dao.replaceAll(listOf(conv(1, 100), conv(2, 200), conv(3, 300)), listOf(member(1, 10), member(1, 11), member(2, 11), member(3, 12)))
        dao.setPresence(userId = 11, online = true, lastActiveAt = null)
        assertEquals(listOf(1L, 2L), dao.observeConversationsWithSomeoneOnline(myUserId = 10).first().sorted())
        assertEquals(emptyList<Long>(), dao.observeConversationsWithSomeoneOnline(myUserId = 11).first()) // trang thai cua chinh minh khong tao cham xanh

        dao.setPresence(userId = 11, online = false, lastActiveAt = 5_000)
        dao.setPresence(userId = 11, online = true, lastActiveAt = null) // online tro lai van giu thoi diem last-seen truoc do
        assertEquals(5_000L, dao.observeMembers(1).first().single { it.userId == 11L }.lastActiveAt)
    }
}
