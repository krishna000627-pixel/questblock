package com.krishna.questblock.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.krishna.questblock.data.AppCategorizer
import com.krishna.questblock.data.Prefs

/**
 * Having notification access lets QuestBlock ask Android which app is playing music.
 * Optional (v73): while a Focus session runs, notifications from Fun & Games apps are dismissed.
 */
class MediaListenerService : NotificationListenerService() {
    private val prefs by lazy { Prefs(applicationContext) }

    companion object {
        /** v214: packages that have a notification right now (for the dots on Quest Home). */
        @Volatile var activePkgs: Set<String> = emptySet()
    }

    private fun refreshActive() {
        activePkgs = runCatching { activeNotifications?.map { it.packageName }?.toSet() }.getOrNull() ?: emptySet()
    }

    override fun onListenerConnected() { refreshActive() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refreshActive() }

    private object Keep {
        val FOCUS_NOTIF_KEEP = setOf(
            "com.android.systemui", "android", "com.android.dialer", "com.google.android.dialer", "com.android.incallui",
            "com.android.phone", "com.android.server.telecom", "com.android.contacts",
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        refreshActive()
        runCatching {
            // v208: during Focus, notifications from apps you did not pick are dismissed (calls and system stay)
            if (prefs.focusUntilCache > System.currentTimeMillis() && sbn.packageName != packageName && sbn.isClearable &&
                sbn.packageName !in prefs.focusAllowed.split(",") && sbn.packageName !in Keep.FOCUS_NOTIF_KEEP) {
                cancelNotification(sbn.key)
                return
            }
            if (!prefs.optFocusNotif || prefs.focusUntilCache <= System.currentTimeMillis()) return
            if (sbn.packageName == packageName || !sbn.isClearable) return
            if (sbn.packageName in prefs.focusAllowed.split(",")) return
            if (AppCategorizer.categoryOf(prefs, packageManager, sbn.packageName) == "fun") cancelNotification(sbn.key)
        }
    }
}
