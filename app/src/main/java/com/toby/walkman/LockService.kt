package com.toby.walkman

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Foreground service that enforces a focus session: it watches the foreground app and, when a
 * locked-away app comes forward, slaps a full-screen overlay over it. In "songs" mode it counts
 * track changes off the active media session. Stops itself when the session's end condition is met.
 */
class LockService : Service() {

    private val TAG = "LockService"
    private var tickerStarted = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usm: UsageStatsManager
    private lateinit var wm: WindowManager
    private lateinit var sessions: MediaSessionManager
    private val component by lazy { ComponentName(this, MediaListener::class.java) }

    private var overlay: View? = null
    private var overlayText: TextView? = null

    private var lastTrack: String? = null
    private var seededTrack = false

    // Latest known foreground app (most-recent UsageStats event wins; persists if none).
    private var currentPkg: String? = null
    private var lastLogged: String? = null

    companion object { const val ACTION_START = "com.toby.walkman.action.START" }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sessions = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        // Read the persisted truth so we honour the real settings (and survive a process restart),
        // instead of trusting whatever the in-memory singleton happens to hold.
        LockController.load(this)
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!tickerStarted) { tickerStarted = true; handler.post(ticker) }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (LockController.finished()) {
                LockController.stop(this@LockService)
                removeOverlay()
                stopSelf()
                return
            }
            countSongs()
            refreshForeground()
            val fg = currentPkg
            val block = fg != null && shouldBlock(fg)
            if (fg != lastLogged) {
                Log.d(TAG, "foreground=$fg block=$block (home=$homeApps)")
                lastLogged = fg
            }
            if (block) showOverlay() else removeOverlay()
            handler.postDelayed(this, 500)
        }
    }

    // ---- which app is in front (incremental: remember the latest resume) ----

    private fun refreshForeground() {
        // Scan a rolling window and take the most recent foreground event. This tolerates
        // UsageStats delivery lag (a late event still wins once it lands) far better than
        // consuming events incrementally. When the window has none (sat in one app a long
        // time), we keep the last known package.
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 60_000, now)
        var pkg: String? = null
        var stamp = 0L
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            // MOVE_TO_FOREGROUND (== ACTIVITY_RESUMED, value 1): the app became frontmost.
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && e.timeStamp >= stamp) {
                stamp = e.timeStamp
                pkg = e.packageName
            }
        }
        if (pkg != null) currentPkg = pkg
    }

    /** Packages we never lock: every home screen + the default home, the default dialer (so
     *  calls still work), and the system UI. Spares the user from getting trapped. */
    private val homeApps: Set<String> by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val set = packageManager.queryIntentActivities(home, 0)
            .map { it.activityInfo.packageName }.toMutableSet()
        packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.let { set.add(it) }
        set.add("com.android.systemui")
        set.add("android")
        (getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)
            ?.defaultDialerPackage?.let { set.add(it) }
        set
    }

    private fun shouldBlock(pkg: String): Boolean {
        if (pkg == packageName) return false
        if (pkg in homeApps) return false        // home / dialer / system UI — never trapped
        return LockController.isLocked(pkg)
    }

    // ---- song counting (songs mode) ----

    private fun countSongs() {
        if (!LockController.active || LockController.mode != LockController.Mode.SONGS) return
        val title = try {
            val list = sessions.getActiveSessions(component)
            val c = list.firstOrNull { it.packageName == "com.spotify.music" } ?: list.firstOrNull()
            c?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        } catch (e: SecurityException) {
            null
        }
        if (title.isNullOrEmpty()) return
        if (!seededTrack) { lastTrack = title; seededTrack = true; return }
        if (title != lastTrack) {
            lastTrack = title
            LockController.onSongAdvanced(this)
        }
    }

    // ---- blocking overlay ----

    private fun showOverlay() {
        if (overlay != null) { overlayText?.text = LockController.remainingLabel(); return }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F20A0A0A"))
            setPadding(70, 70, 70, 70)
        }
        root.addView(TextView(this).apply {
            text = "🔒"; textSize = 64f; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Locked away"; setTextColor(Color.WHITE); textSize = 26f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(0, 24, 0, 6)
        })
        overlayText = TextView(this).apply {
            text = LockController.remainingLabel()
            setTextColor(Color.parseColor("#1DB954")); textSize = 18f; gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }
        root.addView(overlayText)
        root.addView(Button(this).apply {
            text = "Back to music"
            setOnClickListener {
                packageManager.getLaunchIntentForPackage("com.spotify.music")?.let {
                    startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Open Walkman"
            setOnClickListener {
                startActivity(Intent(this@LockService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        // NOT_FOCUSABLE so the overlay doesn't swallow the Home/Back keys — the user must be
        // able to leave to the home screen (which lifts the lock). It still covers touches to
        // the app underneath, and its own buttons still work.
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(root, lp)
            overlay = root
        } catch (e: Exception) {
            overlay = null; overlayText = null
        }
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        overlayText = null
    }

    // ---- foreground notification ----

    private fun startInForeground() {
        val channelId = "focus_lock"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Focus lock", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Walkman focus lock")
            .setContentText("Apps are locked away")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(open)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notif)
        }
    }
}
