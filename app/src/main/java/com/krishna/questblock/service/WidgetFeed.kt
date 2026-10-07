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
            combine(repo.tasks, repo.allSubTasks, repo.player) { tasks, subs, p -> lineFor(tasks, subs) to p.coins }
                .distinctUntilChanged()
                .collect { (line, coins) -> runCatching { QuestWidget.update(app.applicationContext, line, coins) } }
        }
    }

    fun lineFor(tasks: List<TaskEntity>, subs: List<SubTask>): String {
        val now = System.currentTimeMillis()
        val dayEnd = nextRunDayStart(0, now) - 1
        val open = tasks.filter { !it.done && it.isAvailableNow(now) }
        val today = open.filter { it.isForToday(dayEnd) }
            .sortedWith(compareBy<TaskEntity> { it.priority }.thenBy { it.dueAt ?: Long.MAX_VALUE })
        // routine day: the next unticked step comes first
        val routines = today.filter { it.questType == "routine" }
        for (r in routines) {
            val next = subs.filter { it.taskId == r.id }.sortedBy { it.id }.firstOrNull { !it.done }
            if (next != null) return "📋 ${r.title}: ${next.title}"
        }
        routines.firstOrNull()?.let { return "📋 ${it.title}" }
        today.firstOrNull()?.let { return "🎯 ${it.title}" }
        // nothing due today: show the next thing that is waiting
        open.filter { it.dueAt != null }.minByOrNull { it.dueAt ?: Long.MAX_VALUE }?.let { return "⏰ ${it.title}" }
        open.minByOrNull { it.priority }?.let { return "📌 ${it.title}" }
        return "All clear today"
    }
}
