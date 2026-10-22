package app.kiddos.musicplayer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kiddos.musicplayer.model.AudioFile
import app.kiddos.musicplayer.model.MusicFolder
import app.kiddos.musicplayer.model.RepeatMode
import app.kiddos.musicplayer.ui.components.FullPlayerSheet
import app.kiddos.musicplayer.ui.components.MiniPlayer
import app.kiddos.musicplayer.ui.screens.FolderListScreen
import app.kiddos.musicplayer.ui.screens.SongListScreen
import app.kiddos.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabletUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleSongs = listOf(
        AudioFile(
            id = 1L,
            title = "Midnight Drive",
            artist = "Retro Boy",
            album = "Synth Vibes",
            durationMs = 210000L,
            uri = null,
            path = "/storage/emulated/0/Music/Synthwave/Midnight_Drive.mp3",
            folderName = "Synthwave",
            folderPath = "/storage/emulated/0/Music/Synthwave",
            size = 4096000L
        ),
        AudioFile(
            id = 2L,
            title = "Neon Sunset",
            artist = "Retro Boy",
            album = "Synth Vibes",
            durationMs = 180000L,
            uri = null,
            path = "/storage/emulated/0/Music/Synthwave/Neon_Sunset.mp3",
            folderName = "Synthwave",
            folderPath = "/storage/emulated/0/Music/Synthwave",
            size = 3500000L
        )
    )

    private val sampleFolders = listOf(
        MusicFolder(
            name = "Synthwave",
            path = "/storage/emulated/0/Music/Synthwave",
            songs = sampleSongs
        ),
        MusicFolder(
            name = "Classical",
            path = "/storage/emulated/0/Music/Classical",
            songs = emptyList()
        )
    )

    @Test
    fun tablet_folderListScreen_displaysFoldersAndCounts() {
        var clickedFolder: MusicFolder? = null

        composeTestRule.setContent {
            MusicPlayerTheme {
                FolderListScreen(
                    folders = sampleFolders,
                    isScanning = false,
                    onFolderClick = { clickedFolder = it },
                    onRescanClick = {},
                    onPickFolderClick = {},
                    bottomPadding = PaddingValues(0.dp)
                )
            }
        }

        // Check top bar and folder contents
        composeTestRule.onNodeWithText("Music Folders").assertIsDisplayed()
        composeTestRule.onNodeWithText("Synthwave").assertIsDisplayed()
        composeTestRule.onNodeWithText("Classical").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 MP3s").assertIsDisplayed()

        // Click folder
        composeTestRule.onNodeWithText("Synthwave").performClick()
        assertEquals("Synthwave", clickedFolder?.name)
    }

    @Test
    fun tablet_folderListScreen_emptyState_showsActions() {
        var scanClicked = false
        var folderClicked = false

        composeTestRule.setContent {
            MusicPlayerTheme {
                FolderListScreen(
                    folders = emptyList(),
                    isScanning = false,
                    onFolderClick = {},
                    onRescanClick = { scanClicked = true },
                    onPickFolderClick = { folderClicked = true },
                    bottomPadding = PaddingValues(0.dp)
                )
            }
        }

        composeTestRule.onNodeWithText("No MP3 Music Found").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan").performClick()
        assertTrue(scanClicked)

        composeTestRule.onNodeWithText("Folder").performClick()
        assertTrue(folderClicked)
    }

    @Test
    fun tablet_songListScreen_displaysSongsAndPlays() {
        var selectedSong: AudioFile? = null
        var playAllClicked = false
        var shuffleClicked = false

        composeTestRule.setContent {
            MusicPlayerTheme {
                SongListScreen(
                    folder = sampleFolders[0],
                    currentSong = sampleSongs[0],
                    isPlaying = true,
                    onSongClick = { selectedSong = it },
                    onPlayAllClick = { playAllClicked = true },
                    onShuffleAllClick = { shuffleClicked = true },
                    onBackClick = {},
                    bottomPadding = PaddingValues(0.dp)
                )
            }
        }

        // Header and action buttons
        composeTestRule.onNodeWithText("Synthwave").assertIsDisplayed()
        composeTestRule.onNodeWithText("Play All").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shuffle").assertIsDisplayed()

        // Songs
        composeTestRule.onNodeWithText("Midnight Drive").assertIsDisplayed()
        composeTestRule.onNodeWithText("Neon Sunset").assertIsDisplayed()

        // Click action buttons
        composeTestRule.onNodeWithText("Play All").performClick()
        assertTrue(playAllClicked)

        composeTestRule.onNodeWithText("Shuffle").performClick()
        assertTrue(shuffleClicked)

        // Click song
        composeTestRule.onNodeWithText("Neon Sunset").performClick()
        assertEquals("Neon Sunset", selectedSong?.title)
    }

    @Test
    fun tablet_miniPlayer_rendersAndTriggersActions() {
        var playPauseClicked = false
        var nextClicked = false
        var miniPlayerTapped = false

        composeTestRule.setContent {
            MusicPlayerTheme {
                MiniPlayer(
                    currentSong = sampleSongs[0],
                    isPlaying = true,
                    currentPositionMs = 30000L,
                    durationMs = 210000L,
                    onPlayPause = { playPauseClicked = true },
                    onNext = { nextClicked = true },
                    onClick = { miniPlayerTapped = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Midnight Drive").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retro Boy • Synthwave").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Pause").performClick()
        assertTrue(playPauseClicked)

        composeTestRule.onNodeWithContentDescription("Next").performClick()
        assertTrue(nextClicked)

        composeTestRule.onNodeWithText("Midnight Drive").performClick()
        assertTrue(miniPlayerTapped)
    }

    @Test
    fun tablet_fullPlayerSheet_rendersControlsAndMetadata() {
        var playPauseClicked = false
        var nextClicked = false
        var prevClicked = false
        var toggleRepeatClicked = false
        var toggleShuffleClicked = false
        var dismissed = false
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MusicPlayerTheme {
                FullPlayerSheet(
                    song = sampleSongs[0],
                    isPlaying = true,
                    currentPositionMs = 45000L,
                    durationMs = 210000L,
                    repeatMode = RepeatMode.ALL,
                    isShuffle = true,
                    onPlayPause = { playPauseClicked = true },
                    onNext = { nextClicked = true },
                    onPrevious = { prevClicked = true },
                    onToggleRepeat = { toggleRepeatClicked = true },
                    onToggleShuffle = { toggleShuffleClicked = true },
                    onSeekTo = {},
                    onDismiss = { dismissed = true }
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000)

        composeTestRule.onNodeWithText("PLAYING FROM FOLDER").assertIsDisplayed()
        composeTestRule.onNodeWithText("Midnight Drive").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retro Boy • Synth Vibes").assertIsDisplayed()

        // Transport controls
        composeTestRule.onNodeWithContentDescription("Pause").performClick()
        assertTrue(playPauseClicked)

        composeTestRule.onNodeWithContentDescription("Next").performClick()
        assertTrue(nextClicked)

        composeTestRule.onNodeWithContentDescription("Previous").performClick()
        assertTrue(prevClicked)

        composeTestRule.onNodeWithContentDescription("Repeat All").performClick()
        assertTrue(toggleRepeatClicked)

        composeTestRule.onNodeWithContentDescription("Shuffle").performClick()
        assertTrue(toggleShuffleClicked)

        composeTestRule.onNodeWithContentDescription("Collapse").performClick()
        assertTrue(dismissed)
    }
}
