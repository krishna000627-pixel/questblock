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

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        runCatching {
            if (!prefs.optFocusNotif || prefs.focusUntilCache <= System.currentTimeMillis()) return
            if (sbn.packageName == packageName || !sbn.isClearable) return
            if (sbn.packageName in prefs.focusAllowed.split(",")) return
            if (AppCategorizer.categoryOf(prefs, packageManager, sbn.packageName) == "fun") cancelNotification(sbn.key)
        }
    }
}
