package com.krishna.questblock.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Quest reminders: one inexact alarm per quest that has a due time. */
object Reminders {
    const val CHANNEL_ID = "quest_reminders"
    const val EXTRA_ID = "task_id"
    const val EXTRA_TITLE = "task_title"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Quest reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun pending(ctx: Context, id: Long, title: String): PendingIntent {
        val i = Intent(ctx, QuestReminderReceiver::class.java)
            .putExtra(EXTRA_ID, id).putExtra(EXTRA_TITLE, title)
        return PendingIntent.getBroadcast(
            ctx, id.toInt(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(ctx: Context, id: Long, title: String, triggerAt: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending(ctx, id, title))
    }

    fun cancel(ctx: Context, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pending(ctx, id, ""))
    }
}

class QuestReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // Reminders switched off in Settings after this alarm was set
        if (!ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getBoolean("reminders", false)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val id = intent.getLongExtra(Reminders.EXTRA_ID, 0L)
        val title = intent.getStringExtra(Reminders.EXTRA_TITLE).orEmpty()
        Reminders.ensureChannel(ctx)
        val open = com.krishna.questblock.ui.openRoutePending(ctx, "home")
        val n = NotificationCompat.Builder(ctx, Reminders.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Quest time ⚔️")
            .setContentText(title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id.toInt(), n)
    }
}

/** Alarms are wiped on reboot; put them back. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: GameRepository
    @Inject lateinit var prefs: com.krishna.questblock.data.Prefs

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        KeepAlive.start(ctx)
        if (prefs.keepPerms) com.krishna.questblock.util.Perms.keepPermsOn(ctx)   // v301/v302
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { repo.rescheduleAllReminders() } finally { result.finish() }
        }
    }
}
