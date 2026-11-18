package com.example.data.local

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.model.Album
import com.example.model.Artist
import com.example.model.MusicFolder
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class LocalMusicScanner(private val context: Context) {

    companion object {
        private val ALBUM_ART_BASE_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }

    suspend fun scanDeviceAudio(includeShortAudio: Boolean): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        val minDurationMs = if (includeShortAudio) 1_000L else 15_000L
        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDurationMs.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Faixa sem título"
                    val rawArtist = cursor.getString(artistCol)
                    val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") {
                        "Artista desconhecido"
                    } else {
                        rawArtist
                    }
                    val rawAlbum = cursor.getString(albumCol)
                    val album = if (rawAlbum.isNullOrBlank() || rawAlbum == "<unknown>") {
                        "Álbum desconhecido"
                    } else {
                        rawAlbum
                    }
                    val albumId = cursor.getLong(albumIdCol)
                    val durationMs = cursor.getLong(durationCol)
                    val trackNumber = cursor.getInt(trackCol)
                    val year = cursor.getInt(yearCol)
                    val dateAddedSec = cursor.getLong(dateAddedCol)
                    val mimeType = cursor.getString(mimeCol) ?: "audio/*"
                    val sizeBytes = cursor.getLong(sizeCol)

                    val rawPath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else ""
                    val (folderName, folderPath) = parseFolderInfo(rawPath)

                    if (!includeShortAudio && isSystemAlertFolder(folderPath)) {
                        continue
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val albumArtUri = ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId)

                    songs.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            durationMs = durationMs,
                            trackNumber = trackNumber,
                            year = year,
                            dateAddedSec = dateAddedSec,
                            contentUri = contentUri,
                            albumArtUri = albumArtUri,
                            folderName = folderName,
                            folderPath = folderPath,
                            mimeType = mimeType,
                            sizeBytes = sizeBytes
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted yet
        } catch (_: Exception) {
            // Ignore malformed MediaStore rows
        }
        songs
    }

    suspend fun extractSongFromUri(uri: Uri): Song? = withContext(Dispatchers.IO) {
        var displayName: String? = null
        var sizeBytes = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                    if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {
        }

        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L
        var mimeType = context.contentResolver.getType(uri) ?: "audio/*"
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        val fallbackTitle = displayName?.substringBeforeLast('.') ?: "Áudio selecionado"
        val syntheticId = -(abs(uri.toString().hashCode().toLong()) + 1L)

        Song(
            id = syntheticId,
            title = title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            artist = artist?.takeIf { it.isNotBlank() } ?: "Artista desconhecido",
            album = album?.takeIf { it.isNotBlank() } ?: "Arquivo local",
            albumId = -1L,
            durationMs = durationMs,
            trackNumber = 0,
            year = 0,
            dateAddedSec = System.currentTimeMillis() / 1000L,
            contentUri = uri,
            albumArtUri = Uri.EMPTY,
            folderName = "Arquivo aberto",
            folderPath = "external",
            mimeType = mimeType,
            sizeBytes = sizeBytes
        )
    }

    fun deriveAlbums(songs: List<Song>): List<Album> {
        return songs.groupBy { it.albumId }
            .map { (albumId, albumSongs) ->
                val first = albumSongs.first()
                Album(
                    id = albumId,
                    name = first.album,
                    artist = first.artist,
                    songCount = albumSongs.size,
                    firstYear = albumSongs.maxOfOrNull { it.year } ?: 0,
                    albumArtUri = first.albumArtUri
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun deriveArtists(songs: List<Song>): List<Artist> {
        return songs.groupBy { it.artist }
            .map { (artistName, artistSongs) ->
                Artist(
                    name = artistName,
                    albumCount = artistSongs.map { it.albumId }.distinct().size,
                    songCount = artistSongs.size,
                    sampleArtUri = artistSongs.firstOrNull()?.albumArtUri
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun deriveFolders(songs: List<Song>): List<MusicFolder> {
        return songs.groupBy { it.folderPath }
            .map { (path, folderSongs) ->
                MusicFolder(
                    name = folderSongs.first().folderName,
                    path = path,
                    songCount = folderSongs.size
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun parseFolderInfo(rawPath: String): Pair<String, String> {
        if (rawPath.isBlank()) return "Armazenamento" to "/"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val clean = rawPath.trimEnd('/')
            val name = clean.substringAfterLast('/', "Armazenamento").ifBlank { "Armazenamento" }
            name to clean.ifBlank { "/" }
        } else {
            val parent = File(rawPath).parentFile
            val name = parent?.name?.ifBlank { "Armazenamento" } ?: "Armazenamento"
            val path = parent?.absolutePath ?: "/"
            name to path
        }
    }

    private fun isSystemAlertFolder(path: String): Boolean {
        val lower = path.lowercase()
        return lower.contains("/alarms") ||
            lower.contains("/notifications") ||
            lower.contains("/ringtones") ||
            lower.startsWith("alarms") ||
            lower.startsWith("notifications") ||
            lower.startsWith("ringtones")
    }
}
