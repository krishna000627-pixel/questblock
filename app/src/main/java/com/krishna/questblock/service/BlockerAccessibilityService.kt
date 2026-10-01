package com.krishna.questblock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs.lastActiveAt = System.currentTimeMillis()
        KeepAlive.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        // Heartbeat for the watchdog / Settings "last active" (throttled)
        val t = SystemClock.elapsedRealtime()
        if (t - lastBeat > 30_000L) { lastBeat = t; prefs.lastActiveAt = System.currentTimeMillis() }
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val isWindowChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        if (pkg in GUARDED_PKGS) {
            if (isWindowChange) {
                stopStudyTimer()
                markForeground(pkg)
            }
            guardSettings()
            return
        }
        if (!isWindowChange || pkg in IGNORED_PKGS || pkg == imePackage()) return

        markForeground(pkg)
        scope.launch { evaluateApp(pkg) }
    }

    private fun markForeground(pkg: String) {
        if (currentPkg != pkg) {
            stopUsageTimer()
            // App switched — if leaving a study app, stop timer
            if (activeStudyPkg != null && pkg != activeStudyPkg) {
                stopStudyTimer()
            }
        }
        currentPkg = pkg
        expiryJob?.cancel()
    }

    private suspend fun evaluateApp(pkg: String) {
        val app = repo.getBlockedApp(pkg)

        // Blocked (non-study) app
        if (app != null && !app.isStudyApp) {
            stopStudyTimer()
            val cal = Calendar.getInstance()
            val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

            // Outside its lock schedule (e.g. only locked 09:00–17:00) -> free to use
            if (!app.enforcedAt(minuteOfDay)) {
                stopUsageTimer()
                logFree(pkg, app.label, "Free · outside lock hours")
                return
            }

            // Daily-limit mode: free until today's allowance is used up
            if (app.dailyLimitMin > 0) {
                val used = if (app.usedDay == dayNumber(System.currentTimeMillis())) app.usedTodayMs else 0L
                if (used < app.dailyLimitMin * 60_000L) {
                    logFree(pkg, "${app.label} · ${used / 60_000L}/${app.dailyLimitMin} min used", "Free · daily limit")
                    startUsageTimer(pkg)
                    return
                }
            }
            stopUsageTimer()

            val now = System.currentTimeMillis()
            if (app.unlockedUntil > now) {
                if (repo.focusUnlockedPkg(pkg)) logFree(pkg, app.label, "Free · Focus Quest")
                expiryJob?.cancel()
                expiryJob = scope.launch {
                    delay(app.unlockedUntil - now + 250)
                    if (currentPkg == pkg) evaluateApp(pkg)
                }
            } else {
                // Let the app's own launch animation / splash windows settle, then make sure it is
                // still the foreground app. Launching the lock screen while the target is still
                // mid-transition is what left a ghost, tap-eating window over the launcher icons.
                delay(SETTLE_MS)
                if (currentPkg == pkg) showBlock(app)
            }
            return
        }

        stopUsageTimer()
        // Study app -> earn coins while inside it
        if (app != null && app.isStudyApp) {
            logFree(pkg, app.label, "Free · study app")
            startStudyTimer(pkg, app.label)
        } else stopStudyTimer()
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
                if (!pm.isInteractive) continue          // screen off: don't count
                if (repo.addUsage(pkg, USAGE_TICK_MS)) {  // limit reached -> lock now
                    usagePkg = null
                    // separate coroutine: evaluateApp stops this very timer job
                    scope.launch { evaluateApp(pkg) }
                    break
                }
            }
        }
    }

    /** Records "this open cost 0 coins, and here's why" in the coin log (at most once per app+reason per 10 min). */
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

    // ── Study timer (coins per N minutes — see Settings) ────────────────
    private fun startStudyTimer(pkg: String, label: String) {
        if (activeStudyPkg == pkg && studyTimerJob?.isActive == true) return // already running
        stopStudyTimer()
        activeStudyPkg = pkg
        studyStartTime = System.currentTimeMillis()
        studyMinutesCredited = 0

        scope.launch {
            val sessionId = repo.startStudySession(pkg, label)
            activeStudySessionId = sessionId
            // Tick every minute; award 1 coin every 5 min
            var minutesTicked = 0
            while (true) {
                delay(60_000L) // 1 minute
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
        // Already on screen for this app, or we just launched it: don't stack another one.
        if (BlockActivity.showingFor == app.packageName) return
        if (lastBlockPkg == app.packageName && now - lastBlockAt < RELAUNCH_GUARD_MS) return
        lastBlockPkg = app.packageName
        lastBlockAt = now

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
        if (instance === this) instance = null
        stopStudyTimer()
        stopUsageTimer()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: BlockerAccessibilityService? = null

        /** Opens the system Recents screen (used by the Reliability page). False if the blocker isn't running. */
        fun openRecents(): Boolean = instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false

        private const val SETTLE_MS = 180L
        private const val RELAUNCH_GUARD_MS = 800L
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
