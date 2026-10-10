package com.krishna.questblock.engine

import android.content.Context
import com.krishna.questblock.data.AppCategorizer
import com.krishna.questblock.data.YtSplit
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.TaskEntity
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
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Completes quests by itself from app usage.
 *  - Time quests: a Study quest ("Chemistry 45 min") is ticked once study apps have that much time today.
 *    A quest with "code" or "coding" in its name uses the Coding category the same way.
 *    Each minute is used for one quest only.
 *  - Discipline quests: a quest like "Keep Fun under 60 min" is ticked after 22:00 if Fun & Games stayed within the limit.
 */
@Singleton
class AutoVerifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: GameRepository,
    private val prefs: Prefs,
) {
    private val mutex = Mutex()
    private val timeRx = Regex("""(\d+)\s*(hours?|hrs?|h|minutes?|mins?|m)\b""", RegexOption.IGNORE_CASE)
    private val limitRx = Regex("""(under|below|less than|max|within)\s*(\d+)\s*(hours?|hrs?|h|minutes?|mins?|m)\b""", RegexOption.IGNORE_CASE)
    private val codeRx = Regex("""\bcod(e|ing)\b""", RegexOption.IGNORE_CASE)

    private fun toMin(n: Int, unit: String) = if (unit.lowercase().startsWith("h")) n * 60 else n

    fun targetMinutes(text: String): Int? {
        val m = timeRx.find(text) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        return toMin(n, m.groupValues[2])
    }

    private fun usageCategory(t: TaskEntity): String? = when {
        t.category == "Study" -> "study"
        codeRx.containsMatchIn(t.title + " " + t.notes) -> "coding"
        else -> null
    }

    /** Returns how many quests were completed. */
    suspend fun run(): Int = mutex.withLock {
        if (!prefs.autoVerify || !UsageTracker.hasAccess(ctx)) return 0
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val today = dayNumber(now)
            val pm = ctx.packageManager
            val per = YtSplit.apply(ctx, prefs, UsageTracker.foreground(ctx, UsageTracker.dayStart(0), now).perPkg, UsageTracker.dayStart(0), now)
            fun usedMin(cat: String): Int =
                (per.entries.filter { AppCategorizer.categoryOf(prefs, pm, it.key) == cat }.sumOf { it.value } / 60_000L).toInt()

            val dayEnd = nextRunDayStart(0, now) - 1
            val open = repo.tasks.first().filter {
                !it.done && !it.isTimerQuest && it.questType != "routine" && it.isAvailableNow(now) && it.isForToday(dayEnd)
            }
            var completed = 0

            // 1) time quests
            for (cat in listOf("study", "coding")) {
                var used = prefs.autoUsed(today, cat)
                var remaining = usedMin(cat) - used
                if (remaining <= 0) continue
                val cands = open
                    .filter { usageCategory(it) == cat && !limitRx.containsMatchIn(it.title + " " + it.notes) }
                    .mapNotNull { t -> (targetMinutes(t.title) ?: targetMinutes(t.notes))?.let { t to it } }
                    .sortedBy { it.second }
                var changed = false
                for ((task, target) in cands) {
                    if (remaining < target) continue
                    repo.completeTask(task)
                    remaining -= target; used += target; completed++; changed = true
                }
                if (changed) prefs.setAutoUsed(today, cat, used)
            }

            // 2) discipline quests, judged once the day is nearly over
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 22) {
                val funMin = usedMin("fun")
                for (t in open) {
                    val text = t.title + " " + t.notes
                    val m = limitRx.find(text) ?: continue
                    if (!text.contains("fun", ignoreCase = true)) continue
                    val limit = toMin(m.groupValues[2].toIntOrNull() ?: continue, m.groupValues[3])
                    if (funMin <= limit) { repo.completeTask(t); completed++ }
                }
            }
            completed
        }
    }
}
