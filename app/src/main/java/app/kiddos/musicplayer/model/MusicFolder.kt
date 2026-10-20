package app.kiddos.musicplayer.model

data class MusicFolder(
    val name: String,
    val path: String,
    val songs: List<AudioFile>,
    val isNoFolder: Boolean = false
) {
    val songCount: Int
        get() = songs.size

    val totalDurationMs: Long
        get() = songs.sumOf { it.durationMs }

    companion object {
        const val NO_FOLDER_NAME = "No Folder"
        const val NO_FOLDER_PATH = "root://no_folder"
        const val NO_FOLDER_DISPLAY_PATH = "Root Storage • Standalone Audio"
    }
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE;

    fun next(): RepeatMode {
        return when (this) {
            OFF -> ALL
            ALL -> ONE
            ONE -> OFF
        }
    }
}
