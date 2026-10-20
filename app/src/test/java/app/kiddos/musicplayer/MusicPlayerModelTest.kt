package app.kiddos.musicplayer

import app.kiddos.musicplayer.model.AudioFile
import app.kiddos.musicplayer.model.MusicFolder
import app.kiddos.musicplayer.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicPlayerModelTest {

    @Test
    fun repeatMode_cyclesCorrectly() {
        var mode = RepeatMode.OFF
        mode = mode.next()
        assertEquals(RepeatMode.ALL, mode)
        mode = mode.next()
        assertEquals(RepeatMode.ONE, mode)
        mode = mode.next()
        assertEquals(RepeatMode.OFF, mode)
    }

    @Test
    fun audioFile_formattedDuration() {
        val file1 = AudioFile(
            id = 1L,
            title = "Song 1",
            artist = "Artist",
            album = "Album",
            durationMs = 65000L, // 1 min 5 sec
            uri = null,
            path = "/storage/emulated/0/Music/song1.mp3",
            folderName = "Music",
            folderPath = "/storage/emulated/0/Music",
            size = 1024L
        )
        assertEquals("1:05", file1.formattedDuration)

        val file2 = file1.copy(durationMs = 0L)
        assertEquals("--:--", file2.formattedDuration)

        val file3 = file1.copy(durationMs = 360000L) // 6 min 0 sec
        assertEquals("6:00", file3.formattedDuration)
    }

    @Test
    fun musicFolder_countsAndDuration() {
        val song1 = AudioFile(1, "A", "Artist", "Album", 60000, null, "/path/1.mp3", "Rock", "/Rock", 100)
        val song2 = AudioFile(2, "B", "Artist", "Album", 120000, null, "/path/2.mp3", "Rock", "/Rock", 200)

        val folder = MusicFolder(
            name = "Rock",
            path = "/Rock",
            songs = listOf(song1, song2)
        )

        assertEquals(2, folder.songCount)
        assertEquals(180000L, folder.totalDurationMs)
        assertEquals(false, folder.isNoFolder)
    }

    @Test
    fun musicFolder_isNoFolder_flagAndDefaults() {
        val rootFolder = MusicFolder(
            name = MusicFolder.NO_FOLDER_NAME,
            path = MusicFolder.NO_FOLDER_PATH,
            songs = emptyList(),
            isNoFolder = true
        )
        assertEquals("No Folder", MusicFolder.NO_FOLDER_NAME)
        assertEquals("root://no_folder", MusicFolder.NO_FOLDER_PATH)
        assertEquals(true, rootFolder.isNoFolder)
        assertEquals(0, rootFolder.songCount)
    }

    @Test
    fun musicScanner_resolveFolderInfo_rootStorageFiles() {
        val scanner = app.kiddos.musicplayer.data.MusicScanner()

        // 1. Emulated storage root (/storage/emulated/0/song.mp3)
        val (name1, path1) = scanner.resolveFolderInfo("/storage/emulated/0/song.mp3", "0", null)
        assertEquals(MusicFolder.NO_FOLDER_NAME, name1)
        assertEquals(MusicFolder.NO_FOLDER_PATH, path1)

        // 2. /sdcard root symlink
        val (name2, path2) = scanner.resolveFolderInfo("/sdcard/track.mp3", "sdcard", null)
        assertEquals(MusicFolder.NO_FOLDER_NAME, name2)
        assertEquals(MusicFolder.NO_FOLDER_PATH, path2)

        // 3. SD card root volume (/storage/ABCD-1234/song.mp3)
        val (name3, path3) = scanner.resolveFolderInfo("/storage/ABCD-1234/song.mp3", "ABCD-1234", null)
        assertEquals(MusicFolder.NO_FOLDER_NAME, name3)
        assertEquals(MusicFolder.NO_FOLDER_PATH, path3)

        // 4. Scoped storage with empty relativePath and "0" bucket
        val (name4, path4) = scanner.resolveFolderInfo("", "0", "")
        assertEquals(MusicFolder.NO_FOLDER_NAME, name4)
        assertEquals(MusicFolder.NO_FOLDER_PATH, path4)

        // 5. Scoped storage with "/" relativePath and null bucket
        val (name5, path5) = scanner.resolveFolderInfo("", null, "/")
        assertEquals(MusicFolder.NO_FOLDER_NAME, name5)
        assertEquals(MusicFolder.NO_FOLDER_PATH, path5)

        // 6. Scoped storage with null relativePath and null bucket
        val (name6, path6) = scanner.resolveFolderInfo("", null, null)
        assertEquals(MusicFolder.NO_FOLDER_NAME, name6)
        assertEquals(MusicFolder.NO_FOLDER_PATH, path6)
    }

    @Test
    fun musicScanner_resolveFolderInfo_subfolderFiles() {
        val scanner = app.kiddos.musicplayer.data.MusicScanner()

        // 1. File in Music subfolder
        val (name1, path1) = scanner.resolveFolderInfo("/storage/emulated/0/Music/song.mp3", "Music", "Music/")
        assertEquals("Music", name1)
        assertEquals("/storage/emulated/0/Music", path1)

        // 2. File in nested subfolder
        val (name2, path2) = scanner.resolveFolderInfo("/storage/emulated/0/Download/Favorites/song.mp3", "Favorites", "Download/Favorites/")
        assertEquals("Favorites", name2)
        assertEquals("/storage/emulated/0/Download/Favorites", path2)

        // 3. MediaStore relativePath only
        val (name3, path3) = scanner.resolveFolderInfo("", null, "Podcasts/Tech/")
        assertEquals("Tech", name3)
        assertEquals("Podcasts/Tech", path3)
    }

    @Test
    fun musicScanner_groupAndSortIntoFolders_unitesRootSongsAndPlacesFirst() {
        val scanner = app.kiddos.musicplayer.data.MusicScanner()

        val rootSong1 = AudioFile(1, "A Root Song", "Artist", "Album", 60000, null, "/storage/emulated/0/a.mp3", MusicFolder.NO_FOLDER_NAME, MusicFolder.NO_FOLDER_PATH, 100)
        val rootSong2 = AudioFile(2, "Z Root Song", "Artist", "Album", 70000, null, "/sdcard/z.mp3", MusicFolder.NO_FOLDER_NAME, MusicFolder.NO_FOLDER_PATH, 200)
        val jazzSong = AudioFile(3, "Jazz Track", "Artist", "Album", 80000, null, "/storage/emulated/0/Jazz/track.mp3", "Jazz", "/storage/emulated/0/Jazz", 300)
        val ambientSong = AudioFile(4, "Ambient Track", "Artist", "Album", 90000, null, "/storage/emulated/0/Ambient/track.mp3", "Ambient", "/storage/emulated/0/Ambient", 400)

        val folders = scanner.groupAndSortIntoFolders(listOf(jazzSong, rootSong2, ambientSong, rootSong1))

        assertEquals(3, folders.size)

        // "No Folder" should be the first folder because it represents root files
        val firstFolder = folders[0]
        assertEquals(MusicFolder.NO_FOLDER_NAME, firstFolder.name)
        assertEquals(MusicFolder.NO_FOLDER_PATH, firstFolder.path)
        assertEquals(true, firstFolder.isNoFolder)
        assertEquals(2, firstFolder.songCount)
        assertEquals("A Root Song", firstFolder.songs[0].title)
        assertEquals("Z Root Song", firstFolder.songs[1].title)

        // Other folders are sorted alphabetically after No Folder
        assertEquals("Ambient", folders[1].name)
        assertEquals(false, folders[1].isNoFolder)
        assertEquals("Jazz", folders[2].name)
        assertEquals(false, folders[2].isNoFolder)
    }
}
