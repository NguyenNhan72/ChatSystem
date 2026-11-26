package com.example.chatserver

import com.example.chat.protocol.*
import io.ktor.http.HttpStatusCode
import org.postgresql.util.PSQLException
import java.sql.Connection
import java.sql.ResultSet

object UserRepository {
    private fun ResultSet.toUser() = UserDto(getLong("id"), getString("username"), getString("display_name"))

    fun create(c: Connection, username: String, displayName: String, passwordHash: String): UserDto = try {
        c.query(
            "INSERT INTO users (username, display_name, password_hash) VALUES (?, ?, ?) RETURNING id, username, display_name",
            username, displayName, passwordHash,
        ) { it.toUser() }.single()
    } catch (e: PSQLException) {
        if (e.sqlState == UNIQUE_VIOLATION) throw ApiException(HttpStatusCode.Conflict, "USERNAME_TAKEN", "Username already exists")
        throw e
    }

    /** Thong tin user kem password hash, chi dung khi dang nhap. */
    fun findForLogin(c: Connection, username: String): Pair<UserDto, String>? =
        c.query("SELECT id, username, display_name, password_hash FROM users WHERE username = ?", username) {
            it.toUser() to it.getString("password_hash")
        }.singleOrNull()

    fun byId(c: Connection, id: Long): UserDto? =
        c.query("SELECT id, username, display_name FROM users WHERE id = ?", id) { it.toUser() }.singleOrNull()

    fun countExisting(c: Connection, ids: List<Long>): Int =
        c.query("SELECT count(*) FROM users WHERE id = ANY(?)", ids) { it.getInt(1) }.single()

    /** Tim kiem user khac [excludeId] co username hoac display name chua chuoi [q] (khong phan biet hoa thuong, loai bo account khach). */
    fun search(c: Connection, q: String, excludeId: Long): List<UserDto> =
        c.query(
            """SELECT id, username, display_name FROM users
               WHERE id <> ? AND username NOT LIKE 'guest_%' AND (username ILIKE ? OR display_name ILIKE ?)
               ORDER BY username LIMIT 50""",
            excludeId, "%$q%", "%$q%",
        ) { it.toUser() }
}
