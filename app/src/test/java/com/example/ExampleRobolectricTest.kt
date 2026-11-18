package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FavoriteSongEntity
import com.example.data.local.PlayHistoryEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistSongCrossRef
import com.example.data.local.SonoraDatabase
import com.example.model.formatDuration
import com.example.model.formatFileSize
import com.example.ui.screens.SongsScreen
import com.example.ui.theme.SonoraTheme
import com.example.ui.viewmodel.LibraryUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: SonoraDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app name matches Sonora`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Sonora", appName)
    }

    @Test
    fun `manifest declares READ_MEDIA_AUDIO and AdMob metadata`() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions?.toList().orEmpty()
        assertTrue(permissions.contains(Manifest.permission.READ_MEDIA_AUDIO))
        assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK))
        assertTrue(permissions.contains(Manifest.permission.INTERNET))

        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        assertEquals(
            "ca-app-pub-6285196863394658~1679603005",
            appInfo.metaData.getString("com.google.android.gms.ads.APPLICATION_ID")
        )
        assertEquals(
            "ca-app-pub-6285196863394658/5561786505",
            com.example.ads.RewardedAdManager.REWARDED_AD_UNIT_ID
        )
        assertEquals(
            "ca-app-pub-6285196863394658/6206005525",
            com.example.ads.SONGS_BANNER_AD_UNIT_ID
        )
    }

    @Test
    fun `explore screen preloads ad and triggers watch callback on click`() {
        var preloaded = false
        var watched = false
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.ExploreScreen(
                    libraryState = LibraryUiState(hasPermission = false),
                    rewardedAdStatus = com.example.ads.RewardedAdStatus.READY,
                    onExploreOpened = { preloaded = true },
                    onWatchRewardedAd = { watched = true },
                    onSelectTab = {},
                    onAlbumClick = {},
                    onArtistClick = {},
                    onFolderClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        assertTrue(preloaded)
        composeTestRule.onNodeWithTag("songs_banner_ad").assertDoesNotExist()
        composeTestRule.onNodeWithTag("explore_rewarded_ad_btn").assertIsDisplayed().performClick()
        assertTrue(watched)
    }

    @Test
    fun `songs screen permission prompt triggers request callback`() {
        var requested = false
        composeTestRule.setContent {
            SonoraTheme {
                SongsScreen(
                    libraryState = LibraryUiState(hasPermission = false),
                    currentSongId = null,
                    isPlaying = false,
                    onRequestPermission = { requested = true },
                    onRescan = {},
                    onOpenAudioFile = {},
                    onSearchChange = {},
                    onSortChange = {},
                    onPlaySong = { _, _ -> },
                    onShuffleAll = {},
                    onToggleFavorite = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddToPlaylist = {},
                    onShowDetails = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("songs_banner_ad").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grant_audio_permission_btn")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(requested)
    }

    @Test
    fun `songs screen shows rationale and app settings fallback when permanently denied`() {
        var settingsOpened = false
        composeTestRule.setContent {
            SonoraTheme {
                SongsScreen(
                    libraryState = LibraryUiState(hasPermission = false),
                    currentSongId = null,
                    isPlaying = false,
                    showPermissionRationale = false,
                    isPermissionPermanentlyDenied = true,
                    onRequestPermission = {},
                    onOpenAppSettings = { settingsOpened = true },
                    onRescan = {},
                    onOpenAudioFile = {},
                    onSearchChange = {},
                    onSortChange = {},
                    onPlaySong = { _, _ -> },
                    onShuffleAll = {},
                    onToggleFavorite = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddToPlaylist = {},
                    onShowDetails = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("permission_notice_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_app_settings_btn")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(settingsOpened)
    }

    @Test
    fun `duration and size formatters work accurately`() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("3:45", formatDuration(225_000L))
        assertEquals("1:02:05", formatDuration(3_725_000L))
        assertEquals("4.5 MB", formatFileSize((4.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `room favorites playlists and history persist locally`() = runTest {
        val dao = database.sonoraDao()

        dao.addFavorite(FavoriteSongEntity(songId = 101L))
        assertTrue(dao.isFavorite(101L))
        assertEquals(listOf(101L), dao.observeFavoriteSongIds().first())

        dao.removeFavorite(101L)
        assertFalse(dao.isFavorite(101L))

        val playlistId = dao.insertPlaylist(PlaylistEntity(name = "Treino"))
        dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId = playlistId, songId = 202L, position = 0))

        val playlists = dao.observePlaylists().first()
        assertEquals(1, playlists.size)
        assertEquals("Treino", playlists.first().name)

        val refs = dao.observeAllPlaylistSongs().first()
        assertEquals(1, refs.size)
        assertEquals(202L, refs.first().songId)

        dao.upsertPlayHistory(PlayHistoryEntity(songId = 202L, playCount = 3))
        val history = dao.observeMostPlayedHistory().first()
        assertEquals(1, history.size)
        assertEquals(3, history.first().playCount)
    }
}
