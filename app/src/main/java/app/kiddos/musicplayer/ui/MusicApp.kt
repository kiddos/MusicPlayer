package app.kiddos.musicplayer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kiddos.musicplayer.R
import app.kiddos.musicplayer.ui.components.FullPlayerSheet
import app.kiddos.musicplayer.ui.components.MiniPlayer
import app.kiddos.musicplayer.ui.screens.FolderListScreen
import app.kiddos.musicplayer.ui.screens.SongListScreen

@Composable
fun MusicApp(
    viewModel: MusicViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val folders by viewModel.folders.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showFullPlayer by remember { mutableStateOf(false) }

    // Required audio read permission depending on API version
    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updatePermission(granted)
    }

    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* optional notification permission handled */ }

    // Folder picker launcher for SAF
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (ignored: Exception) {
            }
            viewModel.scanCustomFolder(uri)
        }
    }

    // File picker launcher for selecting individual loose audio files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (ignored: Exception) {
                }
            }
            viewModel.scanCustomFiles(uris)
        }
    }

    // Initial check on mount
    LaunchedEffect(Unit) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            requiredPermission
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.updatePermission(isGranted)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Error messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Back handling: close full player if open, else if inside folder return to folder list
    BackHandler(enabled = showFullPlayer || selectedFolder != null) {
        if (showFullPlayer) {
            showFullPlayer = false
        } else if (selectedFolder != null) {
            viewModel.clearSelectedFolder()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MiniPlayer(
                currentSong = currentSong,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                onPlayPause = { viewModel.playPause() },
                onNext = { viewModel.next() },
                onClick = { showFullPlayer = true }
            )
        }
    ) { innerPadding ->
        if (!hasPermission) {
            PermissionRequiredScreen(
                permissionName = requiredPermission,
                onRequestPermission = { permissionLauncher.launch(requiredPermission) },
                onPickFolder = { folderPickerLauncher.launch(null) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            val currentSelected = selectedFolder
            if (currentSelected != null) {
                SongListScreen(
                    folder = currentSelected,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onSongClick = { song -> viewModel.playSong(song, currentSelected) },
                    onPlayAllClick = { viewModel.playAll(currentSelected, shuffle = false) },
                    onShuffleAllClick = { viewModel.playAll(currentSelected, shuffle = true) },
                    onBackClick = { viewModel.clearSelectedFolder() },
                    bottomPadding = innerPadding
                )
            } else {
                FolderListScreen(
                    folders = folders,
                    isScanning = isScanning,
                    onFolderClick = { folder -> viewModel.selectFolder(folder) },
                    onRescanClick = { viewModel.scanMusic() },
                    onPickFolderClick = { folderPickerLauncher.launch(null) },
                    onPickFilesClick = { filePickerLauncher.launch(arrayOf("audio/*", "application/octet-stream")) },
                    currentLanguage = currentLanguage,
                    onLanguageSelect = { lang -> viewModel.setLanguage(lang) },
                    bottomPadding = innerPadding
                )
            }
        }

        if (showFullPlayer && currentSong != null) {
            FullPlayerSheet(
                song = currentSong,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                repeatMode = repeatMode,
                isShuffle = isShuffle,
                onPlayPause = { viewModel.playPause() },
                onNext = { viewModel.next() },
                onPrevious = { viewModel.previous() },
                onToggleRepeat = { viewModel.toggleRepeat() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onSeekTo = { pos -> viewModel.seekTo(pos) },
                onDismiss = { showFullPlayer = false }
            )
        }
    }
}

@Composable
fun PermissionRequiredScreen(
    permissionName: String,
    onRequestPermission: () -> Unit,
    onPickFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.permission_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onRequestPermission,
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.Security, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.btn_grant_permission))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.permission_manual_pick_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onPickFolder,
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.btn_select_music_folder))
        }
    }
}
