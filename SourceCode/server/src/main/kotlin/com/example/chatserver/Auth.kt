package com.example.chatserver

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import org.mindrot.jbcrypt.BCrypt
import java.util.Date

/** Xac thuc stateless: token mang luon user id, moi instance deu xac thuc duoc ma khong can session store. */
class JwtService(secret: String) {
    init {
        require(secret.isNotBlank() && secret.length >= 16) {
            "JWT secret phai co do dai toi thieu 16 ky tu de dam bao an toan chu ky HMAC256"
        }
    }
    private val algorithm = Algorithm.HMAC256(secret)
    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(ISSUER).build()

    fun issue(userId: Long): String = JWT.create()
        .withIssuer(ISSUER)
        .withClaim(CLAIM_UID, userId)
        .withExpiresAt(Date(System.currentTimeMillis() + TTL_MS))
        .sign(algorithm)

    companion object {
        const val ISSUER = "mobile-chat"
        const val CLAIM_UID = "uid"
        private const val TTL_MS = 7L * 24 * 3600 * 1000 // don gian hoa: khong dung refresh token; dang nhap lai sau 7 ngay
    }
}

object Passwords {
    // Hash mau dung de so sanh thoi gian (constant-time) tranh tan cong timing attack khi user khong ton tai
    val DUMMY_HASH: String = hash("dummy-password-timing-defense")

    fun hash(plain: String): String = BCrypt.hashpw(plain, BCrypt.gensalt())
    fun matches(plain: String, hash: String): Boolean = BCrypt.checkpw(plain, hash)
}

/** User id cua nguoi dung da duoc xac thuc; chi hop le ben trong route authenticate { }. */
fun ApplicationCall.userId(): Long = principal<JWTPrincipal>()!!.payload.getClaim(JwtService.CLAIM_UID).asLong()
