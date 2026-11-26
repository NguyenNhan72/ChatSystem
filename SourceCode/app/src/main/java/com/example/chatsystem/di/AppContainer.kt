package com.example.chatsystem.di

import android.content.Context
import android.util.Log
import com.example.chat.protocol.Protocol
import com.example.chatsystem.BuildConfig
import com.example.chatsystem.data.local.SessionStore
import com.example.chatsystem.data.local.database.AppDatabase
import com.example.chatsystem.data.remote.api.ChatApi
import com.example.chatsystem.data.repository.AuthRepository
import com.example.chatsystem.data.repository.ChatRepository
import com.example.chatsystem.data.remote.websocket.ChatSocketClient
import com.example.chatsystem.data.remote.websocket.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Dependency Injection thu cong: khoi tao moi doi tuong ton tai lau dai mot lan duy nhat theo dung thu tu phu thuoc.
 * (Thay vi dung Hilt phuc tap, viet lop AppContainer truc tiep giup de doc, de quan ly va de giai thich bao ve do an.)
 */
class AppContainer(context: Context) {
    val sessionStore = SessionStore(context)
    val database = AppDatabase.create(context)

    /** Ton tai song song voi tien trinh app: WebSocket va vong lap xu ly event khong bi rang buoc vao bat ky man hinh nao. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Dung chung cho ca Retrofit (REST) va WebSocket client — chung mot pool ket noi, chung interceptor tu them header JWT. */
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val token = sessionStore.session.value?.token
            val request = chain.request().newBuilder().apply { token?.let { header("Authorization", "Bearer $it") } }.build()
            val response = chain.proceed(request)
            // Token het han hoac bi tu choi: xoa session; UI lang nghe su kien va tu dong xu ly dang nhap lai.
            if (response.code == 401 && token != null && !request.url.encodedPath.startsWith("/api/auth/")) {
                Log.w(TAG, "401 on ${request.method} ${request.url.encodedPath}: clearing session")
                sessionStore.clear()
            }
            response
        }
        // Logcat tag "OkHttp": log phuong thuc, URL, ma HTTP status, thoi gian moi cuoc goi REST.
        // Muc log BASIC, khong dung BODY de tranh lo mat khau va token len Logcat.
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    val api: ChatApi = Retrofit.Builder()
        .baseUrl(BuildConfig.SERVER_URL)
        .client(okHttp)
        .addConverterFactory(Protocol.json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ChatApi::class.java)

    // Chat server khong ket noi duoc => hoi service discovery server khac (null = loi mang, giu server cu va thu lai)
    val socket = ChatSocketClient(okHttp, appScope) { failed -> runCatching { api.chatServer(failed).chatServerUrl }.getOrNull() }
    val authRepository = AuthRepository(api, sessionStore, database)
    val chatRepository = ChatRepository(api, database, socket, sessionStore, appScope)

    init {
        NetworkMonitor(context, socket).start()
        // Da dang nhap => duy tri ket noi WebSocket toi chatServerUrl; dang xuat / mat token => dong socket.
        appScope.launch {
            sessionStore.session.distinctUntilChangedBy { it?.token }.collect { s ->
                if (s != null) chatRepository.connect(s.chatServerUrl) else chatRepository.disconnect()
            }
        }
    }

    companion object {
        const val TAG = "MobileChat" // Tag dung de loc log rieng cua app tren Logcat
    }
}
