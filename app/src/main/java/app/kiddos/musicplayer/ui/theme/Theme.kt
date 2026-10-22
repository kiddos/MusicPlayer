package app.kiddos.musicplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = VibrantPrimaryDark,
    onPrimary = VibrantOnPrimaryDark,
    primaryContainer = VibrantPrimaryContainerDark,
    onPrimaryContainer = VibrantOnPrimaryContainerDark,
    secondary = VibrantSecondaryDark,
    onSecondary = VibrantOnSecondaryDark,
    secondaryContainer = VibrantSecondaryContainerDark,
    onSecondaryContainer = VibrantOnSecondaryContainerDark,
    tertiary = VibrantTertiaryDark,
    onTertiary = VibrantOnTertiaryDark,
    tertiaryContainer = VibrantTertiaryContainerDark,
    onTertiaryContainer = VibrantOnTertiaryContainerDark,
    background = VibrantBackgroundDark,
    surface = VibrantSurfaceDark,
    surfaceVariant = VibrantSurfaceVariantDark,
    outline = VibrantOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = VibrantPrimaryLight,
    onPrimary = VibrantOnPrimaryLight,
    primaryContainer = VibrantPrimaryContainerLight,
    onPrimaryContainer = VibrantOnPrimaryContainerLight,
    secondary = VibrantSecondaryLight,
    onSecondary = VibrantOnSecondaryLight,
    secondaryContainer = VibrantSecondaryContainerLight,
    onSecondaryContainer = VibrantOnSecondaryContainerLight,
    tertiary = VibrantTertiaryLight,
    onTertiary = VibrantOnTertiaryLight,
    tertiaryContainer = VibrantTertiaryContainerLight,
    onTertiaryContainer = VibrantOnTertiaryContainerLight,
    background = VibrantBackgroundLight,
    surface = VibrantSurfaceLight,
    surfaceVariant = VibrantSurfaceVariantLight,
    outline = VibrantOutlineLight
)

@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep dynamicColor false by default to showcase our beautiful bespoke vibrant music palette
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
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
        typography = Typography,
        content = content
    )
}