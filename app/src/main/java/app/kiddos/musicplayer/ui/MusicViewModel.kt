package app.kiddos.musicplayer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.kiddos.musicplayer.data.MusicScanner
import app.kiddos.musicplayer.model.AudioFile
import app.kiddos.musicplayer.model.MusicFolder
import app.kiddos.musicplayer.model.RepeatMode
import app.kiddos.musicplayer.playback.MusicPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = MusicScanner(application)
    private val playerManager = MusicPlayerManager.getInstance(application)

    private val _folders = MutableStateFlow<List<MusicFolder>>(emptyList())
    val folders: StateFlow<List<MusicFolder>> = _folders.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedFolder = MutableStateFlow<MusicFolder?>(null)
    val selectedFolder: StateFlow<MusicFolder?> = _selectedFolder.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _currentLanguage = MutableStateFlow(app.kiddos.musicplayer.util.LocaleHelper.getSavedLanguage(application))
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    val currentSong: StateFlow<AudioFile?> = playerManager.currentSong
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPositionMs: StateFlow<Long> = playerManager.currentPositionMs
    val durationMs: StateFlow<Long> = playerManager.durationMs
    val repeatMode: StateFlow<RepeatMode> = playerManager.repeatMode
    val isShuffle: StateFlow<Boolean> = playerManager.isShuffle
    val errorMessage: StateFlow<String?> = playerManager.errorMessage

    private val customFolderUris = mutableSetOf<Uri>()
    private val customFileUris = mutableSetOf<Uri>()

    fun updatePermission(granted: Boolean) {
        _hasPermission.value = granted
        if (granted && _folders.value.isEmpty()) {
            scanMusic()
        }
    }

    fun scanMusic() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                var scanned = scanner.scanSharedMusic()
                for (treeUri in customFolderUris) {
                    val custom = scanner.scanDocumentFolder(treeUri)
                    if (custom.isNotEmpty()) {
                        scanned = mergeFolderLists(scanned, custom)
                    }
                }
                for (uris in customFileUris.chunked(50)) {
                    val custom = scanner.scanDocumentFiles(uris)
                    if (custom.isNotEmpty()) {
                        scanned = mergeFolderLists(scanned, custom)
                    }
                }
                _folders.value = scanned
                // If a folder was selected, update its songs reference
                _selectedFolder.value?.let { current ->
                    _selectedFolder.value = scanned.find { isMatchingFolder(it, current) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanCustomFolder(treeUri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val customFolders = scanner.scanDocumentFolder(treeUri)
                if (customFolders.isNotEmpty()) {
                    val merged = mergeFolderLists(_folders.value, customFolders)
                    if (merged != _folders.value) {
                        customFolderUris.add(treeUri)
                    }
                    _folders.value = merged
                    _selectedFolder.value?.let { current ->
                        _selectedFolder.value = merged.find { isMatchingFolder(it, current) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanCustomFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val customFolders = scanner.scanDocumentFiles(uris)
                if (customFolders.isNotEmpty()) {
                    val merged = mergeFolderLists(_folders.value, customFolders)
                    if (merged != _folders.value) {
                        customFileUris.addAll(uris)
                    }
                    _folders.value = merged
                    _selectedFolder.value?.let { current ->
                        _selectedFolder.value = merged.find { isMatchingFolder(it, current) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    companion object {
        internal fun mergeFolderLists(
            existing: List<MusicFolder>,
            incoming: List<MusicFolder>
        ): List<MusicFolder> {
            if (incoming.isEmpty()) return existing
            if (existing.isEmpty()) return incoming

            val resultFolders = existing.map { it.copy(songs = it.songs.toMutableList()) }.toMutableList()

            for (incomingFolder in incoming) {
                val matchedFolder = resultFolders.find { isMatchingFolder(it, incomingFolder) }
                if (matchedFolder != null) {
                    val updatedSongs = matchedFolder.songs.toMutableList()
                    for (incomingSong in incomingFolder.songs) {
                        val alreadyInFolder = updatedSongs.any { isSameSong(it, incomingSong) }
                        if (!alreadyInFolder) {
                            updatedSongs.add(incomingSong)
                        }
                    }
                    val index = resultFolders.indexOf(matchedFolder)
                    resultFolders[index] = matchedFolder.copy(
                        songs = updatedSongs.sortedBy { it.title.lowercase() }
                    )
                } else {
                    val trulyNewSongs = incomingFolder.songs.filter { incSong ->
                        resultFolders.none { folder -> folder.songs.any { isSameSong(it, incSong) } }
                    }
                    if (trulyNewSongs.isNotEmpty()) {
                        resultFolders.add(
                            incomingFolder.copy(
                                songs = trulyNewSongs.sortedBy { it.title.lowercase() }
                            )
                        )
                    }
                }
            }

            return resultFolders.sortedWith(
                compareBy<MusicFolder> { !it.isNoFolder }.thenBy { it.name.lowercase() }
            )
        }

        internal fun isSameSong(a: AudioFile, b: AudioFile): Boolean {
            if (a.id == b.id) return true

            val aCleanPath = a.path.removePrefix("file://").trim()
            val bCleanPath = b.path.removePrefix("file://").trim()
            if (aCleanPath.isNotBlank() && bCleanPath.isNotBlank() && aCleanPath.equals(bCleanPath, ignoreCase = true)) {
                return true
            }

            if (a.uri != null && b.uri != null && a.uri == b.uri) {
                return true
            }

            val aFileName = aCleanPath.substringAfterLast('/').substringAfterLast("%2F").substringAfterLast(':')
            val bFileName = bCleanPath.substringAfterLast('/').substringAfterLast("%2F").substringAfterLast(':')
            if (aFileName.isNotBlank() && aFileName.equals(bFileName, ignoreCase = true)) {
                if (a.size > 0L && b.size > 0L && a.size == b.size) return true
                if (a.durationMs > 0L && b.durationMs > 0L && kotlin.math.abs(a.durationMs - b.durationMs) <= 1500L) return true
            }

            if (a.title.isNotBlank() && a.title.equals(b.title, ignoreCase = true)) {
                if (a.size > 0L && b.size > 0L && a.size == b.size) return true
                val durationClose = a.durationMs > 0L && b.durationMs > 0L && kotlin.math.abs(a.durationMs - b.durationMs) <= 2000L
                val artistMatches = a.artist.equals(b.artist, ignoreCase = true) ||
                        isUnknownArtist(a.artist) || isUnknownArtist(b.artist)
                if (durationClose && artistMatches) return true
            }

            return false
        }

        private fun isUnknownArtist(artist: String?): Boolean {
            if (artist.isNullOrBlank()) return true
            val lower = artist.trim().lowercase()
            return lower in listOf("unknown", "unknown artist", "<unknown>", "<unknown artist>")
        }

        internal fun isMatchingFolder(existing: MusicFolder, incoming: MusicFolder): Boolean {
            if (existing.isNoFolder && incoming.isNoFolder) return true
            if (existing.path == MusicFolder.NO_FOLDER_PATH && incoming.path == MusicFolder.NO_FOLDER_PATH) return true
            if (existing.name.equals(MusicFolder.NO_FOLDER_NAME, ignoreCase = true) &&
                incoming.name.equals(MusicFolder.NO_FOLDER_NAME, ignoreCase = true)) return true

            val existingClean = existing.path.removePrefix("file://").trimEnd('/')
            val incomingClean = incoming.path.removePrefix("file://").trimEnd('/')
            if (existingClean.isNotBlank() && incomingClean.isNotBlank() &&
                existingClean.equals(incomingClean, ignoreCase = true)) return true

            val bothAreFilePaths = existingClean.startsWith('/') && incomingClean.startsWith('/')
            if (bothAreFilePaths && !existingClean.equals(incomingClean, ignoreCase = true)) {
                return incoming.songs.any { incSong -> existing.songs.any { exSong -> isSameSong(exSong, incSong) } }
            }

            val existingLast = existingClean.substringAfterLast('/').substringAfterLast(':')
            val incomingLast = incomingClean.substringAfterLast('/').substringAfterLast(':')
            if (existingLast.isNotBlank() && existingLast.equals(incomingLast, ignoreCase = true)) {
                if (existing.name.equals(incoming.name, ignoreCase = true)) return true
            }

            if (existing.name.isNotBlank() && existing.name.equals(incoming.name, ignoreCase = true)) {
                return true
            }

            if (incoming.songs.any { incSong -> existing.songs.any { exSong -> isSameSong(exSong, incSong) } }) {
                return true
            }

            return false
        }
    }

    fun selectFolder(folder: MusicFolder) {
        _selectedFolder.value = folder
    }

    fun clearSelectedFolder() {
        _selectedFolder.value = null
    }

    fun playSong(song: AudioFile, folder: MusicFolder) {
        playerManager.playSong(song = song, newPlaylist = folder.songs)
    }

    fun playAll(folder: MusicFolder, shuffle: Boolean = false) {
        if (folder.songs.isEmpty()) return
        if (shuffle) {
            val shuffled = folder.songs.shuffled()
            playerManager.playSong(shuffled.first(), shuffled)
        } else {
            playerManager.playSong(folder.songs.first(), folder.songs)
        }
    }

    fun playPause() {
        playerManager.playPause()
    }

    fun next() {
        playerManager.next()
    }

    fun previous() {
        playerManager.previous()
    }

    fun toggleRepeat() {
        playerManager.toggleRepeat()
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun clearError() {
        playerManager.clearError()
    }

    fun setLanguage(languageCode: String) {
        app.kiddos.musicplayer.util.LocaleHelper.setLanguage(getApplication(), languageCode)
        _currentLanguage.value = languageCode
    }
}
