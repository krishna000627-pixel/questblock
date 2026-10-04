package com.krishna.questblock.engine

import android.content.Context
import com.krishna.questblock.data.AppCategorizer
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.UsageTracker
import com.krishna.questblock.data.dayNumber
import com.krishna.questblock.data.isAvailableNow
import com.krishna.questblock.data.nextRunDayStart
import com.krishna.questblock.ui.isForToday
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Completes Study quests by itself. A Study quest with a time in its title or notes ("Chemistry 45 min")
 * is ticked once the Study category has enough time today. Each study minute is used for one quest only.
 */
@Singleton
class AutoVerifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: GameRepository,
    private val prefs: Prefs,
) {
    private val mutex = Mutex()
    private val timeRx = Regex("""(\d+)\s*(hours?|hrs?|h|minutes?|mins?|m)\b""", RegexOption.IGNORE_CASE)

    fun targetMinutes(text: String): Int? {
        val m = timeRx.find(text) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val unit = m.groupValues[2].lowercase()
        return if (unit.startsWith("h")) n * 60 else n
    }

    /** Returns how many quests were completed. */
    suspend fun run(): Int = mutex.withLock {
        if (!prefs.autoVerify || !UsageTracker.hasAccess(ctx)) return 0
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val today = dayNumber(now)
            val pm = ctx.packageManager
            val studyMin = UsageTracker.foreground(ctx, UsageTracker.dayStart(0), now).perPkg.entries
                .filter { AppCategorizer.categoryOf(prefs, pm, it.key) == "study" }
                .sumOf { it.value } / 60_000L
            var used = prefs.autoStudyUsed(today)
            var remaining = studyMin.toInt() - used
            if (remaining <= 0) return@withContext 0
            val dayEnd = nextRunDayStart(0, now) - 1
            val candidates = repo.tasks.first()
                .filter { !it.done && it.category == "Study" && !it.isTimerQuest && it.questType != "routine" &&
                    it.isAvailableNow(now) && it.isForToday(dayEnd) }
                .mapNotNull { t -> (targetMinutes(t.title) ?: targetMinutes(t.notes))?.let { t to it } }
                .sortedBy { it.second }
            var completed = 0
            for ((task, target) in candidates) {
                if (remaining < target) continue
                repo.completeTask(task)
                remaining -= target; used += target; completed++
            }
            if (completed > 0) prefs.setAutoStudyUsed(today, used)
            completed
        }
    }
}
