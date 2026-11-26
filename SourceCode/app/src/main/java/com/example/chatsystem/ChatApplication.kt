package com.example.chatsystem

import android.app.Application
import com.example.chatsystem.di.AppContainer

class ChatApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
