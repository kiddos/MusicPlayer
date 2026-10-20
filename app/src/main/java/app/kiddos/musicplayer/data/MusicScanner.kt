package app.kiddos.musicplayer.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import app.kiddos.musicplayer.model.AudioFile
import app.kiddos.musicplayer.model.MusicFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MusicScanner(private val context: Context? = null) {

    suspend fun scanSharedMusic(): List<MusicFolder> = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext emptyList()
        val audioFiles = mutableListOf<AudioFile>()
        val contentResolver: ContentResolver = ctx.contentResolver

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
            projection.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }

        // Filter for MP3 files specifically
        val selection = "(${MediaStore.Audio.Media.DISPLAY_NAME} LIKE '%.mp3' OR " +
                "${MediaStore.Audio.Media.MIME_TYPE} = 'audio/mpeg' OR " +
                "${MediaStore.Audio.Media.MIME_TYPE} = 'audio/mp3') AND " +
                "${MediaStore.Audio.Media.SIZE} > 0"

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            contentResolver.query(
                collectionUri,
                projection.toTypedArray(),
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                } else -1

                val relativePathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collectionUri, id)
                    val rawTitle = cursor.getString(titleCol)
                    val artist = cursor.getString(artistCol) ?: "<Unknown Artist>"
                    val album = cursor.getString(albumCol) ?: "<Unknown Album>"
                    val durationMs = cursor.getLong(durationCol)
                    val data = cursor.getString(dataCol) ?: ""
                    val displayName = cursor.getString(nameCol) ?: "Track_$id.mp3"
                    val size = cursor.getLong(sizeCol)

                    val title = when {
                        !rawTitle.isNullOrBlank() -> rawTitle
                        displayName.isNotBlank() -> displayName.substringBeforeLast('.')
                        else -> "Track $id"
                    }

                    // Extract folder information
                    val (folderName, folderPath) = resolveFolderInfo(
                        data = data,
                        bucketDisplayName = if (bucketCol != -1) cursor.getString(bucketCol) else null,
                        relativePath = if (relativePathCol != -1) cursor.getString(relativePathCol) else null
                    )

                    audioFiles.add(
                        AudioFile(
                            id = id,
                            title = title,
                            artist = if (artist.contains("<unknown>", ignoreCase = true)) "Unknown Artist" else artist,
                            album = if (album.contains("<unknown>", ignoreCase = true)) "Unknown Album" else album,
                            durationMs = durationMs,
                            uri = contentUri,
                            path = data,
                            folderName = folderName,
                            folderPath = folderPath,
                            size = size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If MediaStore returned empty, fallback to scanning common public music folders directly
        if (audioFiles.isEmpty()) {
            val directFiles = scanPublicDirectoriesDirectly()
            audioFiles.addAll(directFiles)
        }

        groupAndSortIntoFolders(audioFiles)
    }

    internal fun resolveFolderInfo(
        data: String,
        bucketDisplayName: String?,
        relativePath: String?
    ): Pair<String, String> {
        // First check if data points to a file directly in root storage (not in any folder)
        if (data.isNotBlank()) {
            val file = File(data)
            if (isNotInAnyFolder(file)) {
                return Pair(MusicFolder.NO_FOLDER_NAME, MusicFolder.NO_FOLDER_PATH)
            }
            val parent = file.parentFile
            if (parent != null && parent.name.isNotBlank()) {
                return Pair(parent.name, parent.absolutePath)
            }
        }

        // Check relativePath
        if (!relativePath.isNullOrBlank()) {
            val cleanRel = relativePath.trim('/')
            if (cleanRel.isEmpty() || cleanRel in listOf(".", "0", "/")) {
                return Pair(MusicFolder.NO_FOLDER_NAME, MusicFolder.NO_FOLDER_PATH)
            }
            val name = cleanRel.substringAfterLast('/', cleanRel)
            if (name.isBlank() || name in listOf(".", "0", "/")) {
                return Pair(MusicFolder.NO_FOLDER_NAME, MusicFolder.NO_FOLDER_PATH)
            }
            return Pair(name, cleanRel)
        }

        // Check bucketDisplayName
        if (!bucketDisplayName.isNullOrBlank()) {
            if (isRootBucketName(bucketDisplayName)) {
                return Pair(MusicFolder.NO_FOLDER_NAME, MusicFolder.NO_FOLDER_PATH)
            }
            return Pair(bucketDisplayName, bucketDisplayName)
        }

        return Pair(MusicFolder.NO_FOLDER_NAME, MusicFolder.NO_FOLDER_PATH)
    }

    internal fun isNotInAnyFolder(file: File): Boolean {
        val parent = file.parentFile ?: return true
        val parentPath = parent.absolutePath.trimEnd('/')
        val parentName = parent.name

        // Check against external storage root
        val externalRoot = try {
            Environment.getExternalStorageDirectory()?.absolutePath?.trimEnd('/')
        } catch (t: Throwable) {
            null
        }

        if (externalRoot != null && (parentPath == externalRoot || parentPath == "/sdcard")) {
            return true
        }

        if (parentPath in listOf("", "/", "/storage", "/storage/emulated", "/storage/emulated/0", "/sdcard", "/storage/self/primary")) {
            return true
        }

        val grandParentPath = parent.parentFile?.absolutePath?.trimEnd('/')
        if (grandParentPath == "/storage") {
            // Parent is /storage/emulated or /storage/XXXX-XXXX (SD card root volume)
            return true
        }
        if (grandParentPath == "/storage/emulated" && (parentName == "0" || parentName == "legacy")) {
            return true
        }
        if (grandParentPath == "/storage/self" && parentName == "primary") {
            return true
        }

        return false
    }

    internal fun isRootBucketName(name: String): Boolean {
        val lower = name.trim().lowercase()
        return lower in listOf("0", "root", "emulated", "sdcard", "legacy", "primary", "/", ".") ||
                lower.matches(Regex("^[0-9a-f]{4}-[0-9a-f]{4}$"))
    }

    private fun scanPublicDirectoriesDirectly(): List<AudioFile> {
        val results = mutableListOf<AudioFile>()
        val publicDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS)
        )

        for (dir in publicDirs) {
            if (dir != null && dir.exists() && dir.isDirectory) {
                scanDirectoryRecursively(dir, results)
            }
        }

        // Also scan loose MP3 files at the root of external storage (not in any folder)
        try {
            val rootDir = Environment.getExternalStorageDirectory()
            if (rootDir != null && rootDir.exists() && rootDir.isDirectory) {
                scanRootFilesDirectly(rootDir, results)
            }
        } catch (ignored: Exception) {
        }

        return results
    }

    private fun scanRootFilesDirectly(rootDir: File, outList: MutableList<AudioFile>) {
        val files = rootDir.listFiles() ?: return
        val retriever = MediaMetadataRetriever()
        for (file in files) {
            if (file.isFile && file.name.endsWith(".mp3", ignoreCase = true)) {
                var duration = 0L
                var artist = "Unknown Artist"
                var album = "Unknown Album"
                var title = file.nameWithoutExtension

                try {
                    retriever.setDataSource(file.absolutePath)
                    val dStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    duration = dStr?.toLongOrNull() ?: 0L
                    val aStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    if (!aStr.isNullOrBlank()) artist = aStr
                    val albStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    if (!albStr.isNullOrBlank()) album = albStr
                    val tStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    if (!tStr.isNullOrBlank()) title = tStr
                } catch (ignored: Exception) {
                }

                outList.add(
                    AudioFile(
                        id = file.hashCode().toLong(),
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        uri = Uri.fromFile(file),
                        path = file.absolutePath,
                        folderName = MusicFolder.NO_FOLDER_NAME,
                        folderPath = MusicFolder.NO_FOLDER_PATH,
                        size = file.length()
                    )
                )
            }
        }
        try {
            retriever.release()
        } catch (ignored: Exception) {
        }
    }

    private fun scanDirectoryRecursively(dir: File, outList: MutableList<AudioFile>) {
        val files = dir.listFiles() ?: return
        val retriever = MediaMetadataRetriever()
        for (file in files) {
            if (file.isDirectory) {
                scanDirectoryRecursively(file, outList)
            } else if (file.isFile && file.name.endsWith(".mp3", ignoreCase = true)) {
                var duration = 0L
                var artist = "Unknown Artist"
                var album = "Unknown Album"
                var title = file.nameWithoutExtension

                try {
                    retriever.setDataSource(file.absolutePath)
                    val dStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    duration = dStr?.toLongOrNull() ?: 0L
                    val aStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    if (!aStr.isNullOrBlank()) artist = aStr
                    val albStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    if (!albStr.isNullOrBlank()) album = albStr
                    val tStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    if (!tStr.isNullOrBlank()) title = tStr
                } catch (ignored: Exception) {
                }

                outList.add(
                    AudioFile(
                        id = file.hashCode().toLong(),
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        uri = Uri.fromFile(file),
                        path = file.absolutePath,
                        folderName = dir.name,
                        folderPath = dir.absolutePath,
                        size = file.length()
                    )
                )
            }
        }
        try {
            retriever.release()
        } catch (ignored: Exception) {
        }
    }

    suspend fun scanDocumentFolder(treeUri: Uri): List<MusicFolder> = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext emptyList()
        val docFile = DocumentFile.fromTreeUri(ctx, treeUri)
            ?: DocumentFile.fromSingleUri(ctx, treeUri)
            ?: return@withContext emptyList()

        if (docFile.isFile) {
            return@withContext scanDocumentFiles(listOf(treeUri))
        }

        val songs = mutableListOf<AudioFile>()
        val initialName = docFile.name
        val isRoot = initialName.isNullOrBlank() || isRootBucketName(initialName)
        val rootFolderName = if (isRoot) MusicFolder.NO_FOLDER_NAME else (initialName ?: MusicFolder.NO_FOLDER_NAME)
        scanDocumentFileRecursively(docFile, songs, rootFolderName, isTopLevel = true)
        groupAndSortIntoFolders(songs)
    }

    suspend fun scanDocumentFiles(uris: List<Uri>): List<MusicFolder> = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext emptyList()
        val songs = mutableListOf<AudioFile>()
        val retriever = MediaMetadataRetriever()
        for (uri in uris) {
            val doc = DocumentFile.fromSingleUri(ctx, uri) ?: continue
            if (doc.isFile && (doc.name?.endsWith(".mp3", ignoreCase = true) == true || doc.type?.contains("audio") == true)) {
                val fileName = doc.name ?: "song.mp3"
                var title = fileName.substringBeforeLast('.')
                var artist = "Unknown Artist"
                var album = "Unknown Album"
                var duration = 0L

                try {
                    ctx.contentResolver.openFileDescriptor(doc.uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { if (it.isNotBlank()) artist = it }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { if (it.isNotBlank()) album = it }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { if (it.isNotBlank()) title = it }
                    }
                } catch (ignored: Exception) {
                }

                songs.add(
                    AudioFile(
                        id = doc.uri.hashCode().toLong(),
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        uri = doc.uri,
                        path = doc.uri.toString(),
                        folderName = MusicFolder.NO_FOLDER_NAME,
                        folderPath = MusicFolder.NO_FOLDER_PATH,
                        size = doc.length()
                    )
                )
            }
        }
        try {
            retriever.release()
        } catch (ignored: Exception) {
        }
        groupAndSortIntoFolders(songs)
    }

    private fun scanDocumentFileRecursively(
        folder: DocumentFile,
        outList: MutableList<AudioFile>,
        parentName: String,
        isTopLevel: Boolean = false
    ) {
        val children = folder.listFiles()
        val retriever = MediaMetadataRetriever()

        for (child in children) {
            if (child.isDirectory) {
                scanDocumentFileRecursively(child, outList, child.name ?: parentName, isTopLevel = false)
            } else if (child.isFile && child.name?.endsWith(".mp3", ignoreCase = true) == true) {
                val fileName = child.name ?: "song.mp3"
                var title = fileName.substringBeforeLast('.')
                var artist = "Unknown Artist"
                var album = "Unknown Album"
                var duration = 0L

                try {
                    context?.contentResolver?.openFileDescriptor(child.uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { if (it.isNotBlank()) artist = it }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { if (it.isNotBlank()) album = it }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { if (it.isNotBlank()) title = it }
                    }
                } catch (ignored: Exception) {
                }

                val folderName = if (isTopLevel && parentName == MusicFolder.NO_FOLDER_NAME) {
                    MusicFolder.NO_FOLDER_NAME
                } else {
                    parentName
                }
                val folderPath = if (folderName == MusicFolder.NO_FOLDER_NAME) {
                    MusicFolder.NO_FOLDER_PATH
                } else {
                    folder.uri.toString()
                }

                outList.add(
                    AudioFile(
                        id = child.uri.hashCode().toLong(),
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        uri = child.uri,
                        path = child.uri.toString(),
                        folderName = folderName,
                        folderPath = folderPath,
                        size = child.length()
                    )
                )
            }
        }
        try {
            retriever.release()
        } catch (ignored: Exception) {
        }
    }

    internal fun groupAndSortIntoFolders(songs: List<AudioFile>): List<MusicFolder> {
        return songs
            .groupBy { it.folderPath }
            .map { (path, songsInFolder) ->
                val isNoFolder = path == MusicFolder.NO_FOLDER_PATH ||
                        songsInFolder.any { it.folderName == MusicFolder.NO_FOLDER_NAME }
                val folderName = if (isNoFolder) {
                    MusicFolder.NO_FOLDER_NAME
                } else {
                    songsInFolder.firstOrNull()?.folderName?.ifBlank { "Music" } ?: "Music"
                }
                MusicFolder(
                    name = folderName,
                    path = if (isNoFolder) MusicFolder.NO_FOLDER_PATH else path,
                    songs = songsInFolder.sortedBy { it.title.lowercase() },
                    isNoFolder = isNoFolder
                )
            }
            .sortedWith(compareBy({ !it.isNoFolder }, { it.name.lowercase() }))
    }
}
