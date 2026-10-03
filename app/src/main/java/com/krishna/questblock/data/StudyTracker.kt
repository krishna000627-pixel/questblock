package com.krishna.questblock.data

import android.content.Context
import com.krishna.questblock.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Pays study coins from Android's usage record instead of a timer.
 * - counts only screen-on time in apps marked Study
 * - leftover minutes carry over (3 min now + 2 min later = 1 coin)
 * - nothing is lost if the service was killed: the next sweep reads the record from the last checkpoint.
 */
object StudyTracker {
    private val lock = Mutex()

    /** [activeLabel] = study app on screen right now (null if none); [carryMs] = progress toward the next coin. */
    class Status(val activeLabel: String?, val carryMs: Long, val unitMs: Long)

    suspend fun sweep(ctx: Context, repo: GameRepository, prefs: Prefs): Status? = lock.withLock {
        if (!UsageTracker.hasAccess(ctx)) return@withLock null
        val perCoin = prefs.studyMinPerCoin.coerceAtLeast(1)
        val unitMs = perCoin * 60_000L
        val now = System.currentTimeMillis()
        val last = prefs.studyCheckpoint

        // first run (or clock went backwards): start counting from now
        if (last <= 0L || now < last) {
            prefs.studyCheckpoint = now
            return@withLock Status(null, 0L, unitMs)
        }
        val studyApps = repo.studyAppsOnce()
        if (studyApps.isEmpty()) {
            prefs.studyCheckpoint = now
            return@withLock Status(null, 0L, unitMs)
        }

        val res = withContext(Dispatchers.IO) { UsageTracker.foreground(ctx, last, now) }
        var activeLabel: String? = null
        var activeCarry = 0L

        for (app in studyApps) {
            val ms = res.perPkg[app.packageName] ?: 0L
            var carry = prefs.studyCarryMs(app.packageName) + ms
            if (ms >= 1000L) {
                var sid = prefs.studySessionId(app.packageName)
                if (sid < 0L || now - prefs.studySessionAt(app.packageName) > 10 * 60_000L) {
                    sid = repo.startStudySession(app.packageName, app.label)
                }
                prefs.setStudySession(app.packageName, sid, now)
                val units = (carry / unitMs).toInt()
                if (units > 0) {
                    repo.creditStudyCoins(sid, units * perCoin, app.label)
                    carry -= units * unitMs
                    DebugLog.d("study: +$units coin(s) for ${app.label}, carry=${carry / 1000}s")
                }
            }
            prefs.setStudyCarryMs(app.packageName, carry)
            if (res.openPkg == app.packageName) { activeLabel = app.label; activeCarry = carry }
        }
        prefs.studyCheckpoint = now
        Status(activeLabel, activeCarry, unitMs)
    }
}
