package com.ledge.launcher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Holds how many notifications each app has, so the app list can show badges. */
object Badges {
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
}

class NotifService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        try {
            Badges.counts = activeNotifications
                .filter { it.isClearable }
                .groupingBy { it.packageName }
                .eachCount()
        } catch (e: Exception) {
        }
    }
}
