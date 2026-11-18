package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Song
import com.example.model.formatDuration
import com.example.ui.components.AlbumArtwork
import com.example.ui.components.SongRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    title: String,
    subtitle: String,
    coverArtUri: Uri?,
    songs: List<Song>,
    favoriteIds: Set<Long>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShowDetails: (Song) -> Unit,
    onRemoveFromPlaylist: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val totalDurationMs = songs.sumOf { it.durationMs }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("collection_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("collection_detail_list"),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item(key = "hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AlbumArtwork(
                        albumArtUri = coverArtUri,
                        contentDescription = title,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.size(180.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$subtitle · ${songs.size} faixas · ${formatDuration(totalDurationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onPlaySong(songs, 0) },
                            enabled = songs.isNotEmpty(),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("collection_play_all_btn")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.play_all))
                        }

                        FilledTonalButton(
                            onClick = { onShufflePlay(songs) },
                            enabled = songs.isNotEmpty(),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("collection_shuffle_btn")
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.shuffle_all))
                        }
                    }
                }
            }

            if (songs.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "Nenhuma faixa nesta coleção.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        isCurrentSong = song.id == currentSongId,
                        isPlaying = isPlaying && song.id == currentSongId,
                        isFavorite = favoriteIds.contains(song.id),
                        onClick = { onPlaySong(songs, index) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        onShowDetails = { onShowDetails(song) },
                        onRemoveFromPlaylist = onRemoveFromPlaylist?.let { remover -> { remover(song) } },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}
