package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.model.ThemeMode

private val SonoraDarkColorScheme = darkColorScheme(
    primary = CoralFlame,
    onPrimary = Color.White,
    primaryContainer = DeepCoralContainer,
    onPrimaryContainer = CoralGlow,
    secondary = WarmAmber,
    onSecondary = Color(0xFF281800),
    secondaryContainer = AmberContainer,
    onSecondaryContainer = WarmAmber,
    tertiary = AcousticTeal,
    onTertiary = Color(0xFF002022),
    tertiaryContainer = TealContainer,
    onTertiaryContainer = AcousticTeal,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceVariant,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    surfaceContainerHighest = Color(0xFF312D48),
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF28243A),
    error = ErrorRed,
    onError = Color.White
)

private val SonoraLightColorScheme = lightColorScheme(
    primary = CoralFlameLight,
    onPrimary = Color.White,
    primaryContainer = CoralLightContainer,
    onPrimaryContainer = Color(0xFF5D1300),
    secondary = AmberLight,
    onSecondary = Color.White,
    secondaryContainer = AmberLightContainer,
    onSecondaryContainer = Color(0xFF3E2300),
    tertiary = AcousticTealLight,
    onTertiary = Color.White,
    tertiaryContainer = TealLightContainer,
    onTertiaryContainer = Color(0xFF002022),
    background = PaperBg,
    onBackground = TextPrimaryLight,
    surface = PaperSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = PaperBg,
    surfaceContainer = PaperSurfaceVariant,
    surfaceContainerHigh = PaperSurfaceHigh,
    surfaceContainerHighest = Color(0xFFDCD4C8),
    outline = PaperOutline,
    outlineVariant = Color(0xFFE6DFD5),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

val SonoraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SonoraTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> SonoraDarkColorScheme
        else -> SonoraLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SonoraTypography,
        shapes = SonoraShapes,
        content = content
    )
}
