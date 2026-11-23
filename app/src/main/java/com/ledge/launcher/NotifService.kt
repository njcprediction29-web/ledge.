package com.ledge.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Badges {
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
    var last by mutableStateOf<Map<String, String>>(emptyMap())
    var active by mutableStateOf<Set<String>>(emptySet())
}

class NotifService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        try {
            val all = activeNotifications
            Badges.active = all.map { it.packageName }.toSet()
            val list = all.filter { it.isClearable }
            Badges.counts = list.groupingBy { it.packageName }.eachCount()
            Badges.last = list.sortedByDescending { it.postTime }.distinctBy { it.packageName }.associate {
                val e = it.notification.extras
                it.packageName to listOfNotNull(
                    e.getCharSequence(Notification.EXTRA_TITLE), e.getCharSequence(Notification.EXTRA_TEXT)
                ).joinToString(": ")
            }
        } catch (e: Exception) {
        }
    }
}
