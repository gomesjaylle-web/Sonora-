package com.example.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val dateAddedSec: Long,
    val contentUri: Uri,
    val albumArtUri: Uri,
    val folderName: String,
    val folderPath: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val firstYear: Int,
    val albumArtUri: Uri
)

data class Artist(
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    val sampleArtUri: Uri?
)

data class MusicFolder(
    val name: String,
    val path: String,
    val songCount: Int
)

data class PlaylistWithDetails(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val songIds: List<Long>,
    val songCount: Int,
    val coverArtUri: Uri?
)

enum class SortOrder(val labelPt: String) {
    TITLE_ASC("Título (A-Z)"),
    ARTIST_ASC("Artista (A-Z)"),
    DURATION_DESC("Maior duração"),
    DATE_ADDED_DESC("Mais recentes")
}

enum class RepeatModeOption {
    OFF,
    ALL,
    ONE
}

enum class ThemeMode(val labelPt: String) {
    DARK("Escuro Studio"),
    LIGHT("Claro Papel"),
    SYSTEM("Seguir sistema")
}

enum class ExploreTab(val labelPt: String) {
    ALBUMS("Álbuns"),
    ARTISTS("Artistas"),
    FOLDERS("Pastas")
}

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format("%.1f MB", mb)
}
