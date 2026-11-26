package com.example.chatsystem.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = MessengerBlue,
    onPrimary = Color.White,
    primaryContainer = MessengerBlueLight,
    onPrimaryContainer = MessengerBlueDark,
    secondary = MessengerTextSecondary,
    onSecondary = Color.White,
    background = MessengerBackground,
    onBackground = MessengerTextPrimary,
    surface = MessengerBackground,
    onSurface = MessengerTextPrimary,
    surfaceVariant = MessengerSurface,
    onSurfaceVariant = MessengerTextSecondary,
    outline = MessengerDivider,
    outlineVariant = MessengerSurfaceVariant,
    error = MessengerDestructive,
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEA),
    onErrorContainer = MessengerDestructive,
)

private val DarkColorScheme = darkColorScheme(
    primary = MessengerBlue,
    onPrimary = Color.White,
    background = Color(0xFF18191A),
    onBackground = Color(0xFFE4E6EB),
    surface = Color(0xFF242526),
    onSurface = Color(0xFFE4E6EB),
    surfaceVariant = Color(0xFF3A3B3C),
    onSurfaceVariant = Color(0xFFB0B3B8),
    outline = Color(0xFF393A3B),
    error = MessengerDestructive,
)

@Composable
fun ChatSystemTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // De dynamicColor = false de giu nguyen bang mau thiet ke Messenger
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
