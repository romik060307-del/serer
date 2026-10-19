package com.rkdesign.glasslauncher

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

internal data class NoticeSnapshot(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val whenMillis: Long,
    val contentIntent: PendingIntent?
)

class LauncherNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        refreshSnapshots()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        upsert(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        snapshots.filter { it.key == sbn.key }.forEach { snapshots.remove(it) }
        latestTitle = snapshots.lastOrNull()?.title.orEmpty()
    }

    private fun refreshSnapshots() {
        try {
            snapshots.clear()
            activeNotifications?.forEach { upsert(it) }
        } catch (_: SecurityException) { /* Android may disconnect notification access at any time. */ }
    }

    private fun upsert(sbn: StatusBarNotification) {
        try {
            val n = sbn.notification ?: return
            val extras = n.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            if (title.isBlank() && text.isBlank()) return
            val item = NoticeSnapshot(sbn.key, sbn.packageName, title, text, sbn.postTime, n.contentIntent)
            snapshots.filter { it.key == item.key }.forEach { snapshots.remove(it) }
            snapshots.add(item)
            if (title.isNotBlank()) latestTitle = title
        } catch (_: Exception) { }
    }

    companion object {
        @Volatile var instance: LauncherNotificationListener? = null
            private set
        internal val snapshots = CopyOnWriteArrayList<NoticeSnapshot>()
        @Volatile internal var latestTitle: String = ""
    }
}
