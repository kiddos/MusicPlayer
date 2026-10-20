package app.kiddos.musicplayer.model

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kiddos.musicplayer.R

data class AudioFile(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri? = null,
    val path: String,
    val folderName: String,
    val folderPath: String,
    val size: Long
) {
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "--:--"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val formattedSize: String
        get() {
            if (size <= 0) return ""
            return when {
                size >= 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024.0))
                size >= 1024 -> "%.0f KB".format(size / 1024.0)
                else -> "$size B"
            }
        }
}

@Composable
fun AudioFile.displayArtist(): String {
    return if (artist.isBlank() || artist.contains("<unknown>", ignoreCase = true) || artist.equals("Unknown Artist", ignoreCase = true)) {
        stringResource(R.string.unknown_artist)
    } else {
        artist
    }
}

@Composable
fun AudioFile.displayAlbum(): String {
    return if (album.isBlank() || album.contains("<unknown>", ignoreCase = true) || album.equals("Unknown Album", ignoreCase = true)) {
        stringResource(R.string.unknown_album)
    } else {
        album
    }
}
