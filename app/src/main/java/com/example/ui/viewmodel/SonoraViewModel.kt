package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalMusicScanner
import com.example.data.local.MusicRepository
import com.example.data.local.PreferencesRepository
import com.example.data.local.SonoraDatabase
import com.example.data.local.UserPreferences
import com.example.model.Album
import com.example.model.Artist
import com.example.model.ExploreTab
import com.example.model.MusicFolder
import com.example.model.PlaylistWithDetails
import com.example.model.Song
import com.example.model.SortOrder
import com.example.model.ThemeMode
import com.example.service.PlaybackController
import com.example.service.PlaybackUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val hasPermission: Boolean = false,
    val isScanning: Boolean = false,
    val allSongs: List<Song> = emptyList(),
    val filteredSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<MusicFolder> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    val favoriteSongs: List<Song> = emptyList(),
    val recentlyPlayedSongs: List<Song> = emptyList(),
    val mostPlayedSongs: List<Song> = emptyList(),
    val playlists: List<PlaylistWithDetails> = emptyList(),
    val searchQuery: String = "",
    val exploreTab: ExploreTab = ExploreTab.ALBUMS,
    val preferences: UserPreferences = UserPreferences()
) {
    fun songById(id: Long): Song? = allSongs.firstOrNull { it.id == id }
}

class SonoraViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SonoraDatabase.getInstance(application)
    private val scanner = LocalMusicScanner(application)
    private val preferencesRepository = PreferencesRepository(application)
    private val repository = MusicRepository(
        scanner = scanner,
        dao = database.sonoraDao(),
        preferencesRepository = preferencesRepository
    )

    val playbackController = PlaybackController(application) { startedSongId ->
        viewModelScope.launch {
            repository.recordPlayback(startedSongId)
        }
    }

    val playbackState: StateFlow<PlaybackUiState> = playbackController.state

    private val _hasPermission = MutableStateFlow(false)
    private val _isScanning = MutableStateFlow(false)
    private val _rawSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _exploreTab = MutableStateFlow(ExploreTab.ALBUMS)

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val userPreferences: StateFlow<UserPreferences> = repository.userPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserPreferences()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val playlistsFlow = _rawSongs.flatMapLatest { songs ->
        val songsById = songs.associateBy { it.id }
        repository.observePlaylists(songsById)
    }

    val libraryState: StateFlow<LibraryUiState> = combine(
        _hasPermission,
        _isScanning,
        _rawSongs,
        _searchQuery,
        _exploreTab,
        repository.userPreferences,
        repository.favoriteSongIds,
        repository.recentHistory,
        repository.mostPlayedHistory,
        playlistsFlow
    ) { values ->
        val hasPerm = values[0] as Boolean
        val scanning = values[1] as Boolean
        @Suppress("UNCHECKED_CAST")
        val rawSongs = values[2] as List<Song>
        val query = values[3] as String
        val exploreTab = values[4] as ExploreTab
        val prefs = values[5] as UserPreferences
        @Suppress("UNCHECKED_CAST")
        val favoriteIds = values[6] as Set<Long>
        @Suppress("UNCHECKED_CAST")
        val recentHistory = values[7] as List<com.example.data.local.PlayHistoryEntity>
        @Suppress("UNCHECKED_CAST")
        val mostHistory = values[8] as List<com.example.data.local.PlayHistoryEntity>
        @Suppress("UNCHECKED_CAST")
        val playlists = values[9] as List<PlaylistWithDetails>

        val sorted = sortSongs(rawSongs, prefs.sortOrder)
        val filtered = if (query.isBlank()) {
            sorted
        } else {
            val q = query.trim().lowercase()
            sorted.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q) ||
                    it.folderName.lowercase().contains(q)
            }
        }

        val songsById = rawSongs.associateBy { it.id }
        val favoriteSongs = rawSongs.filter { favoriteIds.contains(it.id) }
        val recentSongs = recentHistory.mapNotNull { songsById[it.songId] }
        val mostPlayedSongs = mostHistory.mapNotNull { songsById[it.songId] }

        LibraryUiState(
            hasPermission = hasPerm,
            isScanning = scanning,
            allSongs = sorted,
            filteredSongs = filtered,
            albums = repository.deriveAlbums(rawSongs),
            artists = repository.deriveArtists(rawSongs),
            folders = repository.deriveFolders(rawSongs),
            favoriteIds = favoriteIds,
            favoriteSongs = favoriteSongs,
            recentlyPlayedSongs = recentSongs,
            mostPlayedSongs = mostPlayedSongs,
            playlists = playlists,
            searchQuery = query,
            exploreTab = exploreTab,
            preferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState()
    )

    init {
        playbackController.connect()
    }

    override fun onCleared() {
        playbackController.disconnect()
        super.onCleared()
    }

    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) {
            scanLibrary()
        }
    }

    fun markAudioPermissionRequested() {
        viewModelScope.launch {
            repository.setHasRequestedAudioPermission(true)
        }
    }

    fun scanLibrary() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val includeShort = userPreferences.value.includeShortAudio
                val scanned = repository.scanSongs(includeShort)
                _rawSongs.value = scanned
                playbackController.registerLibrarySongs(scanned)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectExploreTab(tab: ExploreTab) {
        _exploreTab.value = tab
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            repository.setSortOrder(order)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setIncludeShortAudio(include: Boolean) {
        viewModelScope.launch {
            repository.setIncludeShortAudio(include)
            if (_hasPermission.value) {
                val scanned = repository.scanSongs(include)
                _rawSongs.value = scanned
                playbackController.registerLibrarySongs(scanned)
            }
        }
    }

    fun markSupporterUnlocked() {
        viewModelScope.launch {
            repository.setSupporter(true)
            _snackbarEvents.tryEmit("Obrigado por apoiar o Sonora! Conteúdo extra desbloqueado.")
        }
    }

    fun showMessage(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    fun playCollection(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        playbackController.playSongs(songs, startIndex, shuffle)
    }

    fun playNext(song: Song) {
        playbackController.playNext(song)
        _snackbarEvents.tryEmit("\"${song.title}\" tocará a seguir")
    }

    fun addToQueue(song: Song) {
        playbackController.addToQueue(song)
        _snackbarEvents.tryEmit("\"${song.title}\" adicionada à fila")
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song.id)
        }
    }

    fun createPlaylist(name: String, initialSong: Song? = null) {
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            if (id > 0L) {
                if (initialSong != null) {
                    repository.addSongToPlaylist(id, initialSong.id)
                    _snackbarEvents.tryEmit("Adicionada à playlist \"$name\"")
                } else {
                    _snackbarEvents.tryEmit("Playlist \"$name\" criada")
                }
            }
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName)
            _snackbarEvents.tryEmit("Playlist renomeada")
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            _snackbarEvents.tryEmit("Playlist excluída")
        }
    }

    fun addSongToPlaylist(playlistId: Long, playlistName: String, song: Song) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, song.id)
            _snackbarEvents.tryEmit("Adicionada a \"$playlistName\"")
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun openExternalAudioFile(uri: Uri) {
        viewModelScope.launch {
            val song = repository.extractSongFromUri(uri)
            if (song != null) {
                playbackController.playSongs(listOf(song), 0, shuffle = false)
                _snackbarEvents.tryEmit("Tocando: ${song.title}")
            } else {
                _snackbarEvents.tryEmit("Não foi possível abrir o arquivo de áudio")
            }
        }
    }

    fun songsForAlbum(albumId: Long): List<Song> {
        return libraryState.value.allSongs
            .filter { it.albumId == albumId }
            .sortedWith(compareBy<Song> { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }.thenBy { it.title.lowercase() })
    }

    fun songsForArtist(artistName: String): List<Song> {
        return libraryState.value.allSongs.filter { it.artist == artistName }
    }

    fun songsForFolder(folderPath: String): List<Song> {
        return libraryState.value.allSongs.filter { it.folderPath == folderPath }
    }

    fun songsForPlaylist(playlistId: Long): List<Song> {
        val playlist = libraryState.value.playlists.firstOrNull { it.id == playlistId } ?: return emptyList()
        val songsById = libraryState.value.allSongs.associateBy { it.id }
        return playlist.songIds.mapNotNull { songsById[it] }
    }

    private fun sortSongs(songs: List<Song>, order: SortOrder): List<Song> {
        return when (order) {
            SortOrder.TITLE_ASC -> songs.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST_ASC -> songs.sortedWith(compareBy<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
            SortOrder.DURATION_DESC -> songs.sortedByDescending { it.durationMs }
            SortOrder.DATE_ADDED_DESC -> songs.sortedByDescending { it.dateAddedSec }
        }
    }
}
