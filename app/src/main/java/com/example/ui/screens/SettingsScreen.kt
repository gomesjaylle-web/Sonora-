package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ThemeMode
import com.example.ui.viewmodel.LibraryUiState

import androidx.compose.material.icons.filled.LibraryMusic

@Composable
fun SettingsScreen(
    libraryState: LibraryUiState,
    isPermissionPermanentlyDenied: Boolean = false,
    onRequestPermission: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onRescan: () -> Unit,
    onOpenAudioFile: () -> Unit,
    onIncludeShortAudioChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        // Library stats & Rescan
        item(key = "library_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Biblioteca do Dispositivo",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Text(
                        text = if (!libraryState.hasPermission) {
                            "Acesso ao áudio ainda não autorizado."
                        } else {
                            "${libraryState.allSongs.size} músicas · ${libraryState.albums.size} álbuns · ${libraryState.artists.size} artistas · ${libraryState.folders.size} pastas"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                when {
                                    libraryState.hasPermission -> onRescan()
                                    isPermissionPermanentlyDenied -> onOpenAppSettings()
                                    else -> onRequestPermission()
                                }
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("settings_rescan_btn")
                        ) {
                            Icon(
                                imageVector = if (libraryState.hasPermission) Icons.Filled.Refresh else Icons.Filled.LibraryMusic,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    libraryState.hasPermission -> "Reescanear"
                                    isPermissionPermanentlyDenied -> "Configurações"
                                    else -> "Permitir áudio"
                                }
                            )
                        }
                        Button(
                            onClick = onOpenAudioFile,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("settings_open_file_btn")
                        ) {
                            Icon(Icons.Filled.AudioFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abrir arquivo")
                        }
                    }
                }
            }
        }

        // Short audio filter
        item(key = "filter_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onIncludeShortAudioChange(!libraryState.preferences.includeShortAudio)
                        }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Incluir áudios curtos (< 15s)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Exibe notas de voz, efeitos curtos e toques salvos no armazenamento.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = libraryState.preferences.includeShortAudio,
                        onCheckedChange = onIncludeShortAudioChange,
                        modifier = Modifier.testTag("short_audio_switch")
                    )
                }
            }
        }

        // Theme selector
        item(key = "theme_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Aparência do App",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onThemeModeChange(mode) }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .testTag("theme_option_${mode.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = libraryState.preferences.themeMode == mode,
                                onClick = { onThemeModeChange(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mode.labelPt,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }

        // About card
        item(key = "about_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Sonora Player Offline",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Reprodução local com Media3 ExoPlayer em segundo plano, suporte a controles na tela de bloqueio, pastas e playlists offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
