package com.example.chatsystem.data.remote

import com.example.chat.protocol.ErrorResponse
import com.example.chat.protocol.Protocol
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Tuong tu runCatching nhung khong bao gio nuot CancellationException cua coroutine. */
suspend inline fun <T> resultOf(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** Thong bao loi than thien voi nguoi dung cho bat ky loi nao tu tang mang. */
fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> {
        val body = runCatching {
            Protocol.json.decodeFromString(ErrorResponse.serializer(), response()?.errorBody()?.string().orEmpty())
        }.getOrNull()
        userMessageFor(body?.error, body?.message ?: "Lỗi server (HTTP ${code()})")
    }
    is IOException -> "Không kết nối được server. Kiểm tra mạng hoặc server đã chạy chưa."
    else -> message ?: "Lỗi không xác định"
}

/** Chuyen ma loi server thanh cau tieng Viet hien thi; ma la se dung fallback cua server. */
fun userMessageFor(code: String?, fallback: String): String = when (code) {
    "INVALID_CREDENTIALS" -> "Sai tên đăng nhập hoặc mật khẩu"
    "USERNAME_TAKEN" -> "Tên đăng nhập đã tồn tại"
    "INVALID_USERNAME" -> "Tên đăng nhập 3–32 ký tự, chỉ gồm a-z, 0-9, _"
    "INVALID_PASSWORD" -> "Mật khẩu 6–72 ký tự"
    "INVALID_DISPLAY_NAME" -> "Tên hiển thị 1–64 ký tự"
    "UNAUTHORIZED" -> "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại"
    "NOT_A_MEMBER" -> "Bạn không thuộc cuộc trò chuyện này"
    else -> fallback
}
