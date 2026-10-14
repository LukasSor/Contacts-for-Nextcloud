package com.nextcloud.contacts.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.nextcloud.contacts.data.prefs.ThemeMode

/** Tonal spot from [NextcloudBlue] when dynamic color is off. */
@Composable
fun NextcloudContactsTheme(
    themeMode: ThemeMode,
    useDynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val amoled = themeMode == ThemeMode.AMOLED

    val context = LocalContext.current
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            if (amoled) dynamic.toAmoled() else dynamic
        }
        else -> {
            remember(darkTheme, amoled) {
                nextcloudBrandColorScheme(isDark = darkTheme, isAmoled = amoled)
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

/** Tonal spot from [NextcloudBlue]. Light surface is white; dark surface is `#121212`. */
private fun nextcloudBrandColorScheme(isDark: Boolean, isAmoled: Boolean): ColorScheme {
    val scheme = dynamicColorScheme(
        seedColor = NextcloudBlue,
        isDark = isDark,
        isAmoled = isAmoled,
        style = PaletteStyle.TonalSpot,
        contrastLevel = 0.0,
    )
    return when {
        isAmoled -> scheme.toAmoled()
        isDark -> scheme.copy(
            background = NextcloudDarkBackground,
            surface = NextcloudDarkBackground,
            surfaceBright = Color(0xFF2A2A2A),
            surfaceContainerLowest = Color(0xFF0C0C0C),
            surfaceContainerLow = Color(0xFF1A1A1A),
            surfaceContainer = Color(0xFF1E1E1E),
            surfaceContainerHigh = Color(0xFF242424),
            surfaceContainerHighest = Color(0xFF2C2C2C),
            inverseSurface = Color(0xFFE4E4E4),
            inverseOnSurface = Color(0xFF1C1C1C),
        )
        else -> scheme.copy(
            background = NextcloudLightBackground,
            surface = NextcloudLightBackground,
            surfaceBright = NextcloudLightBackground,
            surfaceContainerLowest = NextcloudLightBackground,
            surfaceContainerLow = Color(0xFFF7F7F7),
            surfaceContainer = Color(0xFFF2F2F2),
            surfaceContainerHigh = Color(0xFFEDEDED),
            surfaceContainerHighest = Color(0xFFE8E8E8),
            inverseSurface = Color(0xFF2F2F2F),
            inverseOnSurface = Color(0xFFF1F1F1),
        )
    }
}

private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF1A1A1A),
    inverseSurface = Color(0xFFE4E4E4),
    inverseOnSurface = Color(0xFF1C1C1C),
)
