package com.toby.walkman

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared state for the "lock away apps" focus session. Persists BOTH the settings (what to lock,
 * and whether a session ends after a span of time or a number of songs) AND the live session, so
 * the foreground [LockService] keeps working even if the OS restarts its process. The service and
 * the UI both read/write this; prefs are the source of truth.
 */
object LockController {
    enum class Mode { TIME, SONGS }

    /** What gets locked away. */
    enum class Scope { EVERYTHING, SELECTED }

    /** Never locked, whatever the scope. (Home screen, system UI and the dialer are also
     *  spared, but that's decided in the service since it depends on the device.) */
    val alwaysAllowed = setOf("com.spotify.music", "com.toby.walkman")

    // Settings.
    var mode by mutableStateOf(Mode.TIME)
    var minutes by mutableStateOf(30)
    var songs by mutableStateOf(10)
    var scope by mutableStateOf(Scope.EVERYTHING)
    /** The apps to lock, used when [scope] is SELECTED. */
    var locked by mutableStateOf<Set<String>>(emptySet())

    // Live session state (persisted so it survives a process restart within the same boot).
    var active by mutableStateOf(false); private set
    private var endElapsed by mutableStateOf(0L)   // SystemClock.elapsedRealtime() deadline
    private var targetSongs by mutableStateOf(0)
    var songsPlayed by mutableStateOf(0); private set

    /** Should this app be locked away during a session? (Home/system exemptions are added on
     *  top of this inside the service.) */
    fun isLocked(pkg: String): Boolean {
        if (pkg in alwaysAllowed) return false
        return if (scope == Scope.EVERYTHING) true else pkg in locked
    }

    /** Begin a session from the current settings and persist everything. */
    fun start(ctx: Context) {
        songsPlayed = 0
        if (mode == Mode.TIME) endElapsed = SystemClock.elapsedRealtime() + minutes * 60_000L
        else targetSongs = songs
        active = true
        save(ctx)
    }

    fun stop(ctx: Context) {
        active = false
        save(ctx)
    }

    fun onSongAdvanced(ctx: Context) {
        if (active && mode == Mode.SONGS) {
            songsPlayed++
            save(ctx)
        }
    }

    fun timeRemainingMs(): Long = (endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    fun songsRemaining(): Int = (targetSongs - songsPlayed).coerceAtLeast(0)

    /** True once the session's end condition is met (or it isn't running). */
    fun finished(): Boolean = when {
        !active -> true
        mode == Mode.TIME -> timeRemainingMs() <= 0
        else -> songsRemaining() <= 0
    }

    fun remainingLabel(): String = if (mode == Mode.TIME) {
        val s = timeRemainingMs() / 1000
        "%d:%02d left".format(s / 60, s % 60)
    } else {
        "${songsRemaining()} songs left"
    }

    // ---- persistence (settings + live session) ----
    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("lock", Context.MODE_PRIVATE)
        mode = if (p.getString("mode", "TIME") == "SONGS") Mode.SONGS else Mode.TIME
        minutes = p.getInt("minutes", 30)
        songs = p.getInt("songs", 10)
        scope = if (p.getString("scope", "EVERYTHING") == "SELECTED") Scope.SELECTED else Scope.EVERYTHING
        locked = p.getStringSet("locked", emptySet())?.toSet() ?: emptySet()
        active = p.getBoolean("active", false)
        endElapsed = p.getLong("endElapsed", 0L)
        targetSongs = p.getInt("targetSongs", 0)
        songsPlayed = p.getInt("songsPlayed", 0)
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences("lock", Context.MODE_PRIVATE).edit()
            .putString("mode", mode.name)
            .putInt("minutes", minutes)
            .putInt("songs", songs)
            .putString("scope", scope.name)
            .putStringSet("locked", locked)
            .putBoolean("active", active)
            .putLong("endElapsed", endElapsed)
            .putInt("targetSongs", targetSongs)
            .putInt("songsPlayed", songsPlayed)
            .apply()
    }
}
