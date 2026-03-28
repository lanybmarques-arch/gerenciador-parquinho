package com.example.gerenciadordeparquinho.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Offset
import androidx.core.view.WindowCompat

enum class AppThemeMode {
    DARK, LIGHT
}

private val DarkColorScheme = darkColorScheme(
    primary = IntenseGreen,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = IntenseGreen,
    onPrimary = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF5F5F5),
    onSurface = Color.Black,
    secondary = Color.DarkGray
)

@Composable
fun BrincandoTheme(
    appThemeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appThemeMode) {
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.LIGHT -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = (appThemeMode == AppThemeMode.LIGHT)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Estilo de destaque para texto verde no modo claro - Sombra mais forte e deslocada para contorno
@Composable
fun getHighlightStyle(isLightMode: Boolean): TextStyle {
    return if (isLightMode) {
        TextStyle(
            shadow = Shadow(
                color = Color.Black,
                offset = Offset(2f, 2f),
                blurRadius = 6f
            )
        )
    } else {
        TextStyle.Default
    }
}
