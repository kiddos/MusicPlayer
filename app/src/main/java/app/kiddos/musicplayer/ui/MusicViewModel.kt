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

    val currentSong: StateFlow<AudioFile?> = playerManager.currentSong
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPositionMs: StateFlow<Long> = playerManager.currentPositionMs
    val durationMs: StateFlow<Long> = playerManager.durationMs
    val repeatMode: StateFlow<RepeatMode> = playerManager.repeatMode
    val isShuffle: StateFlow<Boolean> = playerManager.isShuffle
    val errorMessage: StateFlow<String?> = playerManager.errorMessage

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
                val scanned = scanner.scanSharedMusic()
                _folders.value = scanned
                // If a folder was selected, update its songs reference
                _selectedFolder.value?.let { current ->
                    _selectedFolder.value = scanned.find { it.path == current.path }
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
                    _folders.value = mergeFolderLists(_folders.value, customFolders)
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
                    _folders.value = mergeFolderLists(_folders.value, customFolders)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun mergeFolderLists(
        existing: List<MusicFolder>,
        incoming: List<MusicFolder>
    ): List<MusicFolder> {
        val folderMap = existing.associateBy { it.path }.toMutableMap()
        for (folder in incoming) {
            val current = folderMap[folder.path]
            if (current != null) {
                val combinedSongs = (current.songs + folder.songs).distinctBy { it.id }
                folderMap[folder.path] = current.copy(
                    songs = combinedSongs.sortedBy { it.title.lowercase() }
                )
            } else {
                folderMap[folder.path] = folder
            }
        }
        return folderMap.values.sortedWith(
            compareBy<MusicFolder> { !it.isNoFolder }.thenBy { it.name.lowercase() }
        )
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
}
