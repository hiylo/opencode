/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : Theme.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.opencode.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.hiylo.opencode.ui.components.LocalAmoledTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9DA3FF),
    onPrimary = Color(0xFF1A1B4B),
    primaryContainer = Color(0xFF2D2F6E),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFCAC3DC),
    onSecondary = Color(0xFF322E41),
    secondaryContainer = Color(0xFF494559),
    onSecondaryContainer = Color(0xFFE7DFF8),
    tertiary = Color(0xFF7DD0E1),
    onTertiary = Color(0xFF003640),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF2B2B35),
    onSurfaceVariant = Color(0xFFC8C5D0),
    surfaceContainer = Color(0xFF1E1E25),
    surfaceContainerHigh = Color(0xFF262630),
    surfaceContainerHighest = Color(0xFF31313B),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF47464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F52B8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF0C0F6A),
    secondary = Color(0xFF5D5B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3DFF9),
    onSecondaryContainer = Color(0xFF1A182C),
    tertiary = Color(0xFF006879),
    onTertiary = Color(0xFFFFFFFF),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFE5E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceContainer = Color(0xFFF3EFF7),
    surfaceContainerHigh = Color(0xFFECE8F1),
    surfaceContainerHighest = Color(0xFFE6E2EB),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC9C5D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

/**
 * AMOLED dark color scheme — pure black surfaces for OLED battery savings.
 * Uses true black (#000000) for the main surface and very dark tones for containers,
 * ensuring cards/sheets are still visually distinguishable from the background.
 */
/** Overrides the surface family with pure black tones for OLED battery savings. */
private fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF1A1A22),
    surfaceContainer = Color(0xFF0D0D12),
    surfaceContainerLow = Color(0xFF080810),
    surfaceContainerLowest = Color.Black,
    surfaceContainerHigh = Color(0xFF141419),
    surfaceContainerHighest = Color(0xFF1C1C24)
)

/** Primary color roles for one accent choice, split by light/dark mode. */
internal data class AccentRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

internal data class AccentPalette(
    val light: AccentRoles,
    val dark: AccentRoles,
)

/** Selectable accent colors. "indigo" is the brand default and matches the base schemes. */
internal val OpenCodeAccents: Map<String, AccentPalette> = mapOf(
    "indigo" to AccentPalette(
        light = AccentRoles(Color(0xFF4F52B8), Color(0xFFFFFFFF), Color(0xFFE0E0FF), Color(0xFF0C0F6A)),
        dark = AccentRoles(Color(0xFF9DA3FF), Color(0xFF1A1B4B), Color(0xFF2D2F6E), Color(0xFFDEE0FF)),
    ),
    "violet" to AccentPalette(
        light = AccentRoles(Color(0xFF7C3AED), Color(0xFFFFFFFF), Color(0xFFEDE9FE), Color(0xFF4C1D95)),
        dark = AccentRoles(Color(0xFFC4B5FD), Color(0xFF2E1065), Color(0xFF6D28D9), Color(0xFFEDE9FE)),
    ),
    "cyan" to AccentPalette(
        light = AccentRoles(Color(0xFF0891B2), Color(0xFFFFFFFF), Color(0xFFCFFAFE), Color(0xFF164E63)),
        dark = AccentRoles(Color(0xFF67E8F9), Color(0xFF083344), Color(0xFF155E75), Color(0xFFCFFAFE)),
    ),
    "green" to AccentPalette(
        light = AccentRoles(Color(0xFF059669), Color(0xFFFFFFFF), Color(0xFFD1FAE5), Color(0xFF064E3B)),
        dark = AccentRoles(Color(0xFF6EE7B7), Color(0xFF064E3B), Color(0xFF065F46), Color(0xFFD1FAE5)),
    ),
    "amber" to AccentPalette(
        light = AccentRoles(Color(0xFFD97706), Color(0xFFFFFFFF), Color(0xFFFEF3C7), Color(0xFF78350F)),
        dark = AccentRoles(Color(0xFFFBBF24), Color(0xFF451A03), Color(0xFF92400E), Color(0xFFFEF3C7)),
    ),
    "red" to AccentPalette(
        light = AccentRoles(Color(0xFFDC2626), Color(0xFFFFFFFF), Color(0xFFFEE2E2), Color(0xFF7F1D1D)),
        dark = AccentRoles(Color(0xFFFCA5A5), Color(0xFF7F1D1D), Color(0xFFB91C1C), Color(0xFFFEE2E2)),
    ),
)

private fun darkSchemeFor(accent: AccentPalette): ColorScheme {
    val roles = accent.dark
    return DarkColorScheme.copy(
        primary = roles.primary,
        onPrimary = roles.onPrimary,
        primaryContainer = roles.primaryContainer,
        onPrimaryContainer = roles.onPrimaryContainer,
    )
}

private fun lightSchemeFor(accent: AccentPalette): ColorScheme {
    val roles = accent.light
    return LightColorScheme.copy(
        primary = roles.primary,
        onPrimary = roles.onPrimary,
        primaryContainer = roles.primaryContainer,
        onPrimaryContainer = roles.onPrimaryContainer,
    )
}

/**
 * OpenCode Material 3 Theme
 * 
 * Supports:
 * - Light/Dark theme based on system settings
 * - Dynamic color on Android 12+ (Material You)
 * - AMOLED dark mode with pure black surfaces
 * - Selectable accent colors (ignored when dynamic color is enabled)
 * - Edge-to-edge display
 */
@Composable
fun OpenCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoledDark: Boolean = false,
    accentColor: String = "indigo",
    content: @Composable () -> Unit
) {
    val accent = OpenCodeAccents[accentColor] ?: OpenCodeAccents.getValue("indigo")
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && amoledDark) scheme.withAmoledSurfaces() else scheme
        }
        darkTheme && amoledDark -> darkSchemeFor(accent).withAmoledSurfaces()
        darkTheme -> darkSchemeFor(accent)
        else -> lightSchemeFor(accent)
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use surface color for status bar (less jarring than primary)
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalAmoledTheme provides (darkTheme && amoledDark)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
