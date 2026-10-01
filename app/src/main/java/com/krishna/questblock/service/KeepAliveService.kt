package com.krishna.questblock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.krishna.questblock.data.BlockedApp
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.fmtLeft
import com.krishna.questblock.ui.MainActivity
import com.krishna.questblock.util.Perms
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Start / channel helpers for the keep-alive service. */
object KeepAlive {
    const val CH_KEEPALIVE = "qb_keepalive"
    const val CH_ALERTS = "qb_alerts"

    /** Safe to call from anywhere: Android refuses foreground-service starts from the background, so failures are swallowed. */
    fun start(ctx: Context) {
        val enabled = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getBoolean("keepalive", true)
        if (!enabled) return
        runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAliveService::class.java)) }
    }

    fun stop(ctx: Context) {
        runCatching { ctx.stopService(Intent(ctx, KeepAliveService::class.java)) }
    }

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_KEEPALIVE, "Blocking & unlock timer", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Small persistent notification that keeps QuestBlock alive and shows unlock time left"; setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Unlock & blocking alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "1-minute warning, expired unlock, and 'Blocking is off'" }
        )
    }
}

/**
 * Foreground service. Three jobs:
 *  1. Keep-alive: a small ongoing notification so the OS is much less eager to kill the app.
 *  2. Live tracker: that same notification shows "Instagram · 12:30 left", warns at 1 minute, then says Expired.
 *  3. Watchdog: if the accessibility service (the blocker) is switched off or killed, posts "Blocking is off, tap to fix".
 */
@AndroidEntryPoint
class KeepAliveService : Service() {

    @Inject lateinit var repo: GameRepository
    @Inject lateinit var prefs: Prefs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var apps: List<BlockedApp> = emptyList()
    private val warned = HashMap<String, Long>()    // pkg -> unlockedUntil we already warned about
    private val expired = HashMap<String, Long>()   // pkg -> unlockedUntil we already announced as expired
    private var started = false
    private var lastTitle = ""
    private var lastText = ""
    private var lastBeatWrite = 0L
    private var lastWatchdog = 0L
    private var offShown = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        KeepAlive.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            val n = ongoing("QuestBlock is on", "Blocking is active")
            lastTitle = "QuestBlock is on"; lastText = "Blocking is active"
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, ID_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID_ONGOING, n)
            }
            scope.launch { repo.blockedApps.collect { apps = it; render() } }
            scope.launch {
                while (true) {
                    render()
                    delay(if (hasActiveUnlock()) 1000L else 5000L)
                }
            }
        }
        return START_STICKY
    }

    private fun hasActiveUnlock(): Boolean {
        val now = System.currentTimeMillis()
        return apps.any { !it.isStudyApp && it.unlockedUntil > now }
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val nm = getSystemService(NotificationManager::class.java)

        if (now - lastBeatWrite > 5000L) { prefs.keepAliveBeat = now; lastBeatWrite = now }

        val active = apps.filter { !it.isStudyApp && it.unlockedUntil > now }.sortedBy { it.unlockedUntil }

        // 1-minute warning (once per unlock)
        active.forEach { a ->
            if (a.unlockedUntil - now <= 60_000L && warned[a.packageName] != a.unlockedUntil) {
                warned[a.packageName] = a.unlockedUntil
                nm.notify(ID_WARN_BASE + (a.packageName.hashCode() and 0xFFFF), alert(
                    "⏳ ${a.label}: 1 minute left", "It locks again when the time runs out."))
            }
        }
        // Expired state (once per unlock, shortly after it ends)
        apps.filter { !it.isStudyApp && it.unlockedUntil in 1..now && now - it.unlockedUntil < 120_000L }.forEach { a ->
            if (expired[a.packageName] != a.unlockedUntil) {
                expired[a.packageName] = a.unlockedUntil
                val id = ID_WARN_BASE + (a.packageName.hashCode() and 0xFFFF)
                nm.cancel(id)
                nm.notify(id, alert("⌛ ${a.label}: unlock expired", "Locked again. Open QuestBlock to earn more time."))
            }
        }

        // Persistent notification text
        val title: String
        val text: String
        if (active.isEmpty()) {
            title = "QuestBlock is on"; text = "Blocking is active"
        } else {
            val first = active.first()
            title = "${first.label} · ${fmtLeft(first.unlockedUntil - now)} left"
            text = if (active.size > 1) "+${active.size - 1} more unlocked" else "Unlocked"
        }
        val screenOn = getSystemService(PowerManager::class.java).isInteractive
        if ((title != lastTitle || text != lastText) && (screenOn || active.isEmpty())) {
            lastTitle = title; lastText = text
            nm.notify(ID_ONGOING, ongoing(title, text))
        }

        // Watchdog (every 20 s, only after setup)
        if (prefs.onboardingDone && now - lastWatchdog > 20_000L) {
            lastWatchdog = now
            val on = Perms.accessibilityEnabled(this)
            if (!on && !offShown) {
                offShown = true
                val fix = PendingIntent.getActivity(this, 1,
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                nm.notify(ID_WATCHDOG, NotificationCompat.Builder(this, KeepAlive.CH_ALERTS)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Blocking is off")
                    .setContentText("Tap to fix — turn QuestBlock back on in Accessibility.")
                    .setContentIntent(fix)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build())
            } else if (on && offShown) {
                offShown = false
                nm.cancel(ID_WATCHDOG)
            }
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun ongoing(title: String, text: String): Notification =
        NotificationCompat.Builder(this, KeepAlive.CH_KEEPALIVE)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun alert(title: String, text: String): Notification =
        NotificationCompat.Builder(this, KeepAlive.CH_ALERTS)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val ID_ONGOING = 4101
        const val ID_WATCHDOG = 4102
        const val ID_WARN_BASE = 4200
    }
}
