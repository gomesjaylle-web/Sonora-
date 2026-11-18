package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Song
import com.example.model.SortOrder
import com.example.ui.components.AlbumArtwork
import com.example.ui.components.SongRow
import com.example.ui.viewmodel.LibraryUiState

import androidx.compose.material.icons.filled.Settings

@Composable
fun SongsScreen(
    libraryState: LibraryUiState,
    currentSongId: Long?,
    isPlaying: Boolean,
    showPermissionRationale: Boolean = false,
    isPermissionPermanentlyDenied: Boolean = false,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit = {},
    onRescan: () -> Unit,
    onOpenAudioFile: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShowDetails: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!libraryState.hasPermission) {
        PermissionWelcomeState(
            showRationale = showPermissionRationale,
            isPermanentlyDenied = isPermissionPermanentlyDenied,
            onRequestPermission = onRequestPermission,
            onOpenAppSettings = onOpenAppSettings,
            onOpenAudioFile = onOpenAudioFile,
            modifier = modifier
        )
        return
    }

    if (libraryState.isScanning && libraryState.allSongs.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Buscando músicas no celular…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (libraryState.allSongs.isEmpty()) {
        EmptyLibraryState(
            onRescan = onRescan,
            onOpenAudioFile = onOpenAudioFile,
            modifier = modifier
        )
        return
    }

    var sortMenuExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("songs_list"),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        // Header
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.brand_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${stringResource(R.string.brand_credit)} · ${libraryState.allSongs.size} faixas no aparelho",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row {
                    IconButton(
                        onClick = onOpenAudioFile,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_audio_file_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AudioFile,
                            contentDescription = stringResource(R.string.open_audio_file),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onRescan,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("rescan_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.rescan_library),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Search Bar
        item(key = "search") {
            OutlinedTextField(
                value = libraryState.searchQuery,
                onValueChange = onSearchChange,
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (libraryState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Limpar busca"
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("songs_search_input")
            )
        }

        // Shuffle & Sort Row
        item(key = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onShuffleAll(libraryState.filteredSongs) },
                    enabled = libraryState.filteredSongs.isNotEmpty(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("shuffle_all_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.shuffle_all),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Box {
                    FilledTonalButton(
                        onClick = { sortMenuExpanded = true },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("sort_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Ordenar",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ordenar",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = order.labelPt,
                                        fontWeight = if (libraryState.preferences.sortOrder == order) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        color = if (libraryState.preferences.sortOrder == order) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                },
                                onClick = {
                                    sortMenuExpanded = false
                                    onSortChange(order)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Recently Played Shelf
        if (libraryState.recentlyPlayedSongs.isNotEmpty() && libraryState.searchQuery.isBlank()) {
            item(key = "recent_header") {
                Text(
                    text = stringResource(R.string.recently_played_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)
                )
            }
            item(key = "recent_shelf") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        libraryState.recentlyPlayedSongs.take(12),
                        key = { _, song -> "recent_${song.id}" }
                    ) { index, song ->
                        RecentSongCard(
                            song = song,
                            onClick = { onPlaySong(libraryState.recentlyPlayedSongs, index) }
                        )
                    }
                }
            }
        }

        // Tracks Section Header
        item(key = "all_tracks_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (libraryState.searchQuery.isBlank()) {
                        "Todas as músicas"
                    } else {
                        "Resultados (${libraryState.filteredSongs.size})"
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = libraryState.preferences.sortOrder.labelPt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (libraryState.filteredSongs.isEmpty()) {
            item(key = "no_search_results") {
                Text(
                    text = "Nenhuma música encontrada para \"${libraryState.searchQuery}\".",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            itemsIndexed(
                items = libraryState.filteredSongs,
                key = { _, song -> song.id }
            ) { index, song ->
                SongRow(
                    song = song,
                    isCurrentSong = song.id == currentSongId,
                    isPlaying = isPlaying && song.id == currentSongId,
                    isFavorite = libraryState.favoriteIds.contains(song.id),
                    onClick = { onPlaySong(libraryState.filteredSongs, index) },
                    onToggleFavorite = { onToggleFavorite(song) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onShowDetails = { onShowDetails(song) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentSongCard(
    song: Song,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box {
                AlbumArtwork(
                    albumArtUri = song.albumArtUri,
                    contentDescription = song.album,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionWelcomeState(
    showRationale: Boolean,
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenAudioFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.img_empty_library),
                contentDescription = "Ilustração de fones e disco de vinil",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            Text(
                text = "${stringResource(R.string.brand_title)} · ${stringResource(R.string.brand_credit)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.permission_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (isPermanentlyDenied || showRationale) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("permission_notice_card")
                ) {
                    Text(
                        text = stringResource(
                            if (isPermanentlyDenied) {
                                R.string.permission_denied_permanently
                            } else {
                                R.string.permission_rationale
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            if (isPermanentlyDenied) {
                Button(
                    onClick = onOpenAppSettings,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("open_app_settings_btn")
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.open_app_settings))
                }
            } else {
                Button(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("grant_audio_permission_btn")
                ) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.permission_button))
                }
            }

            OutlinedButton(
                onClick = onOpenAudioFile,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_single_file_btn")
            ) {
                Icon(Icons.Filled.AudioFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.open_audio_file))
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    onRescan: () -> Unit,
    onOpenAudioFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.img_empty_library),
                contentDescription = "Biblioteca vazia",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Text(
                text = "${stringResource(R.string.brand_title)} · ${stringResource(R.string.brand_credit)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.empty_library_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.empty_library_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRescan,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("rescan_empty_btn")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.rescan_library))
            }
            OutlinedButton(
                onClick = onOpenAudioFile,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_file_empty_btn")
            ) {
                Icon(Icons.Filled.AudioFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.open_audio_file))
            }
        }
    }
}
