package com.example.service

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.model.RepeatModeOption
import com.example.model.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerMinutesRemaining: Int? = null,
    val stopAfterCurrentTrack: Boolean = false
)

class PlaybackController(
    private val context: Context,
    private val onTrackStarted: (Long) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val songsByMediaId = mutableMapOf<String, Song>()
    private var lastRecordedSongId: Long? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncFromPlayer()
            if (isPlaying) startProgressTicker() else stopProgressTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncFromPlayer()
            if (playbackState == Player.STATE_ENDED && _state.value.stopAfterCurrentTrack) {
                _state.update { it.copy(stopAfterCurrentTrack = false) }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (_state.value.stopAfterCurrentTrack && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                mediaController?.pause()
                _state.update { it.copy(stopAfterCurrentTrack = false) }
            }
            syncFromPlayer()
            val currentId = mediaItem?.mediaId?.toLongOrNull()
            if (currentId != null && currentId != lastRecordedSongId) {
                lastRecordedSongId = currentId
                onTrackStarted(currentId)
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            syncFromPlayer()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            syncFromPlayer()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncFromPlayer()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _state.update { it.copy(playbackSpeed = playbackParameters.speed) }
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val controller = future.get()
                    mediaController = controller
                    controller.addListener(playerListener)
                    syncFromPlayer()
                    if (controller.isPlaying) {
                        startProgressTicker()
                    }
                } catch (_: Exception) {
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun disconnect() {
        stopProgressTicker()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val controller = mediaController ?: run {
            connect()
            return
        }
        songs.forEach { songsByMediaId[it.id.toString()] = it }

        val targetIndex = startIndex.coerceIn(0, songs.lastIndex)
        val mediaItems = songs.map { it.toMediaItem() }

        controller.shuffleModeEnabled = shuffle
        controller.setMediaItems(mediaItems, targetIndex, 0L)
        controller.prepare()
        controller.play()
        syncFromPlayer()
        val startedId = songs[targetIndex].id
        if (startedId != lastRecordedSongId) {
            lastRecordedSongId = startedId
            onTrackStarted(startedId)
        }
    }

    fun playNext(song: Song) {
        val controller = mediaController ?: return
        songsByMediaId[song.id.toString()] = song
        if (controller.mediaItemCount == 0) {
            playSongs(listOf(song), 0)
            return
        }
        val insertIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
        controller.addMediaItem(insertIndex, song.toMediaItem())
        syncFromPlayer()
    }

    fun addToQueue(song: Song) {
        val controller = mediaController ?: return
        songsByMediaId[song.id.toString()] = song
        if (controller.mediaItemCount == 0) {
            playSongs(listOf(song), 0)
            return
        }
        controller.addMediaItem(song.toMediaItem())
        syncFromPlayer()
    }

    fun skipToQueueIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
        }
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount && controller.mediaItemCount > 1) {
            controller.removeMediaItem(index)
            syncFromPlayer()
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        } else if (controller.mediaItemCount > 0) {
            controller.seekToDefaultPosition(0)
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3_000L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            controller.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
        _state.update { it.copy(currentPositionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        syncFromPlayer()
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        syncFromPlayer()
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(sleepTimerMinutesRemaining = null, stopAfterCurrentTrack = false) }
            return
        }
        _state.update { it.copy(sleepTimerMinutesRemaining = minutes, stopAfterCurrentTrack = false) }
        sleepTimerJob = scope.launch {
            var remaining = minutes
            while (remaining > 0 && isActive) {
                delay(60_000L)
                remaining--
                _state.update { it.copy(sleepTimerMinutesRemaining = if (remaining > 0) remaining else null) }
            }
            mediaController?.pause()
        }
    }

    fun setStopAfterCurrentTrack(enabled: Boolean) {
        sleepTimerJob?.cancel()
        _state.update { it.copy(sleepTimerMinutesRemaining = null, stopAfterCurrentTrack = enabled) }
    }

    fun registerLibrarySongs(songs: List<Song>) {
        songs.forEach { songsByMediaId[it.id.toString()] = it }
        syncFromPlayer()
    }

    private fun syncFromPlayer() {
        val controller = mediaController ?: return
        val queue = buildList {
            for (i in 0 until controller.mediaItemCount) {
                val item = controller.getMediaItemAt(i)
                val resolved = songsByMediaId[item.mediaId] ?: item.toFallbackSong()
                add(resolved)
            }
        }
        val currentIdx = controller.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        val currentSong = if (currentIdx >= 0) queue[currentIdx] else null
        val duration = controller.duration.takeIf { it > 0L } ?: currentSong?.durationMs ?: 0L
        val repeatOption = when (controller.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatModeOption.ALL
            Player.REPEAT_MODE_ONE -> RepeatModeOption.ONE
            else -> RepeatModeOption.OFF
        }

        _state.update {
            it.copy(
                currentSong = currentSong,
                queue = queue,
                currentIndex = currentIdx,
                isPlaying = controller.isPlaying,
                currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                shuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = repeatOption,
                playbackSpeed = controller.playbackParameters.speed
            )
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val dur = controller.duration.takeIf { it > 0L }
                        ?: _state.value.currentSong?.durationMs ?: 0L
                    _state.update {
                        it.copy(
                            currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                            durationMs = dur
                        )
                    }
                }
                delay(400L)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun Song.toMediaItem(): MediaItem {
        val extras = Bundle().apply {
            putString("folderName", folderName)
            putString("mimeType", mimeType)
            putLong("sizeBytes", sizeBytes)
            putLong("durationMs", durationMs)
            putLong("albumId", albumId)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(contentUri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun MediaItem.toFallbackSong(): Song {
        val md = mediaMetadata
        val extras = md.extras
        return Song(
            id = mediaId.toLongOrNull() ?: -1L,
            title = md.title?.toString() ?: "Faixa de áudio",
            artist = md.artist?.toString() ?: "Artista desconhecido",
            album = md.albumTitle?.toString() ?: "Álbum desconhecido",
            albumId = extras?.getLong("albumId") ?: -1L,
            durationMs = extras?.getLong("durationMs") ?: 0L,
            trackNumber = 0,
            year = 0,
            dateAddedSec = 0L,
            contentUri = localConfiguration?.uri ?: android.net.Uri.EMPTY,
            albumArtUri = md.artworkUri ?: android.net.Uri.EMPTY,
            folderName = extras?.getString("folderName") ?: "Dispositivo",
            folderPath = "/",
            mimeType = extras?.getString("mimeType") ?: "audio/*",
            sizeBytes = extras?.getLong("sizeBytes") ?: 0L
        )
    }
}
