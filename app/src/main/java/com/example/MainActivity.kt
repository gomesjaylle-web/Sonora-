package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.model.PlaylistWithDetails
import com.example.model.Song
import com.example.ui.components.AddToPlaylistSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.components.NowPlayingSheet
import com.example.ui.components.PlaybackSpeedSheet
import com.example.ui.components.PlaylistNameDialog
import com.example.ui.components.QueueBottomSheet
import com.example.ui.components.SleepTimerSheet
import com.example.ui.components.TrackDetailsDialog
import com.example.ui.navigation.AlbumDetailRoute
import com.example.ui.navigation.ArtistDetailRoute
import com.example.ui.navigation.ExploreRoute
import com.example.ui.navigation.FavoritesDetailRoute
import com.example.ui.navigation.FolderDetailRoute
import com.example.ui.navigation.MostPlayedDetailRoute
import com.example.ui.navigation.PlaylistDetailRoute
import com.example.ui.navigation.PlaylistsRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.SongsRoute
import com.example.ui.screens.CollectionDetailScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SongsScreen
import com.example.ads.RewardedAdManager
import com.example.ui.theme.SonoraTheme
import com.example.ui.viewmodel.SonoraViewModel
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        enableEdgeToEdge()
        setContent {
            val viewModel: SonoraViewModel = viewModel()
            val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()

            SonoraTheme(themeMode = prefs.themeMode) {
                SonoraApp(viewModel = viewModel)
            }
        }
    }
}

private data class TopLevelNavItem(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
    val testTag: String
)

