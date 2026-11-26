package com.example.chatsystem

import com.example.chatsystem.data.local.entity.MemberEntity
import com.example.chatsystem.presentation.viewmodel.presenceLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresenceLabelTest {
    private val now = 10_000_000_000L
    private val me = 1L

    private fun member(id: Long, online: Boolean, lastActiveAt: Long? = null) =
        MemberEntity(7, id, "u$id", "U$id", 0, 0, online, lastActiveAt)

    @Test
    fun `direct chat shows the other person's status`() {
        val meOnline = member(me, true)
        assertEquals("Đang hoạt động", presenceLabel(listOf(meOnline, member(2, true)), me, isGroup = false, now))
        assertEquals("Hoạt động vừa xong", presenceLabel(listOf(meOnline, member(2, false, now - 20_000)), me, false, now))
        assertEquals("Hoạt động 5 phút trước", presenceLabel(listOf(meOnline, member(2, false, now - 5 * 60_000)), me, false, now))
        assertEquals("Hoạt động 3 giờ trước", presenceLabel(listOf(meOnline, member(2, false, now - 3 * 3_600_000)), me, false, now))
        assertEquals("Hoạt động 2 ngày trước", presenceLabel(listOf(meOnline, member(2, false, now - 2 * 86_400_000)), me, false, now))
        assertNull(presenceLabel(listOf(meOnline, member(2, false, null)), me, false, now)) // chua tung thay online: khong hien gi
    }

    @Test
    fun `group shows member count and how many others are online`() {
        val members = listOf(member(me, true), member(2, true), member(3, false), member(4, true))
        assertEquals("4 thành viên · 2 đang hoạt động", presenceLabel(members, me, isGroup = true, now)) // khong tinh chinh minh
        assertEquals("2 thành viên", presenceLabel(listOf(member(me, true), member(3, false)), me, true, now))
    }
}
