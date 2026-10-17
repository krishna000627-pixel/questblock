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
import com.krishna.questblock.data.AppDatabase
import com.krishna.questblock.data.FunLimit
import com.krishna.questblock.data.FunSignal
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.StudySignal
import com.krishna.questblock.data.StudyTracker
import com.krishna.questblock.data.dayNumber
import com.krishna.questblock.data.fmtLeft
import com.krishna.questblock.data.frozenAt
import com.krishna.questblock.data.leftMs
import com.krishna.questblock.ui.MainActivity
import com.krishna.questblock.util.Backup
import com.krishna.questblock.util.Health
import com.krishna.questblock.ui.fmtDur
import com.krishna.questblock.util.Perms
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Start / channel helpers for the keep-alive service. */
object KeepAlive {
    const val CH_KEEPALIVE = "qb_keepalive"
    const val CH_IDLE = "qb_idle"   // quiet, collapsed: used when nothing is unlocked
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
            NotificationChannel(CH_IDLE, "Blocking running (quiet)", NotificationManager.IMPORTANCE_MIN)
                .apply { description = "Hidden-away notification while nothing is unlocked"; setShowBadge(false) }
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
    @Inject lateinit var db: AppDatabase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var apps: List<BlockedApp> = emptyList()
    private var started = false
    private var lastTitle = ""
    private var lastText = ""
    private var lastBeatWrite = 0L
    private var lastWatchdog = 0L
    private var offShown = false
    private var healthKey = ""
    private var study: StudyTracker.Status? = null
    private var studyAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        KeepAlive.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            val n = ongoing("QuestBlock is on", "Blocking is active")
            lastTitle = "QuestBlock is on|Blocking is active|-1"; lastText = ""
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, ID_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID_ONGOING, n)
            }
            scope.launch { repo.blockedApps.collect { apps = it; render() } }
            // v73: remember when Focus ends, so Do Not Disturb, music and notification rules know a Focus is running
            scope.launch { repo.player.collect { prefs.focusUntilCache = it.focusModeUntil } }
            // v208: moving the phone clock must not shorten or lengthen a Focus session. The wall clock is compared with the uptime clock;
            // a jump moves the session end by the same amount. (A jump made while the app is not running cannot be seen.)
            scope.launch {
                var lastWall = System.currentTimeMillis()
                var lastUp = android.os.SystemClock.elapsedRealtime()
                while (true) {
                    delay(5_000L)
                    val wall = System.currentTimeMillis()
                    val up = android.os.SystemClock.elapsedRealtime()
                    val drift = (wall - lastWall) - (up - lastUp)
                    val before = lastWall
                    lastWall = wall
                    lastUp = up
                    if (kotlin.math.abs(drift) > 20_000L && prefs.focusUntilCache > before) {
                        runCatching { repo.shiftFocusEnd(drift) }
                    }
                }
            }
            // v205: turning accessibility off during a strict Focus session breaks it (give-up penalty, no reward)
            scope.launch {
                var off = 0
                while (true) {
                    delay(5_000L)
                    val strict = prefs.focusStrict && prefs.focusUntilCache > System.currentTimeMillis()
                    off = if (strict && !Perms.accessibilityEnabled(applicationContext)) off + 1 else 0
                    if (off >= 2) {
                        off = 0
                        val pen = runCatching { repo.abandonFocus(force = true) }.getOrDefault(0)
                        val fix = PendingIntent.getActivity(this@KeepAliveService, 2,
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                        getSystemService(NotificationManager::class.java)?.notify(9205, NotificationCompat.Builder(this@KeepAliveService, KeepAlive.CH_ALERTS)
                            .setSmallIcon(android.R.drawable.ic_dialog_alert)
                            .setContentTitle("Strict focus broken")
                            .setContentText("Accessibility was turned off" + (if (pen > 0) ", \u2212$pen coins" else "") + ". Tap to turn it back on.")
                            .setContentIntent(fix)
                            .setAutoCancel(true)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .build())
                    }
                }
            }
            // Study coins come from Android's usage record; sweep every 20 s (also catches up after the service was killed)
            scope.launch {
                while (true) {
                    val st = runCatching { StudyTracker.sweep(applicationContext, repo, prefs) }.getOrNull()
                    // Backup 2.0: one automatic copy a day into Downloads/QuestBlock (keeps the newest 3)
                    if (prefs.onboardingDone && prefs.lastAutoBackupDay != dayNumber(System.currentTimeMillis())) {
                        prefs.lastAutoBackupDay = dayNumber(System.currentTimeMillis())
                        runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Backup.autoBackup(applicationContext, db) } }
                    }
                    study = st; studyAt = System.currentTimeMillis()
                    render()
                    // quicker while a study app is open; enter/leave from the accessibility service wakes it at once
                    withTimeoutOrNull(if (StudySignal.pkg != null) 10_000L else 20_000L) { StudySignal.pokes.receive() }
                }
            }
            scope.launch {
                while (true) {
                    render()
                    runCatching { OptionalTick.run(applicationContext, repo, prefs) }
                    delay(if (hasActiveUnlock() || StudySignal.pkg != null || FunSignal.pkg != null || FunSignal.shortsOn || FunSignal.ytFunOn) 1000L else 5000L)
                }
            }
        }
        return START_STICKY
    }

    /** Websites with coin time, plus Shorts/Reels only while that screen is open (away time is saved, not shown). */
    private fun liveKeys(): List<String> = prefs.unlockKeys().filter { !it.startsWith("short:") || FunSignal.shortsKind == it.removePrefix("short:") }

    private fun hasActiveUnlock(): Boolean {
        val now = System.currentTimeMillis()
        return apps.any { !it.isStudyApp && it.unlockedUntil > now && !it.frozenAt(prefs.awayAt(it.packageName), now) } ||
            liveKeys().any { prefs.siteUnlockedUntil(it) > now }
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val nm = getSystemService(NotificationManager::class.java)

        if (now - lastBeatWrite > 5000L) { prefs.keepAliveBeat = now; lastBeatWrite = now }

        // v73: optional time-left bubble over a fun app (or Shorts)
        val fl = FunLimit.last
        if (prefs.optBubble && fl != null && fl.enabled && (FunSignal.pkg != null || FunSignal.shortsOn || FunSignal.ytFunOn)) {
            val left = fl.limitMs - (fl.usedMs + (now - fl.at).coerceAtLeast(0L))
            FunBubble.show(this, if (left > 0L) "🎮 " + fmtDur(left) + " left" else "🎮 limit reached")
        } else FunBubble.hide(this)

        // an app counts as unlocked while time is running, or while it is paused (user away) with time saved
        val active = apps.filter { !it.isStudyApp && (it.unlockedUntil > now || it.frozenAt(prefs.awayAt(it.packageName), now)) }
            .sortedWith(compareBy({ it.frozenAt(prefs.awayAt(it.packageName), now) }, { it.leftMs(prefs.awayAt(it.packageName), now) }))

        // websites unlocked with coins show up here too, like apps
        val sites = liveKeys().filter { prefs.siteUnlockedUntil(it) > now }.sortedBy { prefs.siteUnlockedUntil(it) }
        val siteExpired = liveKeys().filter { val u = prefs.siteUnlockedUntil(it); u in 1..now && now - u < 120_000L }
            .maxByOrNull { prefs.siteUnlockedUntil(it) }
        // ONE notification does everything: live countdown, 5-min block tracker, 1-min warning, expired state.
        val justExpired = apps.filter { !it.isStudyApp && it.unlockedUntil in 1..now && now - it.unlockedUntil < 120_000L &&
                !it.frozenAt(prefs.awayAt(it.packageName), now) }
            .maxByOrNull { it.unlockedUntil }
        // Old separate alert notifications are gone; clear any left over from earlier versions.
        apps.forEach { nm.cancel(ID_WARN_BASE + (it.packageName.hashCode() and 0xFFFF)) }

        val title: String
        val text: String
        var whenMs = 0L
        var progress = -1
        var visible = false
        var pinWhen = false
        var route = "vault"   // page the notification opens
        // Priority: a study app on screen -> study line. Otherwise (unlocked app open, or nothing) -> unlock line.
        val sigPkg = StudySignal.pkg
        if (sigPkg != null) {
            // live progress: stored carry + time since the last check (or since the app opened, if later)
            val unitMs = prefs.studyMinPerCoin.coerceAtLeast(1) * 60_000L
            val live = prefs.studyCarryMs(sigPkg) + (now - maxOf(prefs.studyCheckpoint, StudySignal.since)).coerceAtLeast(0L)
            val carry = live % unitMs
            val toGo = (unitMs - carry).coerceAtLeast(1_000L)
            val unlockHint = active.firstOrNull()?.let { a ->
                val aw = prefs.awayAt(a.packageName)
                " · ${a.label} ${(a.leftMs(aw, now) + 59_999L) / 60_000L} min ${if (a.frozenAt(aw, now)) "saved" else "left"}"
            } ?: ""
            route = "usage"
            title = "📚 ${StudySignal.label} · studying"
            text = "1 \uD83E\uDE99 every ${prefs.studyMinPerCoin} min$unlockHint"
            whenMs = now + toGo
            progress = ((carry * 100) / unitMs).toInt().coerceIn(0, 100)
        } else if (com.krishna.questblock.data.CodingSignal.pkg != null) {
            route = "usage"
            title = "💻 ${com.krishna.questblock.data.CodingSignal.label} · coding"
            text = com.krishna.questblock.data.CodingSignal.line
            // coding apps are Vault apps too: while one is open and has unlock time, count that time down
            apps.firstOrNull { it.packageName == com.krishna.questblock.data.CodingSignal.pkg && it.unlockedUntil > now }
                ?.let { whenMs = it.unlockedUntil; pinWhen = true }
        } else if ((FunSignal.pkg != null || FunSignal.shortsOn || FunSignal.ytFunOn) && FunLimit.last?.enabled == true) {
            // a fun app is on screen and a daily limit is set: show how much is left
            route = "usage"
            val fs = FunLimit.last!!
            val usedNow = fs.usedMs + (now - fs.at).coerceAtLeast(0L)
            val leftFun = fs.limitMs - usedNow
            val pct = (usedNow * 100 / fs.limitMs).toInt().coerceIn(0, 100)
            val funHint = active.firstOrNull()?.let { a ->
                val aw = prefs.awayAt(a.packageName)
                " · ${a.label} ${(a.leftMs(aw, now) + 59_999L) / 60_000L} min ${if (a.frozenAt(aw, now)) "saved" else "left"}"
            } ?: ""
            if (leftFun > 0L) {
                title = "${if (pct >= 80) "⏳ " else ""}🎮 Fun ${fmtDur(usedNow)} of ${fmtDur(fs.limitMs)}"
                text = "Fun time left today$funHint"
                whenMs = now + leftFun
                progress = pct
            } else {
                title = "🎮 Fun limit reached"
                text = "This session can finish. It locks the next time you open it."
                visible = true
            }
        } else if (active.isNotEmpty()) {
            route = "unlock_timers"
            val first = active.first()
            val away = prefs.awayAt(first.packageName)
            val paused = first.frozenAt(away, now)
            val left = first.leftMs(away, now)
            val blockMs = 5 * 60_000L
            val blocksLeft = ((left + blockMs - 1) / blockMs).toInt().coerceAtLeast(1)
            val inBlock = left - (blocksLeft - 1) * blockMs            // time left in the current 5-min block
            val more = active.size - 1 + sites.size
            val extra = if (more > 0) " · +$more more unlocked" else ""
            if (paused) {
                title = "⏸ ${first.label} · ${fmtLeft(left)} saved"
                text = "Paused while you're away$extra"
                visible = true
            } else {
                val warn = if (left <= 60_000L) "⏳ " else ""
                // The ONLY clock is the system countdown in the header (it ticks by itself, exact to the second).
                // A second clock in the text always disagreed with it by up to a second.
                title = "$warn${first.label} · unlocked"
                text = "$blocksLeft × 5 min block${if (blocksLeft > 1) "s" else ""} left$extra"
                whenMs = first.unlockedUntil
                progress = (((blockMs - inBlock) * 100) / blockMs).toInt().coerceIn(0, 100)
            }
        } else if (sites.isNotEmpty()) {
            route = "unlock_timers"
            val site = sites.first()
            val until = prefs.siteUnlockedUntil(site)
            val left = until - now
            val blockMs = 5 * 60_000L
            val blocksLeft = ((left + blockMs - 1) / blockMs).toInt().coerceAtLeast(1)
            val inBlock = left - (blocksLeft - 1) * blockMs
            val extra = if (sites.size > 1) " · +${sites.size - 1} more unlocked" else ""
            title = "${if (left <= 60_000L) "⏳ " else ""}${com.krishna.questblock.data.pseudoLabel(site) ?: site} · unlocked"
            text = "$blocksLeft × 5 min block${if (blocksLeft > 1) "s" else ""} left$extra"
            whenMs = until
            progress = (((blockMs - inBlock) * 100) / blockMs).toInt().coerceIn(0, 100)
        } else if (justExpired != null) {
            route = "quests"
            title = "⌛ ${justExpired.label} locked again"; text = "Unlock ran out. Open QuestBlock to earn more time."
        } else if (siteExpired != null) {
            route = "quests"
            title = "⌛ ${com.krishna.questblock.data.pseudoLabel(siteExpired) ?: siteExpired} locked again"; text = "Unlock ran out. Open QuestBlock to earn more time."
        } else {
            val strictLine = if (!prefs.strictMode) "🛡 Strict off" else if (prefs.strictPausedUntil > now) "🛡 Strict paused" else "🛡 Strict on"
            title = "QuestBlock is on"; text = "Blocking is active · $strictLine"
        }
        val screenOn = getSystemService(PowerManager::class.java).isInteractive
        val key = "$title|$text|${progress / 2}" + if (pinWhen) "|$whenMs" else ""
        if (key != lastTitle && (screenOn || active.isEmpty())) {
            lastTitle = key; lastText = ""
            if (title.startsWith("\u231B")) com.krishna.questblock.util.Sounds.alarm()   // "locked again": the unlock ran out
            nm.notify(ID_ONGOING, ongoing(title, text, whenMs, progress, visible, route))
        }

        // Watchdog (every 20 s, only after setup)
        if (prefs.onboardingDone && now - lastWatchdog > 20_000L) {
            lastWatchdog = now
            // Anti-bypass: Accessibility, and (while fun rules are on) Usage access and battery settings
            val probs = Health.problems(this, prefs)
            val key = probs.joinToString("|") { it.id }
            if (probs.isNotEmpty() && key != healthKey) {
                healthKey = key
                val top = probs.first()
                val fix = PendingIntent.getActivity(this, 1,
                    Intent(top.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                nm.notify(ID_WATCHDOG, NotificationCompat.Builder(this, KeepAlive.CH_ALERTS)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(top.title)
                    .setContentText("Tap to fix. " + top.text + if (probs.size > 1) " (+${probs.size - 1} more)" else "")
                    .setContentIntent(fix)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build())
            } else if (probs.isEmpty() && healthKey.isNotEmpty()) {
                healthKey = ""
                nm.cancel(ID_WATCHDOG)
            }
        }
    }

    private fun openApp(route: String): PendingIntent = com.krishna.questblock.ui.openRoutePending(this, route)

    private fun ongoing(title: String, text: String, countdownTo: Long = 0L, progress: Int = -1, visible: Boolean = countdownTo > 0L, route: String = "vault"): Notification =
        NotificationCompat.Builder(this, if (visible || countdownTo > 0L) KeepAlive.CH_KEEPALIVE else KeepAlive.CH_IDLE)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(route))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(if (visible || countdownTo > 0L) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .apply {
                if (countdownTo > 0L) {   // system-drawn countdown keeps ticking even if our updates are paused
                    setWhen(countdownTo); setShowWhen(true); setUsesChronometer(true); setChronometerCountDown(true)
                }
                if (progress >= 0) setProgress(100, progress, false)
            }
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
