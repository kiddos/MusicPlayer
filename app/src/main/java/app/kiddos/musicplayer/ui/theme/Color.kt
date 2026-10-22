package app.kiddos.musicplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Default legacy colors for compatibility
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Vibrant modern music theme palette - Light
val VibrantPrimaryLight = Color(0xFF5A45FF)
val VibrantOnPrimaryLight = Color(0xFFFFFFFF)
val VibrantPrimaryContainerLight = Color(0xFFEEECFF)
val VibrantOnPrimaryContainerLight = Color(0xFF1B00A8)

val VibrantSecondaryLight = Color(0xFF0284C7)
val VibrantOnSecondaryLight = Color(0xFFFFFFFF)
val VibrantSecondaryContainerLight = Color(0xFFE0F2FE)
val VibrantOnSecondaryContainerLight = Color(0xFF0369A1)

val VibrantTertiaryLight = Color(0xFFE11D48)
val VibrantOnTertiaryLight = Color(0xFFFFFFFF)
val VibrantTertiaryContainerLight = Color(0xFFFFE4E6)
val VibrantOnTertiaryContainerLight = Color(0xFF9F1239)

val VibrantBackgroundLight = Color(0xFFF8FAFC)
val VibrantSurfaceLight = Color(0xFFFFFFFF)
val VibrantSurfaceVariantLight = Color(0xFFF1F5F9)
val VibrantOutlineLight = Color(0xFFCBD5E1)

// Vibrant modern music theme palette - Dark
val VibrantPrimaryDark = Color(0xFFA594FD)
val VibrantOnPrimaryDark = Color(0xFF1B008C)
val VibrantPrimaryContainerDark = Color(0xFF3C27CD)
val VibrantOnPrimaryContainerDark = Color(0xFFE7E0FF)

val VibrantSecondaryDark = Color(0xFF38BDF8)
val VibrantOnSecondaryDark = Color(0xFF003554)
val VibrantSecondaryContainerDark = Color(0xFF0284C7)
val VibrantOnSecondaryContainerDark = Color(0xFFBAE6FD)

val VibrantTertiaryDark = Color(0xFFFB7185)
val VibrantOnTertiaryDark = Color(0xFF5E001F)
val VibrantTertiaryContainerDark = Color(0xFF9F1239)
val VibrantOnTertiaryContainerDark = Color(0xFFFFD9DF)

val VibrantBackgroundDark = Color(0xFF0B0F19)
val VibrantSurfaceDark = Color(0xFF131826)
val VibrantSurfaceVariantDark = Color(0xFF1E2538)
val VibrantOutlineDark = Color(0xFF334155)

// Accent & Tag colors for music metadata
val AccentViolet = Color(0xFF8B5CF6)
val AccentPink = Color(0xFFEC4899)
val AccentTeal = Color(0xFF14B8A6)
val AccentAmber = Color(0xFFF59E0B)
val AccentRose = Color(0xFFF43F5E)
val AccentCyan = Color(0xFF06B6D4)

/**
 * Reusable gradient pair for badges, cards, and vinyl discs
 */
data class GradientPair(val start: Color, val end: Color) {
    fun toBrush(): Brush = Brush.linearGradient(listOf(start, end))
}

val FolderGradients = listOf(
    GradientPair(Color(0xFF6366F1), Color(0xFF8B5CF6)), // Indigo -> Violet
    GradientPair(Color(0xFF06B6D4), Color(0xFF3B82F6)), // Cyan -> Blue
    GradientPair(Color(0xFFF43F5E), Color(0xFFFB923C)), // Rose -> Orange
    GradientPair(Color(0xFF8B5CF6), Color(0xFFEC4899)), // Violet -> Pink
    GradientPair(Color(0xFF10B981), Color(0xFF06B6D4)), // Emerald -> Teal
    GradientPair(Color(0xFFF59E0B), Color(0xFFEF4444)), // Amber -> Red
    GradientPair(Color(0xFF3B82F6), Color(0xFF8B5CF6)), // Sky -> Purple
    GradientPair(Color(0xFFEC4899), Color(0xFFF43F5E))  // Pink -> Rose
)

/**
 * Select a deterministic, harmonious gradient pair for any folder name.
 */
fun getFolderGradient(name: String, isNoFolder: Boolean): GradientPair {
    if (isNoFolder) {
        return GradientPair(Color(0xFF7C3AED), Color(0xFFC026D3)) // Royal Purple/Fuchsia
    }
    val lower = name.lowercase()
    return when {
        "chill" in lower || "vibe" in lower -> GradientPair(Color(0xFF0EA5E9), Color(0xFF10B981)) // Ocean Mint
        "jazz" in lower || "soul" in lower || "blues" in lower -> GradientPair(Color(0xFFF59E0B), Color(0xFFEA580C)) // Warm Amber
        "rock" in lower || "metal" in lower || "punk" in lower -> GradientPair(Color(0xFFEF4444), Color(0xFFB91C1C)) // Crimson Flame
        "synth" in lower || "wave" in lower || "electro" in lower -> GradientPair(Color(0xFFD946EF), Color(0xFF8B5CF6)) // Cyber Magenta
        "pop" in lower || "dance" in lower -> GradientPair(Color(0xFFEC4899), Color(0xFFF43F5E)) // Electric Pink
        "classic" in lower || "lofi" in lower -> GradientPair(Color(0xFF6366F1), Color(0xFF06B6D4)) // Indigo Cyan
        else -> {
            val index = kotlin.math.abs(name.hashCode()) % FolderGradients.size
            FolderGradients[index]
        }
    }
}