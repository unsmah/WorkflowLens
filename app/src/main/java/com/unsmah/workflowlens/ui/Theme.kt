package com.unsmah.workflowlens.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Violet = Color(0xFF6750A4)
private val VioletDark = Color(0xFFD0BCFF)
private val SurfaceDark = Color(0xFF141218)

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

@Composable
fun WorkflowLensTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content
    )
}
