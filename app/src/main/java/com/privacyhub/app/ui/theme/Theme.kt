package com.privacyhub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF3F51B5), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF), onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF00897B), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2DFDB), onSecondaryContainer = Color(0xFF00201C),
    background = Color(0xFFFAF9FC), onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFAF9FC), onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE3E1EC), onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFBAC3FF), onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF26379F), onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF80CBC4), onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048), onSecondaryContainer = Color(0xFFB2DFDB),
    background = Color(0xFF121318), onBackground = Color(0xFFE4E1E6),
    surface = Color(0xFF121318), onSurface = Color(0xFFE4E1E6),
    surfaceVariant = Color(0xFF46464F), onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF91909A),
)

@Composable
fun FocusMapTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
