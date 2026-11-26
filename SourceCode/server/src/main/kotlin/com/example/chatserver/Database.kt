package com.example.chatserver

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/** JDBC thuan dung pool HikariCP: moi cau lenh SQL chay deu the hien ro trong code. */
class Database(private val ds: HikariDataSource) {

    /** Tinh bat bien (idempotent): schema.sql chi dung lenh CREATE ... IF NOT EXISTS. */
    fun migrate() {
        val sql = javaClass.getResource("/schema.sql")!!.readText()
        ds.connection.use { c -> c.createStatement().use { it.execute(sql) } }
    }

    /** Chay [block] trong mot transaction tren IO dispatcher; tu dong rollback neu co loi. */
    suspend fun <T> tx(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        ds.connection.use { c ->
            c.autoCommit = false
            try {
                block(c).also { c.commit() }
            } catch (e: Throwable) {
                c.rollback()
                throw e
            }
        }
    }

    companion object {
        fun connect(url: String, user: String, password: String) = Database(
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                maximumPoolSize = 10
                connectionTimeout = 5_000 // bao loi ngay neu DB tat, tranh bi treo request 30s
            })
        )

        fun fromEnv() = connect(
            env("DB_URL", "jdbc:postgresql://localhost:5433/chat"),
            env("DB_USER", "chat"),
            env("DB_PASSWORD", "chat"),
        )
    }
}

private fun PreparedStatement.bind(params: Array<out Any?>) = params.forEachIndexed { i, p ->
    // Chuyen List<Long> -> bigint[] cua PostgreSQL de query co the dung "= ANY(?)"
    if (p is List<*>) setArray(i + 1, connection.createArrayOf("bigint", p.toTypedArray())) else setObject(i + 1, p)
}

fun <T> Connection.query(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { ps ->
        ps.bind(params)
        ps.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } }
    }

fun Connection.update(sql: String, vararg params: Any?): Int =
    prepareStatement(sql).use { ps -> ps.bind(params); ps.executeUpdate() }
