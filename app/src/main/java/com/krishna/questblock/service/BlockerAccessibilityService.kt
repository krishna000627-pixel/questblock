package com.krishna.questblock.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
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
import com.krishna.questblock.data.YtGate
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
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
        // Leaving YouTube/Instagram for any other screen (also QuestBlock's own lock screen) removes the Shorts/Reels counter
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && kindOf(pkg) == null &&
            pkg !in IGNORED_PKGS && pkg != imePackage()) {
            if (shortsJob != null) stopShortTimer() else CounterBubble.hide(this)
        }
        if (pkg == packageName) return
        if (pkg in BROWSERS) checkWebsite(pkg)
        if (pkg in YOUTUBE_PKGS || pkg == INSTAGRAM_PKG) checkShorts(pkg, event)
        if (pkg in YOUTUBE_PKGS) checkYtGate(pkg, event)
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
        if (pkg !in YOUTUBE_PKGS) { if (ytLatchKey.isNotEmpty()) DebugLog.d("yt: verdict cleared, left YouTube for $pkg"); ytLatchKey = ""; ytPendingKey = ""; ytLatchFun = false; ytExpiryJob?.cancel(); ytSettleJob?.cancel(); ytGraceJob?.cancel(); ytTapTitle = "" }   // coming back judges the video again
        if (kindOf(pkg) == null) { if (shortsJob != null) stopShortTimer() else CounterBubble.hide(this) }
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
            if (shortNeedsCoins(kind)) {
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
            }
            startShortTimer(kind)
        }
    }

    /** Vault pick, or (YouTube) the study gate: Shorts are always fun, a chapter name in the title never frees them. */
    private fun shortNeedsCoins(kind: String): Boolean = prefs.shortPicked(kind) || (kind == ShortsStats.YT && prefs.ytGateOn)

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
                    if (shortNeedsCoins(kind) && prefs.siteUnlockedUntil("short:$kind") <= System.currentTimeMillis()) {
                        showSiteBlock("short:$kind"); break
                    }
                    if (ticks % 3 == 0) {
                        val st = FunLimit.state(this@BlockerAccessibilityService, prefs, force = true)
                        val locked = st.reached || prefs.funBlockedNow()
                        if (locked && shortNeedsCoins(kind)) { blockShort(kind); break }
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
        if (shortNeedsCoins(kind) && prefs.siteUnlockedUntil(key) > System.currentTimeMillis()) prefs.setAwayAt(key, System.currentTimeMillis())
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

    // ── v157: YouTube study gate. Judges the PLAYING video once, from its title. ──
    // chapter studied -> free · chapter not studied -> study first · no chapter -> coins (2 a minute).
    // Home, search and Shorts are not judged here (Shorts always pay, see shortNeedsCoins).
    private var ytScrollAt = 0L
    private var ytClickAt = 0L
    private var ytLastRead = 0L
    private var ytLatchKey = ""        // the video already judged: it is not judged again until the video changes
    private var ytLatchFun = false
    private var ytPendingKey = ""      // an unprompted new title waits here for a second identical read
    private var ytSettleJob: Job? = null
    private var ytExpiryJob: Job? = null
    private var ytNoTitleLogAt = 0L
    private var ytNoTitleCount = 0
    private var ytUnjudgedSince = 0L
    private var ytAdSince = 0L
    private var ytLastTitle = ""
    private var ytLastHint = ""
    private var ytNotWatchLogAt = 0L
    private var ytTapTitle = ""        // v165: the title of the card you tapped (home, search or recommendations)
    private var ytTapAt = 0L
    private var ytGraceJob: Job? = null
    private var ytSrcLog = ""

    private class YtNode(val label: String, val top: Int, val bottom: Int)
    /** What one read of the YouTube screen found. [byId] now means "the title was placed from the Subscribe row" (this YouTube build has no view ids). */
    private class YtRead(
        val onWatch: Boolean, val title: String?, val hint: String, val byId: Boolean, val playerFound: Boolean = false,
        val ad: Boolean = false, val home: Boolean = false, val short: Boolean = false, val sample: String = "", val anchorMissing: Boolean = false,
        val preview: Boolean = false,
    )

    private fun checkYtGate(pkg: String, e: AccessibilityEvent) {
        if (!prefs.ytGateOn) return
        val t = SystemClock.uptimeMillis()
        when (e.eventType) {
            // 2: scrolling (recommendations, comments, description) never judges anything, and the read after it is not trusted either
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                ytScrollAt = t
                // video not judged yet and the user already scrolls: read the title NOW, while it is still on screen
                if (ytLatchKey.isEmpty() && t - ytLastRead >= 400L) { ytLastRead = t; evalYtGate(pkg) }
                return
            }
            // a tap may start another video: for a few seconds a new title is accepted at once
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                ytClickAt = t
                // v165: remember which video card was tapped; its title is the verdict source (no ad or scroll guessing)
                val card = tappedTitle(e.source)
                if (card != null) { ytTapTitle = card; ytTapAt = t; DebugLog.d("yt: tapped '${card.take(60)}'") }
                else DebugLog.d("yt: click, no card title (source=${e.source != null})")
                scheduleYtRead(pkg, 1_000L); return
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> { scheduleYtRead(pkg, 1_000L) }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {}
            else -> return
        }
        // coin time bought for a non-study video: look again the moment it runs out
        if (ytLatchFun && ytExpiryJob?.isActive != true) {
            val left = prefs.siteUnlockedUntil("yt:fun") - System.currentTimeMillis()
            if (left > 0L) armYtExpiry(left)
        }
        if (ytLatchKey.isNotEmpty() && t - ytScrollAt < YT_SCROLL_QUIET_MS) return    // a judged video is left alone while scrolling
        if (t - ytLastRead < 800L) return          // 4: a burst of events is one read
        ytLastRead = t
        evalYtGate(pkg)
    }

    private fun scheduleYtRead(pkg: String, ms: Long) {
        ytSettleJob?.cancel()
        ytSettleJob = scope.launch { delay(ms); evalYtGate(pkg) }
    }

    private fun armYtExpiry(ms: Long) {
        ytExpiryJob?.cancel()
        ytExpiryJob = scope.launch {
            delay(ms + 300L)
            if (ytLatchFun && currentPkg?.let { it in YOUTUBE_PKGS } == true) judgeYt(ytLastTitle, ytLastHint)   // coin time over: lock again, in full screen too
        }
    }

    private fun evalYtGate(pkg: String) {
        if (!prefs.ytGateOn) return
        val now = SystemClock.uptimeMillis()
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        if (isShortForm(root, ShortsStats.YT)) { ytLatchKey = ""; ytPendingKey = ""; return }    // 7: Shorts never use the title
        val r = readYtWatch(root)
        if (r.short) { ytLatchKey = ""; ytPendingKey = ""; return }
        // v161: the playing video's title comes from YouTube's media session. It does not move when you scroll, and recommendations,
        // comments and ad cards can never be mistaken for it. The screen is only the fallback.
        val sess = ytSession()
        val playing = sess?.playing == true
        val tapFresh = ytTapTitle.isNotEmpty() && now - ytTapAt < YT_TAP_TITLE_MS   // v165: a video you just tapped is judged by the title you tapped
        // YouTube home with nothing playing: free, and the next video is judged fresh. A video playing in the small player (or in
        // full screen, where YouTube shows no buttons) is still a video: it is judged from the media session.
        if (r.home && !playing && !r.preview) { ytLatchKey = ""; ytPendingKey = ""; ytLatchFun = false; ytNoTitleCount = 0; ytUnjudgedSince = 0L; return }
        if (sess == null && ytLatchKey.isNotEmpty() && now - ytScrollAt < YT_SCROLL_QUIET_MS) { scheduleYtRead(pkg, YT_SCROLL_QUIET_MS); return }   // screen reads wait for the scroll to stop; the session does not need to
        if (!r.onWatch && !playing && !r.preview) {      // search, channel, a page still loading: free, the verdict is kept
            if (now - ytNotWatchLogAt > 10_000L) { ytNotWatchLogAt = now; DebugLog.d("yt: not a watch page, session='${sess?.title.orEmpty().take(60)}' playing=$playing labels=[${r.sample}]") }
            return
        }
        // v168: no ad waiting. A tapped video is judged by the card you tapped, any other by the media session title.
        val sTitle = sess?.title.orEmpty()
        // v167: every source of the title in the Debug log (media session, screen, tapped card), written when any of them changes
        val srcLog = "session='${sTitle.take(70)}' playing=$playing screen='${r.title.orEmpty().take(70)}' tapped='${if (tapFresh) ytTapTitle.take(70) else ""}'"
        if (srcLog != ytSrcLog) { ytSrcLog = srcLog; DebugLog.d("yt: src $srcLog") }
        val title = if (tapFresh) ytTapTitle else sTitle.ifEmpty { r.title }
        val hint = if (tapFresh) "" else if (sTitle.isNotEmpty()) sess?.artist.orEmpty() else r.hint
        val strong = tapFresh || sTitle.isNotEmpty() || r.byId
        if (title == null) {
            // Video page but no title read. Scrolled away after a verdict: keep it. Never judged: after 3 reads it is treated as fun (locked).
            if (now - ytNoTitleLogAt > 5_000L) { ytNoTitleLogAt = now; DebugLog.d("yt: watch page, no title read, labels=[${r.sample}] verdict='${ytLatchKey.take(30)}'") }
            if ((r.playerFound || playing) && ytLatchKey.isEmpty()) {
                if (ytUnjudgedSince == 0L) ytUnjudgedSince = now
                ytNoTitleCount++
                // title row scrolled away: wait for it to come back (12 s); title row on screen but unreadable: 3 tries
                val give = if (r.anchorMissing) now - ytUnjudgedSince >= 12_000L else ytNoTitleCount >= 3
                if (give) { ytNoTitleCount = 0; ytUnjudgedSince = 0L; ytLatchKey = "?unreadable"; judgeYt("", "", YT_GRACE_MS) } else scheduleYtRead(pkg, 1_500L)
            }
            return
        }
        ytNoTitleCount = 0
        ytUnjudgedSince = 0L
        val key = YtGate.key(title)
        if (key.isEmpty() || key == ytLatchKey) { ytPendingKey = ""; return }                     // 4: same video, already judged
        val tapped = now - ytClickAt < 6_000L
        if (ytLatchKey.isNotEmpty() && !tapped) {
            // another title with no tap: autoplay, or a recommendation. Needs the title placed from the Subscribe row and two reads.
            if (!strong) return
            if (ytPendingKey != key) { ytPendingKey = key; scheduleYtRead(pkg, 900L); return }
        }
        ytPendingKey = ""
        ytLatchKey = key
        DebugLog.d("yt: title from ${if (tapFresh) "tapped card" else if (sTitle.isNotEmpty()) "media session" else "screen"}")
        judgeYt(title, hint, if (tapFresh || now - ytClickAt < YT_TAP_TITLE_MS) 0L else YT_GRACE_MS)   // v165: a video that started by itself gets 10 s before any lock
    }

    /**
     * v160: this YouTube build gives the service no view ids, only labels, so everything is found from labels and positions.
     *  - video page   = a "Dislike" label plus "Share" or "Comments"
     *  - YouTube home = the "Home" and "Shorts" tabs are on screen
     *  - the title    = the text right above the Subscribe row (the channel / views / likes line is skipped)
     *  - an ad        = "Skip ad", "Visit advertiser", "Ad 1 of 2" style labels in the player: not judged while it plays
     *    (ad cards in the recommendation list sit below the Subscribe row, so they are never read as the title)
     */
    private fun readYtWatch(root: AccessibilityNodeInfo): YtRead {
        val nodes = ArrayList<YtNode>()
        val br = Rect()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 24 || nodes.size >= 160) return
            val id = n.viewIdResourceName.orEmpty().substringAfter(":id/").lowercase()
            if (id.isNotEmpty() && YT_SKIP_IDS.any { id.contains(it) }) return
            val s = (n.text ?: n.contentDescription)?.toString()?.trim()
            if (!s.isNullOrEmpty() && n.isVisibleToUser) {
                n.getBoundsInScreen(br)
                if (!br.isEmpty) nodes += YtNode(s, br.top, br.bottom)
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        fun has(p: (String) -> Boolean) = nodes.any { p(it.label.lowercase()) }
        val sample = nodes.take(12).joinToString(" | ") { it.label.take(24) }
        if (has { it == "remix" }) return YtRead(false, null, "", false, short = true, sample = sample)
        val ad = nodes.any { it.label.length <= 40 && YT_AD_RE.containsMatchIn(it.label) }
        val onWatch = has { it.startsWith("dislike") } && has { it == "share" || it.startsWith("comment") }
        if (!onWatch) {
            val home = has { it == "home" } && has { it == "shorts" }
            // v164: a video playing inside the home feed (Mute / captions buttons on the card) is a video too: judge it by the title under it
            val pv = if (home) feedPreviewTitle(nodes) else null
            if (pv != null) DebugLog.d("yt: feed preview playing, title='${pv.first.orEmpty().take(60)}'")
            return YtRead(false, pv?.first, "", pv != null, playerFound = pv != null, ad = ad, home = home, sample = sample, preview = pv != null)
        }
        val anchor = nodes.filter { val l = it.label.lowercase(); l.startsWith("subscribe") && l.length <= 14 }.minByOrNull { it.top }
            ?: nodes.filter { it.label.lowercase().startsWith("subscribe to") }.minByOrNull { it.top }
            ?: return YtRead(true, null, "", false, playerFound = true, ad = ad, sample = sample, anchorMissing = true)   // title row scrolled off
        val above = nodes.filter {
            it.bottom <= anchor.top + 4 && anchor.top - it.bottom <= 500 && it.label.length >= 4 && it.label.any { c -> c.isLetter() }
        }
        val pick = above.filter { !YT_META_RE.containsMatchIn(it.label) }.ifEmpty { above }.maxByOrNull { it.bottom }
            ?: return YtRead(true, null, "", false, playerFound = true, ad = ad, sample = sample)
        val hint = nodes.firstOrNull { it.label.startsWith("Subscribe to ", ignoreCase = true) }
            ?.label?.substringAfter(" to ")?.trimEnd('.', ' ').orEmpty()
        DebugLog.d("yt: read title='${pick.label.take(60)}' channel='${hint.take(30)}' ad=$ad")
        return YtRead(true, pick.label, hint, true, playerFound = true, ad = ad, sample = sample)
    }

    /** v164: the home feed plays a muted/unmuted preview inside the card. It has Mute and captions buttons; the title is the first plain text under them. */
    private fun feedPreviewTitle(nodes: List<YtNode>): Pair<String?, String>? {
        val ctl = nodes.filter { YT_PREVIEW_RE.containsMatchIn(it.label) }.minByOrNull { it.top } ?: return null
        val below = nodes.filter {
            it.top >= ctl.bottom && it.top - ctl.bottom <= 800 && it.label.length >= 4 && it.label.any { c -> c.isLetter() } &&
                !YT_DUR_RE.matches(it.label) && !YT_CHROME_RE.containsMatchIn(it.label)
        }.sortedBy { it.top }
        val pick = below.firstOrNull { !YT_META_RE.containsMatchIn(it.label) } ?: below.firstOrNull()
        return Pair(pick?.label, "")
    }

    /** v165: the title of the card that was tapped. Climbs from the tapped view to the first parent that holds readable text. Buttons (Like, Share, ...) give nothing. */
    private fun tappedTitle(src: AccessibilityNodeInfo?): String? {
        var n: AccessibilityNodeInfo = src ?: return null
        val labels = ArrayList<String>()
        for (up in 0 until 6) {
            labels.clear()
            collectLabels(n, 0, labels)
            if (labels.isNotEmpty()) {
                if (labels.any { YT_CTRL_RE.containsMatchIn(it) }) return null
                return labels.firstOrNull { !YT_META_RE.containsMatchIn(it) } ?: labels.first()
            }
            n = n.parent ?: return null
        }
        return null
    }

    private fun collectLabels(x: AccessibilityNodeInfo?, depth: Int, out: ArrayList<String>) {
        if (x == null || depth > 4 || out.size >= 30) return
        val s = (x.text ?: x.contentDescription)?.toString()?.trim()
        if (!s.isNullOrEmpty() && s.length >= 6 && s.any { it.isLetter() } && !YT_DUR_RE.matches(s)) out += s
        for (i in 0 until x.childCount) collectLabels(x.getChild(i), depth + 1, out)
    }

    /** The video must not keep playing behind (or after) a lock. */
    private fun pauseYoutube() {
        runCatching {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
            val cn = android.content.ComponentName(this, MediaListenerService::class.java)
            msm.getActiveSessions(cn).firstOrNull { it.packageName in YOUTUBE_PKGS }?.transportControls?.pause()
        }
    }

    private class YtSession(val title: String, val artist: String, val playing: Boolean)

    /** YouTube's media session (needs notification access, which the music log already uses): the playing video's title and channel. */
    private fun ytSession(): YtSession? = runCatching {
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
        val cn = android.content.ComponentName(this, MediaListenerService::class.java)
        val c = msm.getActiveSessions(cn).firstOrNull { it.packageName in YOUTUBE_PKGS } ?: return@runCatching null
        val md = c.metadata
        val title = md?.let { (it.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
            ?: it.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE))?.trim() }.orEmpty()
        val st = c.playbackState?.state
        YtSession(
            title, md?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty(),
            st == android.media.session.PlaybackState.STATE_PLAYING || st == android.media.session.PlaybackState.STATE_BUFFERING,
        )
    }.getOrNull()

    private fun judgeYt(title: String, hint: String, graceMs: Long = 0L) {
        ytLastTitle = title; ytLastHint = hint
        val d = YtGate.decide(this, title, hint)
        DebugLog.d("yt: '${title.take(70)}' hint='${hint.take(50)}' -> ${d.kind} ${d.chapter}" + if (graceMs > 0L && d.kind != YtGate.Kind.FREE) " (waits ${graceMs / 1000} s)" else "")
        ytLatchFun = d.kind == YtGate.Kind.FUN
        if (d.kind != YtGate.Kind.FREE && graceMs > 0L) {
            // v165: YouTube sometimes starts a video on its own. Lock only if the same video is still the one on screen after the wait.
            val k = ytLatchKey
            ytGraceJob?.cancel()
            ytGraceJob = scope.launch {
                delay(graceMs)
                if (ytLatchKey == k && currentPkg?.let { it in YOUTUBE_PKGS } == true) judgeYt(title, hint, 0L)
            }
            return
        }
        when (d.kind) {
            YtGate.Kind.FREE -> ytExpiryJob?.cancel()
            YtGate.Kind.STUDY_FIRST -> showSiteBlock("ytstudy:${d.chapter}")
            YtGate.Kind.FUN -> scope.launch {
                val st = FunLimit.state(this@BlockerAccessibilityService, prefs)
                if (st.reached || prefs.funBlockedNow()) {
                    FunSignal.quiet = !st.reached
                    pauseYoutube()
                    showFunLimit("yt:fun")
                    return@launch
                }
                val left = prefs.siteUnlockedUntil("yt:fun") - System.currentTimeMillis()
                if (left > 0L) armYtExpiry(left) else { pauseYoutube(); showSiteBlock("yt:fun") }
            }
        }
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
        delay(700L)
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)   // second swipe opens the full tile panel
        delay(1_000L)
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
        val state = tileState(tile) ?: tileIsLit(tile)   // no state info from the tile: read its colour
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

    /** Press BACK until the panel is really gone, so you land on the app screen and not in the notifications. */
    private suspend fun closeShade() {
        for (i in 0 until 3) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(500L)
            val open = findBedtimeTile() != null || rootInActiveWindow?.packageName?.toString() == "com.android.systemui"
            if (!open) return
        }
    }

    /** Reads the tile's colour from a screenshot: coloured = on, grey = off. null = cannot tell. */
    private suspend fun tileIsLit(tile: AccessibilityNodeInfo): Boolean? {
        if (Build.VERSION.SDK_INT < 30) return null
        val r = Rect()
        tile.getBoundsInScreen(r)
        if (r.isEmpty) return null
        val bmp: Bitmap = suspendCancellableCoroutine<Bitmap?> { cont ->
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(shot: ScreenshotResult) {
                    val hb = shot.hardwareBuffer
                    val b = runCatching { Bitmap.wrapHardwareBuffer(hb, shot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                    hb.close()
                    if (cont.isActive) cont.resume(b)
                }
                override fun onFailure(errorCode: Int) { if (cont.isActive) cont.resume(null) }
            })
        } ?: return null
        val dx = (r.width() * 0.15f).toInt()
        val dy = (r.height() * 0.15f).toInt()
        val pts = listOf(r.left + dx to r.top + dy, r.right - dx to r.top + dy, r.left + dx to r.bottom - dy, r.right - dx to r.bottom - dy)
        var sat = 0f
        var n = 0
        val hsv = FloatArray(3)
        for ((x, y) in pts) {
            if (x !in 0 until bmp.width || y !in 0 until bmp.height) continue
            Color.colorToHSV(bmp.getPixel(x, y), hsv)
            sat += hsv[1]; n++
        }
        bmp.recycle()
        if (n == 0) return null
        DebugLog.d("service: Bedtime tile colour saturation ${"%.2f".format(sat / n)}")
        return sat / n > 0.22f
    }

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
        evaluateAppInner(pkg)
        // coding-category app on screen -> notification line; anything else clears it
        if (currentPkg == pkg && AppCategorizer.categoryOf(prefs, packageManager, pkg) == "coding") {
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            com.krishna.questblock.data.CodingSignal.enter(pkg, label)
        } else com.krishna.questblock.data.CodingSignal.leave()
    }

    private suspend fun evaluateAppInner(pkg: String) {
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
        private const val YT_SCROLL_QUIET_MS = 1_500L
        private const val YT_AD_GRACE_MS = 8_000L
        private const val YT_TAP_TITLE_MS = 12_000L
        private const val YT_GRACE_MS = 10_000L
        private val YT_CTRL_RE = Regex("(?i)^(like|dislike|share|download|save|remix|clip|thanks|report|subscribe|subscribed|comments?|add a? ?comment|more|hide|cancel|close|cast|pause|play|next|previous|settings|captions|autoplay|minimi[sz]e|video player)\\b")
        private val YT_AD_RE = Regex("(?i)^(skip ads?|visit advertiser|ad\\s*(\u00B7|:|\\d))")
        private val YT_META_RE = Regex("(?i)(^[@#])|(\\b(views?|likes?|subscribers?|ago|watching|premiered|streamed)\\b)|(^\\W*more$)|^subscribe")
        private val YT_PREVIEW_RE = Regex("(?i)^((un)?mute\\b|(turn (on|off) )?(closed )?captions?\\b|subtitles?\\b|cc$)")
        private val YT_DUR_RE = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")
        private val YT_CHROME_RE = Regex("(?i)\\b(progress|seek bar|scrubber|video player|full ?screen)\\b")
        private val YT_SKIP_IDS = listOf("comment", "engagement", "related", "recommend", "shelf", "results", "live_chat", "ad_", "promo", "shorts")
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
