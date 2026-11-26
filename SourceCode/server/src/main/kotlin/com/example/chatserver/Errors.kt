package com.example.chatserver

import io.ktor.http.HttpStatusCode

/** Duoc nem ra o bat ky dau trong qua trinh xu ly; StatusPages (REST) hoac socket handler (WS) se chuyen thanh error response tuong ung. */
class ApiException(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message)

fun badRequest(code: String, message: String) = ApiException(HttpStatusCode.BadRequest, code, message)

/** Ma SQLSTATE cua PostgreSQL khi vi pham rang buoc UNIQUE. */
const val UNIQUE_VIOLATION = "23505"
