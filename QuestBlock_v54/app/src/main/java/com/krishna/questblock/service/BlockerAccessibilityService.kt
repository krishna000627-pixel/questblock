package com.krishna.questblock.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.krishna.questblock.data.BlockedApp
import com.krishna.questblock.data.dayNumber
import com.krishna.questblock.data.enforcedAt
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.StudySession
import com.krishna.questblock.data.AppCategorizer
import com.krishna.questblock.data.FunLimit
import com.krishna.questblock.data.FunSignal
import com.krishna.questblock.data.StudySignal
import com.krishna.questblock.util.DebugLog
import com.krishna.questblock.data.UsageTracker
import com.krishna.questblock.ui.BlockActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class BlockerAccessibilityService : AccessibilityService() {

    @Inject lateinit var repo: GameRepository
    @Inject lateinit var prefs: Prefs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var expiryJob: Job? = null

    // ── Fun-time limit ──
    private var funCutJob: Job? = null
    private var funCutPkg: String? = null
    /** Safe app (e.g. Free Fire) we are inside right now: never cut mid-use. */
    private var safeSession: String? = null
    /** Safe apps we let in while still under the limit. */
    private val safeOk = HashSet<String>()
    /** When the user last left (or locked the phone away from) a safe app. */
    private val safeLeftAt = HashMap<String, Long>()
    private var studyTimerJob: Job? = null
    private var usageJob: Job? = null
    private var usagePkg: String? = null
    private var currentPkg: String? = null
    private var lastGuard = 0L
    private var lastBlockPkg: String? = null
    private var lastBlockAt = 0L
    private var lastBeat = 0L
    private val freeLogAt = HashMap<String, Long>()

    // Study-session tracking
    private var activeStudyPkg: String? = null
    private var activeStudySessionId: Long = -1L
    private var studyStartTime: Long = 0L
    private var studyMinutesCredited: Int = 0

    // v17: tracking which unlocked app is currently in foreground
    private var trackedUnlockPkg: String? = null

    // Unlock carry-over: remember when the phone sleeps / wakes so time spent away is not charged
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    trackedUnlockPkg?.let { prefs.setAwayAt(it, System.currentTimeMillis()) }
                    StudySignal.leave()
                    FunSignal.leave()
                    safeSession?.let { safeLeftAt[it] = System.currentTimeMillis() }
                    safeSession = null
                }
                Intent.ACTION_USER_PRESENT ->
                    currentPkg?.let { p -> scope.launch { evaluateApp(p) } }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        runCatching {
            registerReceiver(screenReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            })
        }
        prefs.lastActiveAt = System.currentTimeMillis()
        KeepAlive.start(this)
        // Recover any sessions from a previous run that ended unexpectedly
        scope.launch { repo.recoverUnlockSessions() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val t = SystemClock.elapsedRealtime()
        if (t - lastBeat > 30_000L) { lastBeat = t; prefs.lastActiveAt = System.currentTimeMillis() }
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val isWindowChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        if (pkg in GUARDED_PKGS) {
            if (isWindowChange) {
                stopStudyTimer()
                recordLeaveIfNeeded(pkg)
                markForeground(pkg)
            }
            guardSettings()
            return
        }
        if (!isWindowChange || pkg in IGNORED_PKGS || pkg == imePackage()) return

        recordLeaveIfNeeded(pkg)
        markForeground(pkg)
        scope.launch { evaluateApp(pkg) }
    }

    /**
     * If we're moving away from an app we were tracking as "user inside unlocked app",
     * flush the time to the DB.
     */
    private fun recordLeaveIfNeeded(newPkg: String) {
        val tracked = trackedUnlockPkg
        if (tracked != null && tracked != newPkg) {
            trackedUnlockPkg = null
            prefs.setAwayAt(tracked, System.currentTimeMillis())   // carry-over: time away is not charged
            scope.launch { repo.onLeaveUnlockedApp(tracked) }
        }
    }

    private fun markForeground(pkg: String) {
        if (currentPkg != pkg) {
            funCutJob?.cancel(); funCutPkg = null
            safeSession?.let { if (it != pkg) { safeLeftAt[it] = System.currentTimeMillis(); safeSession = null } }
            stopUsageTimer()
            if (activeStudyPkg != null && pkg != activeStudyPkg) {
                stopStudyTimer()
            }
        }
        currentPkg = pkg
        expiryJob?.cancel()
    }

    /**
     * Carry-over: if the user left this app while it still had unlock time, push the deadline forward by the
     * time they were away, so only minutes actually spent inside the app are used up.
     */
    private suspend fun applyCarryOver(app: BlockedApp): BlockedApp {
        val away = prefs.awayAt(app.packageName)
        if (away <= 0L) return app
        prefs.clearAway(app.packageName)
        val n = System.currentTimeMillis()
        if (app.isStudyApp || app.unlockedUntil <= away || dayNumber(away) != dayNumber(n)) return app
        val extended = app.unlockedUntil + (n - away)
        repo.setUnlockedUntil(app.packageName, extended)
        DebugLog.d("carry-over: ${app.packageName} was away ${(n - away) / 1000}s, left=${(app.unlockedUntil - away) / 1000}s kept")
        return app.copy(unlockedUntil = extended)
    }

    /**
     * Daily Fun & Games limit. Returns true when the app was locked.
     * - normal fun apps are cut the moment the limit is reached (a timer is set while they are open)
     * - "safe" apps (Free Fire) are never cut mid-use; they are locked the next time they are opened,
     *   with a 5 minute window to come back to a game already under way (notification glance, phone lock)
     */
    private suspend fun checkFunLimit(pkg: String): Boolean {
        // Quiet hours / study hours / exam mode: fun apps are locked. A game already under way is not cut (same rule as the daily limit).
        if (prefs.funBlockedNow() && AppCategorizer.categoryOf(prefs, packageManager, pkg) == "fun") {
            val safeNow = prefs.isFunSafe(pkg)
            if (safeNow && safeSession == pkg) { FunSignal.enter(pkg); return false }
            safeOk -= pkg
            safeSession = null
            FunSignal.leave()
            FunSignal.quiet = true
            showFunLimit(pkg)
            return true
        }
        FunSignal.quiet = false
        if (prefs.effectiveFunLimitMin <= 0 || AppCategorizer.categoryOf(prefs, packageManager, pkg) != "fun") {
            FunSignal.leave()
            return false
        }
        val safe = prefs.isFunSafe(pkg)
        if (safe && safeSession == pkg) { FunSignal.enter(pkg); return false }       // inside the same session
        val st = FunLimit.state(this, prefs)
        if (!st.enabled) { FunSignal.leave(); return false }                        // no limit / no usage access
        if (!st.reached) {
            if (safe) { safeSession = pkg; safeOk += pkg } else scheduleFunCut(pkg)
            FunSignal.enter(pkg)
            return false
        }
        val left = safeLeftAt[pkg] ?: 0L
        if (safe && pkg in safeOk && System.currentTimeMillis() - left < SAFE_REJOIN_MS) {
            safeSession = pkg
            FunSignal.enter(pkg)
            return false
        }
        safeOk -= pkg
        safeSession = null
        FunSignal.leave()
        showFunLimit(pkg)
        return true
    }

    private fun scheduleFunCut(pkg: String) {
        if (funCutPkg == pkg && funCutJob?.isActive == true) return
        funCutJob?.cancel()
        funCutPkg = pkg
        funCutJob = scope.launch {
            while (true) {
                val st = FunLimit.state(this@BlockerAccessibilityService, prefs, force = true)
                if (!st.enabled) return@launch
                val left = st.limitMs - st.usedMs
                if (left <= 0L) break
                // look again at least once a minute: time with the screen off does not count
                delay(minOf(left, 60_000L).coerceAtLeast(1_000L))
                if (currentPkg != pkg) return@launch
            }
            if (currentPkg == pkg) {
                com.krishna.questblock.util.DebugLog.d("service: fun limit reached, cutting $pkg")
                FunSignal.leave()
                showFunLimit(pkg)
            }
        }
    }

    private fun showFunLimit(pkg: String) {
        val now = SystemClock.uptimeMillis()
        if (BlockActivity.showingFor == pkg) return
        if (lastBlockPkg == pkg && now - lastBlockAt < RELAUNCH_GUARD_MS) return
        lastBlockPkg = pkg
        lastBlockAt = now
        com.krishna.questblock.util.DebugLog.d("service.showFunLimit pkg=$pkg")
        startActivity(
            Intent(this, BlockActivity::class.java)
                .putExtra(BlockActivity.EXTRA_PKG, pkg)
                .putExtra(BlockActivity.EXTRA_FUN_LIMIT, true)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
        )
    }

    private suspend fun evaluateApp(pkg: String) {
        if (checkFunLimit(pkg)) return
        val app = repo.getBlockedApp(pkg)?.let { applyCarryOver(it) }

        if (app != null && !app.isStudyApp) {
            stopStudyTimer()
            StudySignal.leave()
            val cal = Calendar.getInstance()
            val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

            if (!app.enforcedAt(minuteOfDay)) {
                stopUsageTimer()
                logFree(pkg, app.label, "Free · outside lock hours")
                return
            }

            if (app.dailyLimitMin > 0) {
                val used = if (app.usedDay == dayNumber(System.currentTimeMillis())) app.usedTodayMs else 0L
                if (used < app.dailyLimitMin * 60_000L) {
                    com.krishna.questblock.util.DebugLog.d("service: $pkg FREE via daily limit (${used / 60_000L}/${app.dailyLimitMin} min)")
                    logFree(pkg, "${app.label} · ${used / 60_000L}/${app.dailyLimitMin} min used", "Free · daily limit")
                    startUsageTimer(pkg)
                    return
                }
            }
            stopUsageTimer()

            val now = System.currentTimeMillis()
            if (app.unlockedUntil > now) {
                com.krishna.questblock.util.DebugLog.d("service: $pkg is unlocked, left=${app.unlockedUntil - now}ms")
                if (repo.focusUnlockedPkg(pkg)) logFree(pkg, app.label, "Free · Focus Quest")
                // v17: user is now inside a paid-unlock app — track entry time
                if (trackedUnlockPkg != pkg) {
                    trackedUnlockPkg = pkg
                    repo.onEnterUnlockedApp(pkg)
                }
                expiryJob?.cancel()
                expiryJob = scope.launch {
                    delay(app.unlockedUntil - now + 250)
                    if (currentPkg == pkg) {
                        // Unlock expired while user was inside — flush usage and re-evaluate
                        trackedUnlockPkg = null
                        repo.onLeaveUnlockedApp(pkg)
                        evaluateApp(pkg)
                    }
                }
            } else {
                delay(SETTLE_MS)
                val fresh = repo.getBlockedApp(pkg)
                if (fresh != null && fresh.unlockedUntil > System.currentTimeMillis()) return
                if (currentPkg == pkg) showBlock(app)
            }
            return
        }

        stopUsageTimer()
        if (app != null && app.isStudyApp) {
            logFree(pkg, app.label, "Free · study app")
            startStudyTimer(pkg, app.label)
            if (UsageTracker.hasAccess(this)) StudySignal.enter(pkg, app.label) else StudySignal.leave()
        } else {
            stopStudyTimer()
            StudySignal.leave()
        }
    }

    // ── Daily-limit usage timer ──────────────────────────────────────────
    private fun startUsageTimer(pkg: String) {
        if (usagePkg == pkg && usageJob?.isActive == true) return
        stopUsageTimer()
        usagePkg = pkg
        usageJob = scope.launch {
            val pm = getSystemService(PowerManager::class.java)
            while (true) {
                delay(USAGE_TICK_MS)
                if (currentPkg != pkg) break
                if (!pm.isInteractive) continue
                if (repo.addUsage(pkg, USAGE_TICK_MS)) {
                    usagePkg = null
                    scope.launch { evaluateApp(pkg) }
                    break
                }
            }
        }
    }

    private fun logFree(pkg: String, detail: String, reason: String) {
        val key = "$pkg|$reason"
        val now = SystemClock.elapsedRealtime()
        val last = freeLogAt[key]
        if (last != null && now - last < FREE_LOG_GAP_MS) return
        freeLogAt[key] = now
        scope.launch { repo.logFreeAccess(reason, detail) }
    }

    private fun stopUsageTimer() {
        usageJob?.cancel()
        usageJob = null
        usagePkg = null
    }

    // ── Study timer ──────────────────────────────────────────────────────
    private fun startStudyTimer(pkg: String, label: String) {
        // With Usage access, coins come from Android's usage record (StudyTracker), not from this timer.
        if (UsageTracker.hasAccess(this)) return
        if (activeStudyPkg == pkg && studyTimerJob?.isActive == true) return
        stopStudyTimer()
        activeStudyPkg = pkg
        studyStartTime = System.currentTimeMillis()
        studyMinutesCredited = 0

        scope.launch {
            val sessionId = repo.startStudySession(pkg, label)
            activeStudySessionId = sessionId
            var minutesTicked = 0
            while (true) {
                delay(60_000L)
                if (currentPkg != pkg) break
                minutesTicked++
                val perCoin = prefs.studyMinPerCoin
                if (minutesTicked % perCoin == 0) {
                    repo.creditStudyCoins(sessionId, perCoin, label)
                    studyMinutesCredited += perCoin
                }
            }
        }.also { studyTimerJob = it }
    }

    private fun stopStudyTimer() {
        studyTimerJob?.cancel()
        studyTimerJob = null
        activeStudyPkg = null
        activeStudySessionId = -1L
        studyMinutesCredited = 0
    }

    private fun showBlock(app: BlockedApp) {
        val now = SystemClock.uptimeMillis()
        if (BlockActivity.showingFor == app.packageName) return
        if (lastBlockPkg == app.packageName && now - lastBlockAt < RELAUNCH_GUARD_MS) return
        lastBlockPkg = app.packageName
        lastBlockAt = now

        com.krishna.questblock.util.DebugLog.d("service.showBlock pkg=${app.packageName} unlockLeft=${app.unlockedUntil - System.currentTimeMillis()}ms")
        val intent = Intent(this, BlockActivity::class.java)
            .putExtra(BlockActivity.EXTRA_PKG, app.packageName)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        startActivity(intent)
    }

    private fun imePackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    private fun guardSettings() {
        if (!prefs.strictMode) return
        if (System.currentTimeMillis() < prefs.strictPausedUntil) return
        val now = SystemClock.uptimeMillis()
        if (now - lastGuard < 300) return
        lastGuard = now
        val root = rootInActiveWindow ?: return
        val text = StringBuilder()
        collectText(root, text, 0)
        if (text.contains("questblock", ignoreCase = true)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, "Strict mode is on", Toast.LENGTH_SHORT).show()
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int) {
        if (node == null || depth > 12) return
        node.text?.let { out.append(it).append(' ') }
        node.contentDescription?.let { out.append(it).append(' ') }
        for (i in 0 until node.childCount) collectText(node.getChild(i), out, depth + 1)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        StudySignal.leave()
        if (instance === this) instance = null
        // Flush unlock tracking before dying
        val tracked = trackedUnlockPkg
        if (tracked != null) {
            trackedUnlockPkg = null
            // Write enteredAt to prefs (already done on enter) so it survives
            // The next service start will call recoverUnlockSessions()
        }
        stopStudyTimer()
        stopUsageTimer()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: BlockerAccessibilityService? = null

        fun openRecents(): Boolean = instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false

        private const val SETTLE_MS = 180L
        private const val RELAUNCH_GUARD_MS = 800L
        private const val SAFE_REJOIN_MS = 5 * 60_000L
        private const val USAGE_TICK_MS = 15_000L
        private const val FREE_LOG_GAP_MS = 10 * 60_000L
        private val GUARDED_PKGS = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.permissioncontroller",
        )
        private val IGNORED_PKGS = setOf(
            "com.android.systemui",
            "android",
        )
    }
}
