package com.krishna.questblock.engine

import android.content.Context
import com.krishna.questblock.data.AppCategorizer
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.UsageTracker
import com.krishna.questblock.data.isAvailableNow
import com.krishna.questblock.data.level
import com.krishna.questblock.data.nextRunDayStart
import com.krishna.questblock.ui.isForToday
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the app's own data (quests, focus, usage) and boils it down to a [Snapshot]. */
@Singleton
class InsightsBuilder @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: GameRepository,
    private val prefs: Prefs,
) {
    suspend fun build(): Snapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val player = repo.player.first()
        val tasks = repo.tasks.first()
        val focus = repo.focusLogs.first()
        val access = UsageTracker.hasAccess(ctx)
        val pm = ctx.packageManager
        val dayEnd = nextRunDayStart(0, now) - 1

        val days = (0 until 14).map { i ->
            val from = UsageTracker.dayStart(i)
            val end = from + 86_400_000L
            var st = 0; var co = 0; var ne = 0; var fu = 0
            if (access) {
                val to = if (i == 0) now else end
                UsageTracker.foreground(ctx, from, to).perPkg.forEach { (pkg, ms) ->
                    val m = (ms / 60_000L).toInt()
                    when (AppCategorizer.categoryOf(prefs, pm, pkg)) {
                        "study" -> st += m
                        "coding" -> co += m
                        "fun" -> fu += m
                        else -> ne += m
                    }
                }
            }
            val done = tasks.count { it.done && (it.completedAt ?: 0L) >= from && (it.completedAt ?: 0L) < end }
            val fm = focus.filter { it.completed && it.startedAt >= from && it.startedAt < end }.sumOf { it.minutes }
            DayStats(from, st, co, ne, fu, done, fm)
        }

        val rate = (1..3).associateWith { d ->
            val done = tasks.count { it.done && it.difficulty == d }
            val failed = tasks.count { !it.done && it.dueAt != null && it.dueAt < now && it.difficulty == d }
            val total = done + failed
            if (total >= 3) done.toDouble() / total else -1.0
        }
        val open = tasks.filter { !it.done && it.isAvailableNow(now) && it.isForToday(dayEnd) }
            .map { QuestBrief(it.title, it.category, it.difficulty, it.dueAt != null && it.dueAt < now) }
        val doneFocus = focus.filter { it.completed }

        Snapshot(
            days = days,
            streak = player.streak,
            xp = player.xp,
            level = player.level(),
            totalQuestsDone = tasks.count { it.done },
            totalFocusMin = doneFocus.sumOf { it.minutes },
            longestFocusMin = doneFocus.maxOfOrNull { it.minutes } ?: 0,
            openToday = open,
            funLimitMin = prefs.effectiveFunLimitMin,
            examMode = prefs.examMode,
            usageAccess = access,
            difficultyRate = rate,
        )
    }
}
