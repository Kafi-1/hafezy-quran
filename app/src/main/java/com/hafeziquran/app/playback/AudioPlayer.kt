package com.hafeziquran.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A snapshot of what the player is doing, for the UI to render. */
data class PlaybackState(
    val connected: Boolean = false,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    /** Surah number currently playing, or null when the queue is empty. */
    val currentSurah: Int? = null,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

/**
 * The app's handle on the background player.
 *
 * Connects to [AudioPlayerService] through a [MediaController] and republishes the
 * player's events as a [StateFlow] the Compose UI can collect. If the connection
 * is not ready yet, commands are no-ops rather than crashes — the controller
 * arrives within a frame or two of the first screen that needs it.
 */
@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    private val future: ListenableFuture<MediaController> = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, AudioPlayerService::class.java)),
    ).buildAsync()

    init {
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = connected
                connected.addListener(listener)
                publish()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** Replaces the queue with [items] and starts playing at [startIndex]. */
    fun playQueue(items: List<MediaItem>, startIndex: Int) {
        val c = controller ?: return
        if (items.isEmpty()) return
        c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        val c = controller ?: return
        if (c.hasNextMediaItem()) c.seekToNextMediaItem()
    }

    fun previous() {
        val c = controller ?: return
        if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem() else c.seekTo(0)
    }

    fun stop() {
        controller?.stop()
    }

    private fun publish() {
        val c = controller ?: return
        _state.value = PlaybackState(
            connected = true,
            playing = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            currentSurah = c.currentMediaItem?.mediaId?.toIntOrNull(),
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
        )
    }
}
