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

private val DarkColorScheme = darkColorScheme(
    primary = BiolumCyan,
    onPrimary = AbyssInk,
    primaryContainer = MidnightHarbor,
    onPrimaryContainer = IceFoam,
    secondary = CopperGlow,
    onSecondary = DarkEmber,
    secondaryContainer = EmberRust,
    onSecondaryContainer = CoralWash,
    tertiary = NightReef,
    onTertiary = NightKelp,
    tertiaryContainer = DeepLagoon,
    onTertiaryContainer = ReefMint,
    background = NightPelagic,
    onBackground = BuoyLight,
    surface = HullSurface,
    onSurface = BuoyLight,
    surfaceVariant = SonarSurface,
    onSurfaceVariant = MistGrey,
    outline = DarkOutline,
    outlineVariant = HullBorder,
)

private val LightColorScheme = lightColorScheme(
    primary = PelagicNavy,
    onPrimary = Color.White,
    primaryContainer = SeafoamMist,
    onPrimaryContainer = DeepTrench,
    secondary = CopperLure,
    onSecondary = Color.White,
    secondaryContainer = CoralWash,
    onSecondaryContainer = DarkRust,
    tertiary = ReefJade,
    onTertiary = Color.White,
    tertiaryContainer = ReefMint,
    onTertiaryContainer = DeepKelp,
    background = WarmSand,
    onBackground = InkSlate,
    surface = DriftwoodPaper,
    onSurface = InkSlate,
    surfaceVariant = CoastalFog,
    onSurfaceVariant = HarborGrey,
    outline = CompassOutline,
    outlineVariant = RopeBorder,
)

val MareaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MareaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MareaTypography,
        shapes = MareaShapes,
        content = content,
    )
}
