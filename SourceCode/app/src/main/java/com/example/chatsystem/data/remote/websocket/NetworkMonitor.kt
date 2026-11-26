package com.example.chatsystem.data.remote.websocket

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.example.chatsystem.di.AppContainer

/**
 * Gan ket WebSocket voi trang thai mang cua he dieu hanh Android:
 * mat mang -> ngat socket ngay lap tuc; co mang lai -> ket noi lai ngay ma khong can doi het thoi gian backoff.
 * Neu khong co lop nay, app phai cho timeout ping 20s moi biet mat mang va cho toi da 30s backoff khi co mang lai.
 */
class NetworkMonitor(context: Context, private val socket: ChatSocketClient) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun start() {
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(AppContainer.TAG, "Network available: reconnecting now")
                socket.reconnectNow()
            }

            override fun onLost(network: Network) {
                Log.i(AppContainer.TAG, "Network lost: dropping the socket")
                socket.onNetworkLost()
            }
        })
    }
}
