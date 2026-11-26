package com.example.chatserver

/**
 * Service discovery (sach Hinh 12-11, vai tro cua ZooKeeper): giu danh sach chat server va chon mot server cho client.
 * - Dang nhap: tra ve server cho user (buoc 3), client mo WebSocket toi server do (buoc 4).
 * - Wrap up: chat server loi => client hoi lai, kem server vua loi trong [exclude], va nhan mot server khac.
 *
 * shortcut: danh sach tinh lay tu cau hinh (CHAT_SERVERS) va chon theo userId thay vi theo vi tri/tai cua server nhu ZooKeeper;
 * nang cap khi chay nhieu chat server that (can them dang ky/heartbeat cua server va chuyen tin giua cac server).
 */
class ServiceDiscovery(private val chatServers: List<String>) {
    init {
        require(chatServers.isNotEmpty()) { "Can it nhat mot chat server" }
    }

    fun pick(userId: Long, exclude: String? = null): String {
        val candidates = chatServers.filter { it != exclude }.ifEmpty { chatServers } // chi con server dang loi: van tra ve de client thu lai
        return candidates[Math.floorMod(userId, candidates.size)] // co dinh theo user: khong doi server khi server con song
    }
}
