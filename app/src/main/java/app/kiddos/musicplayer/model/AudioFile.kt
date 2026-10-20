package app.kiddos.musicplayer.model

import android.net.Uri

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

