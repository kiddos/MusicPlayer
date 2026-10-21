package app.kiddos.musicplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.isActive
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kiddos.musicplayer.R
import app.kiddos.musicplayer.model.AudioFile
import app.kiddos.musicplayer.model.MusicFolder
import app.kiddos.musicplayer.model.RepeatMode
import app.kiddos.musicplayer.model.displayAlbum
import app.kiddos.musicplayer.model.displayArtist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    song: AudioFile?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    repeatMode: RepeatMode,
    isShuffle: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    if (song == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Handle user scrubbing slider
    var isUserDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val displayPosition = if (isUserDragging) dragPositionMs.toLong() else currentPositionMs
    val effectiveDuration = durationMs.coerceAtLeast(1L)

    // Smooth vinyl rotation (maintains angle when paused, no abrupt snap to 0)
    val vinylRotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val current = vinylRotation.value % 360f
                vinylRotation.snapTo(current)
                vinylRotation.animateTo(
                    targetValue = current + 360f,
                    animationSpec = tween(durationMillis = 12000, easing = LinearEasing)
                )
            }
        }
    }

    // Music note lively rhythmic groove & pulse animation
    val noteTransition = rememberInfiniteTransition(label = "MusicNoteGroove")
    val pulseScale by noteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = AnimRepeatMode.Reverse
        ),
        label = "NotePulseScale"
    )
    val swayAngle by noteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = AnimRepeatMode.Reverse
        ),
        label = "NoteSwayAngle"
    )

    // Smooth return to upright rest position when playback is paused
    val animatedNoteScale by animateFloatAsState(
        targetValue = if (isPlaying) pulseScale else 1f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "RestNoteScale"
    )
    val animatedNoteSway by animateFloatAsState(
        targetValue = if (isPlaying) swayAngle else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "RestNoteSway"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Elegant drag pill handle
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Top Header Bar: collapse button, folder badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_collapse),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.playing_from_folder),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val folderDisplayName = if (song.folderName == MusicFolder.NO_FOLDER_NAME) {
                        stringResource(R.string.no_folder_name)
                    } else {
                        song.folderName
                    }
                    Text(
                        text = folderDisplayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Symmetric right spacer to keep title centered
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Animated Vinyl Record Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .aspectRatio(1f)
                    .shadow(20.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.85f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Spinning vinyl layer (grooves and realistic specular sheen)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(vinylRotation.value),
                    contentAlignment = Alignment.Center
                ) {
                    // Vinyl grooves
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxSize(0.85f)
                    ) {}
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxSize(0.65f)
                    ) {}
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        modifier = Modifier.fillMaxSize(0.48f)
                    ) {}

                    // Specular sheen light reflection across grooves
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        Color.Transparent,
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // Center disc label (album art / vinyl label)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize(0.32f),
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Decorative inner ring on label
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxSize(0.78f)
                        ) {}

                        // Rhythmic pulsing & swaying MusicNote icon (stays upright, grooves to beat)
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(34.dp)
                                .graphicsLayer {
                                    scaleX = animatedNoteScale
                                    scaleY = animatedNoteScale
                                    rotationZ = animatedNoteSway
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Track Title & Artist Info
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee()
            )

            Spacer(modifier = Modifier.height(6.dp))

            val artistText = song.displayArtist()
            val albumText = song.displayAlbum()
            Text(
                text = "$artistText • $albumText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Progress Slider
            Slider(
                value = (displayPosition.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f),
                onValueChange = { frac ->
                    isUserDragging = true
                    dragPositionMs = frac * effectiveDuration
                },
                onValueChangeFinished = {
                    onSeekTo(dragPositionMs.toLong())
                    isUserDragging = false
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            // Duration timestamps
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(displayPosition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatMs(effectiveDuration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Control Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.cd_shuffle),
                        tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Previous Button
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_previous),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause Button
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Next Button
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.cd_next),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat Button (OFF / ALL / ONE)
                IconButton(onClick = onToggleRepeat) {
                    when (repeatMode) {
                        RepeatMode.OFF -> {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = stringResource(R.string.cd_repeat_off),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        RepeatMode.ALL -> {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = stringResource(R.string.cd_repeat_all),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        RepeatMode.ONE -> {
                            Icon(
                                imageVector = Icons.Default.RepeatOne,
                                contentDescription = stringResource(R.string.cd_repeat_one),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Audio Metadata & Folder Details Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.tag_mp3_audio),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (song.formattedSize.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = song.formattedSize,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = stringResource(R.string.tag_stereo),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val displayFolderPath = if (song.folderName == MusicFolder.NO_FOLDER_NAME) {
                            stringResource(R.string.no_folder_display_path)
                        } else {
                            song.folderPath
                        }
                        Text(
                            text = displayFolderPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
