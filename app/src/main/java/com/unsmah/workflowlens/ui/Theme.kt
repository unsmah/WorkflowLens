package com.unsmah.workflowlens.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Violet = Color(0xFF6750A4)
private val VioletDark = Color(0xFFD0BCFF)
private val SurfaceDark = Color(0xFF141218)
private val Amoled = Color(0xFF000000)

private val LightScheme = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    secondary = Color(0xFF625B71),
    surface = Color(0xFFFFFBFE)
)

private val DarkScheme = darkColorScheme(
    primary = VioletDark,
    onPrimary = Color(0xFF381E72),
    secondary = Color(0xFFCCC2DC),
    surface = SurfaceDark
)

private val AmoledScheme = darkColorScheme(
    primary = VioletDark,
    onPrimary = Color(0xFF381E72),
    secondary = Color(0xFFCCC2DC),
    surface = Amoled,
    background = Amoled
)

/**
 * Theme modes: "system" (follow device), "light", "dark", "amoled" (pure-black
 * backgrounds). Dynamic Material You color on Android 12+ unless AMOLED is chosen.
 */
@Composable
fun WorkflowLensTheme(mode: String = "system", content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when (mode) {
        "light" -> if (Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(context) else LightScheme
        "dark" -> if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else DarkScheme
        "amoled" -> AmoledScheme
        else -> if (systemDark) {
            if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else DarkScheme
        } else {
            if (Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(context) else LightScheme
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
