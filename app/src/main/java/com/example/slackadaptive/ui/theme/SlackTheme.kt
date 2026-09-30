package com.example.slackadaptive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SlackAubergine = Color(0xFF4A154B)
val SlackAubergineDark = Color(0xFF350D36)
val SlackAubergineContainer = Color(0xFFF4E8F4)
val SlackActiveItemLight = Color(0xFF1164A3)
val SlackGreenOnline = Color(0xFF2BAC76)
val SlackMentionBadge = Color(0xFFE01E5A)

private val LightColorScheme = lightColorScheme(
    primary = SlackAubergine,
    onPrimary = Color.White,
    primaryContainer = SlackAubergineContainer,
    onPrimaryContainer = SlackAubergineDark,
    secondary = SlackActiveItemLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E9F8),
    onSecondaryContainer = Color(0xFF08395E),
    tertiary = SlackGreenOnline,
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF8F8FA),
    background = Color(0xFFFFFFFF),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8B4E8),
    onPrimary = SlackAubergineDark,
    primaryContainer = SlackAubergine,
    onPrimaryContainer = Color(0xFFF9E6F9),
    secondary = Color(0xFF64B5F6),
    onSecondary = Color(0xFF062942),
    secondaryContainer = Color(0xFF11466E),
    onSecondaryContainer = Color(0xFFD6E9F8),
    tertiary = SlackGreenOnline,
    surface = Color(0xFF1A1D21),
    surfaceVariant = Color(0xFF222529),
    background = Color(0xFF1A1D21),
)

@Composable
fun SlackAdaptiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