@Composable
fun SonoraApp(viewModel: SonoraViewModel) {
    val context = LocalContext.current
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val rewardedAdManager = remember { RewardedAdManager() }
    val rewardedAdStatus by rewardedAdManager.status.collectAsStateWithLifecycle()

    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var pendingSongForNewPlaylist by remember { mutableStateOf<Song?>(null) }
    var playlistToRename by remember { mutableStateOf<PlaylistWithDetails?>(null) }
    var songForPlaylistPicker by remember { mutableStateOf<Song?>(null) }
    var songForDetails by remember { mutableStateOf<Song?>(null) }

    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    val activity = remember(context) { context.findActivity() }
    var permissionRationaleVersion by remember { mutableStateOf(0) }

    val showAudioRationale = remember(
        libraryState.hasPermission,
        libraryState.preferences.hasRequestedAudioPermission,
        permissionRationaleVersion
    ) {
        !libraryState.hasPermission &&
            activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, audioPermission)
    }

    val isPermissionPermanentlyDenied = remember(
        libraryState.hasPermission,
        libraryState.preferences.hasRequestedAudioPermission,
        showAudioRationale
    ) {
        !libraryState.hasPermission &&
            libraryState.preferences.hasRequestedAudioPermission &&
            !showAudioRationale
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.markAudioPermissionRequested()
        val audioGranted = results[audioPermission] == true ||
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        permissionRationaleVersion++
        viewModel.onPermissionResult(audioGranted)
    }

    val openAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openExternalAudioFile(uri)
        }
    }

    fun requestAudioPermissions() {
        val permsToRequest = buildList {
            add(audioPermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionsLauncher.launch(permsToRequest)
    }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) ==
            PackageManager.PERMISSION_GRANTED
        permissionRationaleVersion++
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val navItems = remember {
        listOf(
            TopLevelNavItem(
                labelRes = R.string.nav_songs,
                selectedIcon = Icons.Filled.LibraryMusic,
                unselectedIcon = Icons.Outlined.LibraryMusic,
                route = SongsRoute,
                testTag = "nav_songs"
            ),
            TopLevelNavItem(
                labelRes = R.string.nav_explore,
                selectedIcon = Icons.Filled.Explore,
                unselectedIcon = Icons.Outlined.Explore,
                route = ExploreRoute,
                testTag = "nav_explore"
            ),
            TopLevelNavItem(
                labelRes = R.string.nav_playlists,
                selectedIcon = Icons.Filled.QueueMusic,
                unselectedIcon = Icons.Outlined.QueueMusic,
                route = PlaylistsRoute,
                testTag = "nav_playlists"
            ),
            TopLevelNavItem(
                labelRes = R.string.nav_settings,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                route = SettingsRoute,
                testTag = "nav_settings"
            )
        )
    }

    fun navigateToTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        MiniPlayer(
                            playbackState = playbackState,
                            onExpand = { showNowPlaying = true },
                            onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
                            onSkipNext = { viewModel.playbackController.skipNext() }
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            navItems.forEach { item ->
                                val selected = currentDestination?.hasRoute(item.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTopLevel(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(item.labelRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.statusBarsPadding()
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = SongsRoute,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable<SongsRoute> {
                            SongsScreen(
                                libraryState = libraryState,
                                currentSongId = playbackState.currentSong?.id,
                                isPlaying = playbackState.isPlaying,
                                showPermissionRationale = showAudioRationale,
                                isPermissionPermanentlyDenied = isPermissionPermanentlyDenied,
                                onRequestPermission = ::requestAudioPermissions,
                                onOpenAppSettings = ::openAppSettings,
                                onRescan = { viewModel.scanLibrary() },
                                onOpenAudioFile = { openAudioLauncher.launch(arrayOf("audio/*")) },
                                onSearchChange = viewModel::updateSearchQuery,
                                onSortChange = viewModel::setSortOrder,
                                onPlaySong = { list, index -> viewModel.playCollection(list, index, shuffle = false) },
                                onShuffleAll = { list -> viewModel.playCollection(list, 0, shuffle = true) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::addToQueue,
                                onAddToPlaylist = { songForPlaylistPicker = it },
                                onShowDetails = { songForDetails = it }
                            )
                        }

                        composable<ExploreRoute> {
                            ExploreScreen(
                                libraryState = libraryState,
                                isPermissionPermanentlyDenied = isPermissionPermanentlyDenied,
                                rewardedAdStatus = rewardedAdStatus,
                                onExploreOpened = { rewardedAdManager.preload(context) },
                                onWatchRewardedAd = {
                                    val hostActivity = activity
                                    if (hostActivity != null) {
                                        rewardedAdManager.showAd(
                                            activity = hostActivity,
                                            wasMusicPlaying = playbackState.isPlaying,
                                            onPauseMusic = { viewModel.playbackController.pause() },
                                            onResumeMusic = { viewModel.playbackController.resume() },
                                            onRewardEarned = { viewModel.markSupporterUnlocked() },
                                            onAdNotReady = {
                                                viewModel.showMessage("Preparando o vídeo, tente novamente em alguns segundos.")
                                            }
                                        )
                                    }
                                },
                                onRequestPermission = ::requestAudioPermissions,
                                onOpenAppSettings = ::openAppSettings,
                                onSelectTab = viewModel::selectExploreTab,
                                onAlbumClick = { album ->
                                    navController.navigate(AlbumDetailRoute(album.id))
                                },
                                onArtistClick = { artist ->
                                    navController.navigate(ArtistDetailRoute(artist.name))
                                },
                                onFolderClick = { folder ->
                                    navController.navigate(FolderDetailRoute(folder.path))
                                }
                            )
                        }

                        composable<PlaylistsRoute> {
                            PlaylistsScreen(
                                libraryState = libraryState,
                                onOpenFavorites = { navController.navigate(FavoritesDetailRoute) },
                                onOpenMostPlayed = { navController.navigate(MostPlayedDetailRoute) },
                                onOpenPlaylist = { playlist ->
                                    navController.navigate(PlaylistDetailRoute(playlist.id))
                                },
                                onCreatePlaylistClick = {
                                    pendingSongForNewPlaylist = null
                                    showCreatePlaylistDialog = true
                                },
                                onRenamePlaylist = { playlist -> playlistToRename = playlist },
                                onDeletePlaylist = { playlist -> viewModel.deletePlaylist(playlist.id) }
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                libraryState = libraryState,
                                isPermissionPermanentlyDenied = isPermissionPermanentlyDenied,
                                onRequestPermission = ::requestAudioPermissions,
                                onOpenAppSettings = ::openAppSettings,
                                onRescan = { viewModel.scanLibrary() },
                                onOpenAudioFile = { openAudioLauncher.launch(arrayOf("audio/*")) },
                                onIncludeShortAudioChange = viewModel::setIncludeShortAudio,
                                onThemeModeChange = viewModel::setThemeMode
                            )
                        }

                        composable<AlbumDetailRoute> { entry ->
                            val route = entry.toRoute<AlbumDetailRoute>()
                            val album = libraryState.albums.firstOrNull { it.id == route.albumId }
                            val songs = viewModel.songsForAlbum(route.albumId)
                            CollectionDetailScreen(
                                title = album?.name ?: "Álbum",
                                subtitle = album?.artist ?: "Artista",
                                coverArtUri = album?.albumArtUri,
                                songs = songs,
                                favoriteIds = libraryState.favoriteIds,
                                currentSongId = playbackState.currentSong?.id,
                                isPlaying = playbackState.isPlaying,
                                onBack = { navController.popBackStack() },
                                onPlaySong = { list, idx -> viewModel.playCollection(list, idx) },
                                onShufflePlay = { list -> viewModel.playCollection(list, 0, shuffle = true) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::addToQueue,
                                onAddToPlaylist = { songForPlaylistPicker = it },
                                onShowDetails = { songForDetails = it }
                            )
                        }

                        composable<ArtistDetailRoute> { entry ->
                            val route = entry.toRoute<ArtistDetailRoute>()
                            val songs = viewModel.songsForArtist(route.artistName)
                            CollectionDetailScreen(
                                title = route.artistName,
                                subtitle = "Artista",
                                coverArtUri = songs.firstOrNull()?.albumArtUri,
                                songs = songs,
                                favoriteIds = libraryState.favoriteIds,
                                currentSongId = playbackState.currentSong?.id,
                                isPlaying = playbackState.isPlaying,
                                onBack = { navController.popBackStack() },
                                onPlaySong = { list, idx -> viewModel.playCollection(list, idx) },
                                onShufflePlay = { list -> viewModel.playCollection(list, 0, shuffle = true) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::addToQueue,
                                onAddToPlaylist = { songForPlaylistPicker = it },
                                onShowDetails = { songForDetails = it }
                            )
                        }

                        composable<FolderDetailRoute> { entry ->
                            val route = entry.toRoute<FolderDetailRoute>()
                            val folder = libraryState.folders.firstOrNull { it.path == route.folderPath }
                            val songs = viewModel.songsForFolder(route.folderPath)
                            CollectionDetailScreen(
                                title = folder?.name ?: "Pasta",
                                subtitle = route.folderPath,
                                coverArtUri = songs.firstOrNull()?.albumArtUri,
                                songs = songs,
                                favoriteIds = libraryState.favoriteIds,
                                currentSongId = playbackState.currentSong?.id,
                                isPlaying = playbackState.isPlaying,
                                onBack = { navController.popBackStack() },
                                onPlaySong = { list, idx -> viewModel.playCollection(list, idx) },
                                onShufflePlay = { list -> viewModel.playCollection(list, 0, shuffle = true) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::addToQueue,
                                onAddToPlaylist = { songForPlaylistPicker = it },
                                onShowDetails = { songForDetails = it }
                            )
                        }

                        composable<PlaylistDetailRoute> { entry ->
                            val route = entry.toRoute<PlaylistDetailRoute>()
                            val playlist = libraryState.playlists.firstOrNull { it.id == route.playlistId }
                            val songs = viewModel.songsForPlaylist(route.playlistId)
                            CollectionDetailScreen(
                                title = playlist?.name ?: "Playlist",
                                subtitle = "Playlist local",
                                coverArtUri = playlist?.coverArtUri,
                                songs = songs,
                                favoriteIds = libraryState.favoriteIds,
                                currentSongId = playbackState.currentSong?.id,
                                isPlaying = playbackState.isPlaying,
                                onBack = { navController.popBackStack() },
                                onPlaySong = { list, idx -> viewModel.playCollection(list, idx) },
                                onShufflePlay = { list -> viewModel.playCollection(list, 0, shuffle = true) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::addToQueue,
                                onAddToPlaylist = { songForPlaylistPicker = it },
                                onShowDetails = { songForDetails = it },
                                onRemoveFromPlaylist = { song ->
                                    viewModel.removeSongFromPlaylist(route.playlistId, song.id)
                                }
                            )
                        }

                        composable<FavoritesDetailRoute> {
                            CollectionDetailScreen(
                                title = stringResource(R.string.favorites_title),
                                subtitle = "Coleção especial",
                                coverArtUri = libraryState.favoriteSongs.firstOrNull()?.albumArtUri,
                                songs = libraryState.favoriteSongs,
                                favoriteIds = libraryState.favoriteIds,
                                currentSongId = playbackState.currentSong?.id,
                                isPlaying = playbackState.isPlaying,
                                onBack = { navController.popBackStack() },
                                onPlaySong = { list, idx -> viewModel.playCollection(list, idx) },
                                onShufflePlay = { list -> viewModel.playCollection(list, 0, shuffle = true) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::addToQueue,
                                onAddToPlaylist = { songForPlaylistPicker = it },
                                onShowDetails = { songForDetails = it }
                            )
                        }

                        composable<MostPlayedDetailRoute> {
                            CollectionDetailScreen(
                                title = stringResource(R.string.most_played_title),
                                subtitle = "Mais ouvidas no celular",
                                coverArtUri = libraryState.mostPlayedSongs.firstOrNull()?.albumArtUri,
                                songs = libraryState.mostPlayedSongs,
                                favoriteIds = libraryState.favoriteIds,
                                currentSongId = playbackState.currentSong?.id,
                                isPlaying = playbackState.isPlaying,
                                onBack = { navController.popBackStack() },
                                onPlaySong = { list, idx -> viewModel.playCollection(list, idx) },
                                onShufflePlay = { list -> viewModel.playCollection(list, 0, shuffle = true) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onPlayNext = viewModel::playNext,
                                onAddToQueue = viewModel::addToQueue,
                                onAddToPlaylist = { songForPlaylistPicker = it },
                                onShowDetails = { songForDetails = it }
                            )
                        }
                    }

                    if (useNavigationRail) {
                        MiniPlayer(
                            playbackState = playbackState,
                            onExpand = { showNowPlaying = true },
                            onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
                            onSkipNext = { viewModel.playbackController.skipNext() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }

    if (showNowPlaying && playbackState.currentSong != null) {
        val currentSong = playbackState.currentSong!!
        NowPlayingSheet(
            playbackState = playbackState,
            isFavorite = libraryState.favoriteIds.contains(currentSong.id),
            onDismiss = { showNowPlaying = false },
            onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
            onSkipNext = { viewModel.playbackController.skipNext() },
            onSkipPrevious = { viewModel.playbackController.skipPrevious() },
            onSeekTo = { pos -> viewModel.playbackController.seekTo(pos) },
            onToggleShuffle = { viewModel.playbackController.toggleShuffle() },
            onCycleRepeat = { viewModel.playbackController.cycleRepeatMode() },
            onToggleFavorite = viewModel::toggleFavorite,
            onOpenQueue = { showQueueSheet = true },
            onOpenSleepTimer = { showSleepTimerSheet = true },
            onOpenSpeed = { showSpeedSheet = true },
            onAddToPlaylist = { songForPlaylistPicker = it }
        )
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            queue = playbackState.queue,
            currentIndex = playbackState.currentIndex,
            onSelectIndex = { index -> viewModel.playbackController.skipToQueueIndex(index) },
            onRemoveIndex = { index -> viewModel.playbackController.removeFromQueue(index) },
            onDismiss = { showQueueSheet = false }
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            currentMinutes = playbackState.sleepTimerMinutesRemaining,
            stopAfterCurrent = playbackState.stopAfterCurrentTrack,
            onSelectMinutes = { mins -> viewModel.playbackController.setSleepTimerMinutes(mins) },
            onSelectEndOfTrack = { viewModel.playbackController.setStopAfterCurrentTrack(true) },
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    if (showSpeedSheet) {
        PlaybackSpeedSheet(
            currentSpeed = playbackState.playbackSpeed,
            onSelectSpeed = { speed -> viewModel.playbackController.setPlaybackSpeed(speed) },
            onDismiss = { showSpeedSheet = false }
        )
    }

    songForPlaylistPicker?.let { song ->
        AddToPlaylistSheet(
            song = song,
            playlists = libraryState.playlists,
            onSelectPlaylist = { playlist ->
                viewModel.addSongToPlaylist(playlist.id, playlist.name, song)
            },
            onCreateNewPlaylist = {
                pendingSongForNewPlaylist = song
                songForPlaylistPicker = null
                showCreatePlaylistDialog = true
            },
            onDismiss = { songForPlaylistPicker = null }
        )
    }

    if (showCreatePlaylistDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.new_playlist),
            confirmLabel = stringResource(R.string.create),
            onConfirm = { name ->
                viewModel.createPlaylist(name, pendingSongForNewPlaylist)
                pendingSongForNewPlaylist = null
                showCreatePlaylistDialog = false
            },
            onDismiss = {
                pendingSongForNewPlaylist = null
                showCreatePlaylistDialog = false
            }
        )
    }

    playlistToRename?.let { playlist ->
        PlaylistNameDialog(
            title = "Renomear playlist",
            initialName = playlist.name,
            confirmLabel = stringResource(R.string.save),
            onConfirm = { newName ->
                viewModel.renamePlaylist(playlist.id, newName)
                playlistToRename = null
            },
            onDismiss = { playlistToRename = null }
        )
    }

    songForDetails?.let { song ->
        TrackDetailsDialog(
            song = song,
            onDismiss = { songForDetails = null }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
