package com.toby.walkman

import android.service.notification.NotificationListenerService

/**
 * An empty notification listener. We don't read notifications — enabling this component in
 * Settings ▸ Notification access is simply what unlocks `MediaSessionManager.getActiveSessions`,
 * which lets the app mirror and control whatever is playing (e.g. the Spotify app).
 */
class MediaListener : NotificationListenerService()
