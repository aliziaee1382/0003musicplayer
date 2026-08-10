package ir.ali0003.musicplayer.data.local

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import ir.ali0003.musicplayer.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class LocalAudioScanner(private val context: Context) {

    fun scanLocalTracksFlow(
        existingTrackIds: Set<Long> = emptySet(),
        chunkSize: Int = 100
    ): Flow<List<Track>> = flow {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection: String? = null
        val chunkBuffer = mutableListOf<Track>()
        var index = 0

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val trackId = id + 500000L

                    // Skip tracks that are already saved in local database for fast incremental scanning
                    if (existingTrackIds.contains(trackId)) {
                        index++
                        continue
                    }

                    val durationMs = cursor.getInt(durationColumn)
                    val filePath = cursor.getString(dataColumn) ?: ""
                    val rawTitle = cursor.getString(titleColumn) ?: ""

                    // Fast extension check without disk I/O
                    val extension = if (filePath.contains('.')) {
                        filePath.substringAfterLast('.').lowercase()
                    } else ""

                    if (extension.isNotEmpty() && extension !in VALID_MUSIC_EXTENSIONS) {
                        continue
                    }

                    // Skip system or hidden paths
                    if (isSystemOrHiddenPath(filePath)) {
                        continue
                    }

                    // Extract title efficiently
                    val title = when {
                        rawTitle.isNotBlank() && rawTitle != "<unknown>" -> rawTitle
                        filePath.isNotBlank() -> {
                            val fileName = filePath.substringAfterLast('/')
                            if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
                        }
                        else -> "Track $index"
                    }

                    // Extract folder name safely
                    val folderName = extractFolderName(filePath)

                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Local Songs"
                    val albumId = cursor.getLong(albumIdColumn)

                    val dateAddedSec = if (dateAddedColumn >= 0) cursor.getLong(dateAddedColumn) else 0L
                    val dateModifiedSec = if (dateModifiedColumn >= 0) cursor.getLong(dateModifiedColumn) else 0L
                    val dateAddedMs = if (dateAddedSec > 0) dateAddedSec * 1000L else System.currentTimeMillis()
                    val dateModifiedMs = if (dateModifiedSec > 0) dateModifiedSec * 1000L else System.currentTimeMillis()

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    // Fast album art resolution: Use MediaStore Album Art URI directly without opening files
                    val albumArtUri = resolveAlbumArtUriFast(albumId)

                    val cleanArtist = if (artist == "<unknown>" || artist.isBlank()) "Local Artist" else artist
                    val cleanAlbum = if (album == "<unknown>" || album.isBlank()) "Local Album" else album
                    val calculatedDurationSec = if (durationMs > 0) durationMs / 1000 else 180

                    chunkBuffer.add(
                        Track(
                            id = trackId,
                            title = title,
                            artist = cleanArtist,
                            album = cleanAlbum,
                            durationSeconds = calculatedDurationSec.coerceAtLeast(1),
                            audioUrl = if (contentUri.isNotBlank()) contentUri else filePath,
                            category = folderName,
                            coverGradientIndex = (index % 5),
                            albumArtUri = albumArtUri,
                            isLocal = true,
                            folderName = folderName,
                            dateAddedTimestamp = dateAddedMs,
                            dateModifiedTimestamp = dateModifiedMs
                        )
                    )
                    index++

                    if (chunkBuffer.size >= chunkSize) {
                        emit(chunkBuffer.toList())
                        chunkBuffer.clear()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (chunkBuffer.isNotEmpty()) {
            emit(chunkBuffer.toList())
            chunkBuffer.clear()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun scanLocalTracks(): List<Track> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Track>()
        scanLocalTracksFlow(chunkSize = 100).collect { chunk ->
            result.addAll(chunk)
        }
        result
    }

    companion object {
        private val VALID_MUSIC_EXTENSIONS = setOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus", "wma", "3gp")

        private fun isSystemOrHiddenPath(filePath: String): Boolean {
            if (filePath.isBlank()) return false
            val lowerPath = filePath.lowercase()

            if (lowerPath.contains("/.")) return true
            if (lowerPath.contains("/android/data/") || lowerPath.contains("/android/obb/")) return true
            if (lowerPath.contains("/cache/") || lowerPath.contains("/.cache/")) return true

            return false
        }

        private fun extractFolderName(filePath: String): String {
            if (filePath.isBlank() || !filePath.contains('/')) return "Phone Storage"
            return try {
                val lastSlash = filePath.lastIndexOf('/')
                if (lastSlash <= 0) return "Phone Storage"
                val prevSlash = filePath.lastIndexOf('/', lastSlash - 1)
                if (prevSlash != -1) {
                    val folder = filePath.substring(prevSlash + 1, lastSlash)
                    if (folder.isNotBlank()) folder else "Phone Storage"
                } else {
                    "Phone Storage"
                }
            } catch (e: Exception) {
                "Phone Storage"
            }
        }

        fun resolveAlbumArtUriFast(albumId: Long): String? {
            return if (albumId > 0) {
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                ).toString()
            } else null
        }

        fun resolveAlbumArtUri(
            context: Context,
            filePath: String,
            contentUri: String,
            trackId: Long,
            albumId: Long
        ): String? {
            return resolveAlbumArtUriFast(albumId)
        }

        fun extractEmbeddedPicture(
            context: Context,
            filePath: String,
            contentUri: String,
            trackId: Long
        ): String? {
            val retriever = MediaMetadataRetriever()
            try {
                if (contentUri.isNotBlank()) {
                    retriever.setDataSource(context, Uri.parse(contentUri))
                } else if (filePath.isNotBlank()) {
                    retriever.setDataSource(filePath)
                } else {
                    return null
                }

                val artBytes = retriever.embeddedPicture
                if (artBytes != null && artBytes.isNotEmpty()) {
                    val cacheDir = File(context.cacheDir, "album_covers")
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs()
                    }
                    val coverFile = File(cacheDir, "cover_$trackId.jpg")
                    if (!coverFile.exists() || coverFile.length() == 0L) {
                        coverFile.writeBytes(artBytes)
                    }
                    return Uri.fromFile(coverFile).toString()
                }
            } catch (e: Exception) {
                // Ignore corrupt or unreadable files cleanly
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore release errors
                }
            }
            return null
        }
    }
}

