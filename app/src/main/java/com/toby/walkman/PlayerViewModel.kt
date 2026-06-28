package com.toby.walkman

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val art: Bitmap?
)

/**
 * Mirrors and controls the active media session. Prefers Spotify, falls back to whatever
 * is playing. Requires the user to grant this app Notification access (see [MediaListener]).
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    var hasAccess by mutableStateOf(false); private set
    var now by mutableStateOf<NowPlaying?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var positionMs by mutableStateOf(0L); private set

    private val sessionManager =
        app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val component = ComponentName(app, MediaListener::class.java)

    private var controller: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = readMetadata(metadata)
        override fun onPlaybackStateChanged(state: PlaybackState?) = readState(state)
        override fun onSessionDestroyed() { attachBest(emptyList()) }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> attachBest(list ?: emptyList()) }

    init {
        // Advance the position locally between callbacks so the reels/scrubber stay smooth.
        viewModelScope.launch {
            while (true) {
                controller?.playbackState?.let { s ->
                    positionMs = if (s.state == PlaybackState.STATE_PLAYING) {
                        s.position + ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong()
                    } else s.position
                }
                delay(250)
            }
        }
    }

    /** Call from Activity.onResume — picks up access granted while we were away. */
    fun refreshConnection() {
        try {
            val sessions = sessionManager.getActiveSessions(component)
            hasAccess = true
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component)
            attachBest(sessions)
        } catch (e: SecurityException) {
            hasAccess = false
        }
    }

    private fun attachBest(list: List<MediaController>) {
        controller?.unregisterCallback(controllerCallback)
        val best = list.firstOrNull { it.packageName == "com.spotify.music" } ?: list.firstOrNull()
        controller = best
        if (best != null) {
            best.registerCallback(controllerCallback)
            readMetadata(best.metadata)
            readState(best.playbackState)
        } else {
            now = null
            isPlaying = false
        }
    }

    private fun readMetadata(m: MediaMetadata?) {
        if (m == null) return
        now = NowPlaying(
            title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "—",
            artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            album = m.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION),
            art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
        )
    }

    private fun readState(s: PlaybackState?) {
        isPlaying = s?.state == PlaybackState.STATE_PLAYING
        if (s != null) positionMs = s.position
    }

    // Transport — routed to the controlled app.
    fun playPause() {
        val c = controller?.transportControls ?: return
        if (isPlaying) c.pause() else c.play()
    }
    fun next() = controller?.transportControls?.skipToNext() ?: Unit
    fun previous() = controller?.transportControls?.skipToPrevious() ?: Unit
    fun stop() = controller?.transportControls?.stop() ?: Unit
    fun seekTo(ms: Long) = controller?.transportControls?.seekTo(ms) ?: Unit

    override fun onCleared() {
        controller?.unregisterCallback(controllerCallback)
        sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
    }
}
