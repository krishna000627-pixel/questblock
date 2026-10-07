package com.krishna.questblock.service

import android.content.Context
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.SubTask
import com.krishna.questblock.data.TaskEntity
import com.krishna.questblock.data.isAvailableNow
import com.krishna.questblock.data.nextRunDayStart
import com.krishna.questblock.ui.isForToday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Keeps the home-screen widget current whenever quests, steps or coins change, even if the app is not on screen. */
object WidgetFeed {
    private var started = false

    fun start(app: Context, repo: GameRepository) {
        if (started) return
        started = true
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            combine(repo.tasks, repo.allSubTasks, repo.player) { tasks, subs, p -> Pair(pickFor(tasks, subs), Triple(p.coins, p.streak, p.xp)) }
                .distinctUntilChanged()
                .collect { (pick, t) -> runCatching { QuestWidget.update(app.applicationContext, pick.line, t.first, pick.action, t.second, t.third) } }
        }
    }

    /** What the widget shows and what its Done button does ("step:ID", "task:ID" or "" for nothing). */
    data class Pick(val line: String, val action: String)

    fun lineFor(tasks: List<TaskEntity>, subs: List<SubTask>): String = pickFor(tasks, subs).line

    fun pickFor(tasks: List<TaskEntity>, subs: List<SubTask>): Pick {
        val now = System.currentTimeMillis()
        val dayEnd = nextRunDayStart(0, now) - 1
        val open = tasks.filter { !it.done && it.isAvailableNow(now) }
        val today = open.filter { it.isForToday(dayEnd) }
            .sortedWith(compareBy<TaskEntity> { it.priority }.thenBy { it.dueAt ?: Long.MAX_VALUE })
        // routine day: the next unticked step comes first
        val routines = today.filter { it.questType == "routine" }
        for (r in routines) {
            val next = subs.filter { it.taskId == r.id }.sortedBy { it.id }.firstOrNull { !it.done }
            if (next != null) return Pick("📋 ${r.title}: ${next.title}", "step:${next.id}")
        }
        routines.firstOrNull()?.let { return Pick("📋 ${it.title}", "task:${it.id}") }
        today.firstOrNull()?.let { return Pick("🎯 ${it.title}", "task:${it.id}") }
        // nothing due today: show the next thing that is waiting
        open.filter { it.dueAt != null }.minByOrNull { it.dueAt ?: Long.MAX_VALUE }?.let { return Pick("⏰ ${it.title}", "") }
        open.minByOrNull { it.priority }?.let { return Pick("📌 ${it.title}", "") }
        return Pick("All clear today", "")
    }
}
