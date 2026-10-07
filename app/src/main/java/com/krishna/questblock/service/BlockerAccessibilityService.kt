package com.krishna.questblock.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
import com.krishna.questblock.data.ShortsStats
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
        if (pkg in BROWSERS) checkWebsite(pkg)
        if (pkg in YOUTUBE_PKGS || pkg == INSTAGRAM_PKG) checkShorts(pkg, event)
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
        if (kindOf(pkg) == null && shortsJob != null) stopShortTimer()
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


    // ── Short videos: YouTube Shorts ("yt") and Instagram Reels ("ig").
    // YouTube and Instagram themselves are never touched here. We count swipes and time, show a live counter over the app,
    // and (if picked in the Vault) ask for coins; after the fun limit they are locked for good.
    private var lastShortCheck = 0L
    private var lastSwipeAt = 0L
    private var shortsJob: Job? = null
    private var shortsKind: String? = null
    private var shortsBackAt = 0L
    private var shortsBackCount = 0
    private var lastClipSig = 0
    private var lastSigCountAt = 0L

    /** A fingerprint of the longer texts on screen (title, channel, caption). It changes when a new video is shown. */
    private fun clipSignature(root: AccessibilityNodeInfo): Int {
        val texts = ArrayList<String>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 14 || texts.size >= 40) return
            val s = (n.text ?: n.contentDescription)?.toString()
            if (s != null && s.length >= 6 && s.any { it.isLetter() }) texts += s
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return if (texts.isEmpty()) 0 else texts.joinToString("|").hashCode()
    }

    /** True when this view sits inside a comments (or engagement) panel, by walking up its parents. */
    private fun inCommentsPanel(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
        var n = node
        var depth = 0
        while (n != null && depth < 12) {
            val id = n.viewIdResourceName?.lowercase().orEmpty()
            if (id.contains("comment") || id.contains("engagement_panel")) return true
            n = n.parent
            depth++
        }
        return false
    }

    private fun kindOf(pkg: String): String? = when {
        pkg in YOUTUBE_PKGS -> ShortsStats.YT
        pkg == INSTAGRAM_PKG -> ShortsStats.IG
        else -> null
    }

    private fun checkShorts(pkg: String, event: AccessibilityEvent) {
        val kind = kindOf(pkg) ?: return
        val scrolled = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        val t = SystemClock.uptimeMillis()
        // Swipes inside the comments panel are scrolling the comments, not changing the video
        if (scrolled && inCommentsPanel(event.source)) {
            DebugLog.d("clips: comments scroll ignored ($kind)")
            return
        }
        // Once the count has started (the Shorts/Reels timer is running), every vertical swipe in the app is one video.
        // No screen check is needed for each swipe, so fast swipes are not missed.
        if (scrolled && shortsKind == kind && shortsJob?.isActive == true) {
            if (t - lastSwipeAt < 700L) return
            if (Build.VERSION.SDK_INT >= 28 && Math.abs(event.scrollDeltaX) > Math.abs(event.scrollDeltaY)) return
            lastSwipeAt = t
            ShortsStats.add(this, kind, 0L, 1)
            DebugLog.d("clips: swipe counted ($kind) while counting, class=${event.className}")
            showCounter(kind)
            return
        }
        if (scrolled) {
            // one swipe = one video (the scroll events of a single swipe come in a burst)
            if (t - lastSwipeAt < 700L) return
        } else {
            if (t - lastShortCheck < 600L) return
            lastShortCheck = t
        }
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val onClips = isShortForm(root, kind)
        logClips(kind, root, onClips, event)
        if (!onClips) {
            if (!scrolled && shortsKind == kind) stopShortTimer()
            return
        }
        // Backup count: some versions of the apps do not send a scroll event for every swipe. A new title/caption on screen
        // while the timer runs means a new video. Not used within 1.2 s of a swipe that was already counted.
        if (!scrolled && shortsKind == kind && shortsJob?.isActive == true) {
            val sig = clipSignature(root)
            val prev = lastClipSig
            if (sig != 0) lastClipSig = sig
            if (prev != 0 && sig != 0 && sig != prev && t - lastSwipeAt > 1200L) {
                lastSwipeAt = t
                ShortsStats.add(this, kind, 0L, 1)
                DebugLog.d("clips: swipe counted ($kind) by new video on screen")
                showCounter(kind)
            }
        }
        if (scrolled) {
            lastSwipeAt = t
            ShortsStats.add(this, kind, 0L, 1)
            DebugLog.d("clips: swipe counted ($kind) class=${event.className} from=${event.fromIndex} to=${event.toIndex}")
            showCounter(kind)
            return
        }
        scope.launch {
            val st = FunLimit.state(this@BlockerAccessibilityService, prefs)
            val locked = st.reached || prefs.funBlockedNow()
            if (prefs.shortPicked(kind)) {
                // picked in the Vault: needs coins to run, and no coins help once the fun limit is used up
                if (locked) { blockShort(kind); return@launch }
                // coin time is only used up while you are inside the clips screen: add back the time you were away
                val key = "short:$kind"
                val away = prefs.awayAt(key)
                if (away > 0L) {
                    prefs.clearAway(key)
                    val n = System.currentTimeMillis()
                    val until = prefs.siteUnlockedUntil(key)
                    if (until > away && dayNumber(away) == dayNumber(n)) prefs.setSiteUnlockedUntil(key, until + (n - away))
                }
                if (prefs.siteUnlockedUntil("short:$kind") <= System.currentTimeMillis()) {
                    DebugLog.d("service: $kind clips need coins, showing the lock")
                    showSiteBlock("short:$kind")
                    return@launch
                }
            } else if (locked && kind == ShortsStats.YT && prefs.shortsBlockOn) {
                blockShort(kind); return@launch
            }
            startShortTimer(kind)
        }
    }

    private fun isShortForm(root: AccessibilityNodeInfo, kind: String): Boolean {
        val pkg = root.packageName?.toString() ?: return false
        val fixed = if (kind == ShortsStats.YT) SHORTS_IDS else REELS_IDS
        for (id in fixed) {
            if (root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")?.isNotEmpty() == true) return true
        }
        // the app's own names can change between versions: look for any view whose id starts like the player's
        val parts = if (kind == ShortsStats.YT) SHORTS_ID_PARTS else REELS_ID_PARTS
        if (hasIdPart(root, parts, 0)) return true
        return hasSelectedTab(root, if (kind == ShortsStats.YT) "Shorts" else "Reels", 0)
    }

    private fun hasIdPart(n: AccessibilityNodeInfo?, parts: List<String>, depth: Int): Boolean {
        if (n == null || depth > 24) return false
        val id = n.viewIdResourceName
        if (id != null && parts.any { id.contains(":id/$it") }) return true
        for (i in 0 until n.childCount) if (hasIdPart(n.getChild(i), parts, depth + 1)) return true
        return false
    }

    /** Fallback: the bottom-bar tab with this name is selected. */
    private fun hasSelectedTab(n: AccessibilityNodeInfo?, name: String, depth: Int): Boolean {
        if (n == null || depth > 24) return false
        val label = (n.contentDescription ?: n.text)?.toString()
        if (label != null && (label.equals(name, ignoreCase = true) || label.startsWith("$name,", ignoreCase = true)) &&
            (n.isSelected || label.contains("selected", ignoreCase = true))) return true
        for (i in 0 until n.childCount) if (hasSelectedTab(n.getChild(i), name, depth + 1)) return true
        return false
    }

    /** For the debug log: a few view ids from the window that look like clips views. */
    private fun sampleIds(n: AccessibilityNodeInfo?, out: MutableSet<String>, depth: Int) {
        if (n == null || depth > 24 || out.size >= 8) return
        val id = n.viewIdResourceName
        if (id != null && (id.contains("reel") || id.contains("clips") || id.contains("short"))) out += id.substringAfter(":id/")
        for (i in 0 until n.childCount) sampleIds(n.getChild(i), out, depth + 1)
    }

    /** For the debug log: short texts / descriptions of a few action buttons, to learn what the screen looks like. */
    private fun sampleLabels(n: AccessibilityNodeInfo?, out: MutableSet<String>, depth: Int) {
        if (n == null || depth > 24 || out.size >= 10) return
        val l = (n.contentDescription ?: n.text)?.toString()
        if (l != null && l.length in 3..28 && Regex("(?i)short|reel|like|comment|share|remix|subscribe|home|library").containsMatchIn(l)) out += l
        for (i in 0 until n.childCount) sampleLabels(n.getChild(i), out, depth + 1)
    }

    private var lastClipsLog = 0L
    private fun logClips(kind: String, root: AccessibilityNodeInfo, found: Boolean, event: AccessibilityEvent?) {
        val t = SystemClock.uptimeMillis()
        if (t - lastClipsLog < 4_000L) return
        lastClipsLog = t
        val ids = LinkedHashSet<String>()
        sampleIds(root, ids, 0)
        val labels = LinkedHashSet<String>()
        sampleLabels(root, labels, 0)
        val ev = " labels=$labels" + if (event != null && event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED)
            " scroll[class=${event.className} from=${event.fromIndex} to=${event.toIndex} count=${event.itemCount}]" else ""
        DebugLog.d("clips: $kind screen=$found ids=$ids$ev")
    }

    private fun showCounter(kind: String) {
        if (!prefs.optCounter) return
        val d = ShortsStats.today(this, kind)
        val name = if (kind == ShortsStats.YT) "Shorts" else "Reels"
        CounterBubble.show(this, "\u25B6 $name ${d.n}")
    }

    /** While a short-video screen is on, add its time every 5 s; cut it when the limit is reached or the coin time ends. */
    private fun startShortTimer(kind: String) {
        if (shortsJob?.isActive == true && shortsKind == kind) { showCounter(kind); return }
        shortsJob?.cancel()
        shortsKind = kind
        FunSignal.shortsOn = true
        FunSignal.shortsKind = kind
        showCounter(kind)
        shortsJob = scope.launch {
            val pm = getSystemService(PowerManager::class.java)
            var ticks = 0
            try {
                while (true) {
                    delay(5_000L)
                    if (!pm.isInteractive) break
                    val root = rootInActiveWindow
                    val pkgOk = root?.packageName?.toString()?.let { kindOf(it) == kind } == true
                    if (!pkgOk || !isShortForm(root!!, kind)) break
                    ShortsStats.add(this@BlockerAccessibilityService, kind, 5_000L, 0)
                    if (kind == ShortsStats.YT) prefs.addShortsMs(5_000L)   // Shorts time counts as fun time
                    showCounter(kind)
                    ticks++
                    // coin time ran out
                    if (prefs.shortPicked(kind) && prefs.siteUnlockedUntil("short:$kind") <= System.currentTimeMillis()) {
                        showSiteBlock("short:$kind"); break
                    }
                    if (ticks % 3 == 0) {
                        val st = FunLimit.state(this@BlockerAccessibilityService, prefs, force = true)
                        val locked = st.reached || prefs.funBlockedNow()
                        if (locked && (prefs.shortPicked(kind) || (kind == ShortsStats.YT && prefs.shortsBlockOn))) { blockShort(kind); break }
                    }
                }
            } finally {
                FunSignal.shortsOn = false
                if (FunSignal.shortsKind == kind) FunSignal.shortsKind = null
                markClipsAway(kind)
                CounterBubble.hide(this@BlockerAccessibilityService)
                if (shortsKind == kind) shortsKind = null
            }
        }
    }

    /** Remember when you left, so coin time you did not use is given back when you return. */
    private fun markClipsAway(kind: String) {
        val key = "short:$kind"
        if (prefs.shortPicked(kind) && prefs.siteUnlockedUntil(key) > System.currentTimeMillis()) prefs.setAwayAt(key, System.currentTimeMillis())
    }

    private fun stopShortTimer() {
        lastClipSig = 0
        shortsKind?.let { markClipsAway(it) }
        FunSignal.shortsKind = null
        shortsJob?.cancel()
        shortsJob = null
        shortsKind = null
        FunSignal.shortsOn = false
        CounterBubble.hide(this)
    }

    private fun blockShort(kind: String) {
        val t = SystemClock.uptimeMillis()
        if (t - shortsBackAt < 1_200L) return
        shortsBackCount = if (t - shortsBackAt > 10_000L) 1 else shortsBackCount + 1
        shortsBackAt = t
        DebugLog.d("service: $kind clips blocked (fun limit / lock window), back x$shortsBackCount")
        Toast.makeText(this, (if (kind == ShortsStats.YT) "Shorts" else "Reels") + " is locked. Fun limit reached.", Toast.LENGTH_SHORT).show()
        // BACK leaves the clips screen and lands on the YouTube / Instagram page; only if the app keeps landing on clips (3rd time), go to the phone's home
        performGlobalAction(if (shortsBackCount > 2) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
        maybeBedtime()
    }

    // ── v73: Bedtime mode (Digital Wellbeing). No root: the service opens Quick Settings and taps the tile, like a finger would. ──
    private var bedtimeBusy = false

    private fun maybeBedtime() {
        if (!prefs.optBedtime || bedtimeBusy || prefs.doneToday("bedtime")) return
        prefs.markToday("bedtime")
        bedtimeBusy = true
        scope.launch {
            delay(1_500L)
            val r = runCatching { toggleBedtime() }.getOrElse { "failed (${it.javaClass.simpleName})" }
            DebugLog.d("service: Bedtime mode at fun limit: $r")
            bedtimeBusy = false
        }
    }

    /** Settings "Test now" button. [onResult] gets a one-line report. */
    fun runBedtimeTest(onResult: (String) -> Unit) {
        if (bedtimeBusy) { onResult("Already running"); return }
        bedtimeBusy = true
        scope.launch {
            val r = runCatching { toggleBedtime() }.getOrElse { "Failed (${it.javaClass.simpleName})" }
            DebugLog.d("service: Bedtime test: $r")
            bedtimeBusy = false
            onResult(r)
        }
    }

    private suspend fun toggleBedtime(): String {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        delay(1_200L)
        var tile: AccessibilityNodeInfo? = null
        for (i in 0 until 5) {
            tile = findBedtimeTile()
            if (tile != null) break
            delay(500L)
        }
        if (tile == null) {
            closeShade()
            return "Tile not found. Keep Bedtime on the first page of Quick Settings."
        }
        val state = tileState(tile)
        if (state == true) {
            closeShade()
            return "Bedtime mode is already on."
        }
        val tapped = clickUp(tile)
        delay(700L)
        closeShade()
        val was = when (state) { false -> "was off"; else -> "state unknown" }
        return if (tapped) "Tapped the Bedtime tile ($was)." else "Found the tile but the tap did not work."
    }

    private fun closeShade() { performGlobalAction(GLOBAL_ACTION_BACK) }

    private fun findBedtimeTile(): AccessibilityNodeInfo? {
        val roots = ArrayList<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots += it }
        runCatching { windows.mapNotNullTo(roots) { it.root } }
        for (r in roots) {
            val hits = r.findAccessibilityNodeInfosByText("Bedtime") ?: continue
            for (h in hits) {
                var cur: AccessibilityNodeInfo? = h
                var hops = 0
                while (cur != null && hops < 6) {
                    if (cur.isClickable) return cur
                    cur = cur.parent; hops++
                }
            }
        }
        return null
    }

    /** true = on, false = off, null = cannot tell. */
    private fun tileState(n: AccessibilityNodeInfo): Boolean? {
        if (n.isCheckable) return n.isChecked
        val sd = if (Build.VERSION.SDK_INT >= 30) n.stateDescription?.toString()?.lowercase().orEmpty() else ""
        if (sd.isNotEmpty()) {
            if (Regex("\\boff\\b").containsMatchIn(sd)) return false
            if (Regex("\\bon\\b").containsMatchIn(sd)) return true
        }
        return null
    }

    private fun clickUp(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n
        var hops = 0
        while (cur != null && hops < 6) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent; hops++
        }
        return false
    }

    // ── Website blocker (reads the browser address bar; works only in browsers that expose it) ──
    private var lastSiteCheck = 0L
    private var lastSiteHit = 0L

    private fun checkWebsite(pkg: String) {
        val t = SystemClock.uptimeMillis()
        if (t - lastSiteCheck < 600L) return
        lastSiteCheck = t
        if (prefs.blockedSites().isEmpty()) return
        val root = rootInActiveWindow ?: return
        val url = findUrl(root, pkg) ?: return
        val domain = prefs.matchedSite(url) ?: return
        BlockActivity.browserPkg = pkg
        if (t - lastSiteHit < 1500L) return
        lastSiteHit = t
        scope.launch {
            val key = "site:$domain"
            val st = FunLimit.state(this@BlockerAccessibilityService, prefs)
            if (st.reached || prefs.funBlockedNow()) {
                // like a fun app: no coin unlock once the limit is used up or fun is locked
                FunSignal.quiet = !st.reached
                DebugLog.d("service: site $domain locked (fun limit / lock window)")
                showFunLimit(key)
                return@launch
            }
            if (prefs.siteUnlockedUntil(domain) > System.currentTimeMillis()) return@launch
            DebugLog.d("service: site $domain blocked, showing coin lock")
            showSiteBlock(key)
        }
    }

    private fun showSiteBlock(key: String) {
        val t = SystemClock.uptimeMillis()
        if (BlockActivity.showingFor == key) return
        if (lastBlockPkg == key && t - lastBlockAt < RELAUNCH_GUARD_MS) return
        lastBlockPkg = key
        lastBlockAt = t
        startActivity(
            Intent(this, BlockActivity::class.java)
                .putExtra(BlockActivity.EXTRA_PKG, key)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
        )
    }

    private fun findUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        for (id in (URL_BAR_IDS[pkg].orEmpty() + "$pkg:id/url_bar")) {
            val n = root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull() ?: continue
            n.text?.toString()?.takeIf { it.contains('.') && !it.contains(' ') }?.let { return it }
        }
        return scanForUrl(root, 0)
    }

    private fun scanForUrl(n: AccessibilityNodeInfo?, depth: Int): String? {
        if (n == null || depth > 12) return null
        if (n.className?.toString()?.contains("EditText") == true) {
            val t = n.text?.toString()
            if (!t.isNullOrBlank() && !t.contains(' ') && t.contains('.')) return t
        }
        for (i in 0 until n.childCount) scanForUrl(n.getChild(i), depth + 1)?.let { return it }
        return null
    }

    private fun showFunLimit(pkg: String) {
        val now = SystemClock.uptimeMillis()
        if (BlockActivity.showingFor == pkg) return
        if (lastBlockPkg == pkg && now - lastBlockAt < RELAUNCH_GUARD_MS) return
        lastBlockPkg = pkg
        lastBlockAt = now
        com.krishna.questblock.util.DebugLog.d("service.showFunLimit pkg=$pkg")
        if (!FunSignal.quiet && FunLimit.last?.reached == true) maybeBedtime()
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

        private val BROWSERS = setOf(
            "com.android.chrome", "com.chrome.beta", "org.chromium.chrome", "org.mozilla.firefox", "org.mozilla.firefox_beta",
            "org.mozilla.fenix", "com.brave.browser", "com.microsoft.emmx", "com.sec.android.app.sbrowser", "com.opera.browser",
            "com.opera.mini.native", "com.duckduckgo.mobile.android", "com.kiwibrowser.browser", "com.vivaldi.browser",
            "com.heytap.browser", "com.coloros.browser", "com.google.android.apps.chrome",
        )
        private val URL_BAR_IDS: Map<String, List<String>> = mapOf(
            "org.mozilla.firefox" to listOf("org.mozilla.firefox:id/mozac_browser_toolbar_url_view", "org.mozilla.firefox:id/url_bar_title"),
            "org.mozilla.fenix" to listOf("org.mozilla.fenix:id/mozac_browser_toolbar_url_view"),
            "com.sec.android.app.sbrowser" to listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text"),
            "com.opera.browser" to listOf("com.opera.browser:id/url_field"),
            "com.duckduckgo.mobile.android" to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"),
        )
        private const val INSTAGRAM_PKG = "com.instagram.android"
        private val REELS_IDS = listOf("clips_viewer_view_pager", "clips_viewer_container", "clips_viewer_fragment")
        private val SHORTS_ID_PARTS = listOf("reel_player", "reel_recycler", "reel_watch", "reel_time_bar", "reel_progress")
        private val REELS_ID_PARTS = listOf("clips_viewer", "clips_video", "clips_author", "clips_ufi")
        private val YOUTUBE_PKGS = setOf("com.google.android.youtube", "app.revanced.android.youtube", "com.vanced.android.youtube")
        /** Views YouTube uses for the Shorts player (the selected "Shorts" tab is the fallback). */
        private val SHORTS_IDS = listOf("reel_recycler", "reel_player_page_container", "shorts_container")
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
