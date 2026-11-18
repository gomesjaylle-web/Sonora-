package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SongsRoute

@Serializable
data object ExploreRoute

@Serializable
data object PlaylistsRoute

@Serializable
data object SettingsRoute

@Serializable
data class AlbumDetailRoute(val albumId: Long)

@Serializable
data class ArtistDetailRoute(val artistName: String)

@Serializable
data class FolderDetailRoute(val folderPath: String)

@Serializable
data class PlaylistDetailRoute(val playlistId: Long)

@Serializable
data object FavoritesDetailRoute

@Serializable
data object MostPlayedDetailRoute
