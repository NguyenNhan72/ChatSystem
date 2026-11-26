package com.example.chatsystem.presentation.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chatsystem.ChatApplication
import com.example.chatsystem.di.AppContainer

/** Ham viewModel() tao ViewModel tu doi tuong [AppContainer] cua ung dung (DI thu cong). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as ChatApplication).container
    return viewModel(initializer = { create(container) })
}
