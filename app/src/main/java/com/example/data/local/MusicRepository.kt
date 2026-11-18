package com.example.data.local

import android.net.Uri
import com.example.model.Album
import com.example.model.Artist
import com.example.model.MusicFolder
import com.example.model.PlaylistWithDetails
import com.example.model.Song
import com.example.model.SortOrder
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class MusicRepository(
    private val scanner: LocalMusicScanner,
    private val dao: SonoraDao,
    private val preferencesRepository: PreferencesRepository
) {
    val userPreferences: Flow<UserPreferences> = preferencesRepository.preferences

    val favoriteSongIds: Flow<Set<Long>> = dao.observeFavoriteSongIds().map { it.toSet() }

    val recentHistory: Flow<List<PlayHistoryEntity>> = dao.observeRecentHistory()

    val mostPlayedHistory: Flow<List<PlayHistoryEntity>> = dao.observeMostPlayedHistory()

    fun observePlaylists(songsById: Map<Long, Song>): Flow<List<PlaylistWithDetails>> {
        return combine(dao.observePlaylists(), dao.observeAllPlaylistSongs()) { playlists, crossRefs ->
            val grouped = crossRefs.groupBy { it.playlistId }
            playlists.map { playlist ->
                val refs = grouped[playlist.id].orEmpty()
                val songIds = refs.map { it.songId }.filter { songsById.containsKey(it) }
                val coverUri = songIds.firstOrNull()?.let { songsById[it]?.albumArtUri }
                PlaylistWithDetails(
                    id = playlist.id,
                    name = playlist.name,
                    createdAt = playlist.createdAt,
                    songIds = songIds,
                    songCount = songIds.size,
                    coverArtUri = coverUri
                )
            }
        }
    }

    suspend fun scanSongs(includeShortAudio: Boolean): List<Song> {
        return scanner.scanDeviceAudio(includeShortAudio)
    }

    suspend fun extractSongFromUri(uri: Uri): Song? {
        return scanner.extractSongFromUri(uri)
    }

    fun deriveAlbums(songs: List<Song>): List<Album> = scanner.deriveAlbums(songs)

    fun deriveArtists(songs: List<Song>): List<Artist> = scanner.deriveArtists(songs)

    fun deriveFolders(songs: List<Song>): List<MusicFolder> = scanner.deriveFolders(songs)

    suspend fun toggleFavorite(songId: Long) {
        if (dao.isFavorite(songId)) {
            dao.removeFavorite(songId)
        } else {
            dao.addFavorite(FavoriteSongEntity(songId = songId))
        }
    }

    suspend fun createPlaylist(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        return dao.insertPlaylist(PlaylistEntity(name = trimmed))
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) {
            dao.renamePlaylist(playlistId, trimmed)
        }
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val nextPos = dao.nextPlaylistPosition(playlistId)
        dao.addSongToPlaylist(
            PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = songId,
                position = nextPos
            )
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        dao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun recordPlayback(songId: Long) {
        if (songId <= 0L) return
        val existing = dao.getPlayHistory(songId)
        val newCount = (existing?.playCount ?: 0) + 1
        dao.upsertPlayHistory(
            PlayHistoryEntity(
                songId = songId,
                lastPlayedAt = System.currentTimeMillis(),
                playCount = newCount
            )
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = preferencesRepository.setThemeMode(mode)

    suspend fun setIncludeShortAudio(include: Boolean) =
        preferencesRepository.setIncludeShortAudio(include)

    suspend fun setSortOrder(order: SortOrder) = preferencesRepository.setSortOrder(order)

    suspend fun setHasRequestedAudioPermission(requested: Boolean) =
        preferencesRepository.setHasRequestedAudioPermission(requested)
}
