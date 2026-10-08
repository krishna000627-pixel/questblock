package com.krishna.questblock.data

import kotlinx.coroutines.sync.withLock
import android.content.Context
import androidx.room.withTransaction
import com.krishna.questblock.service.Reminders
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import com.krishna.questblock.ui.isForToday
import kotlin.math.roundToInt

fun baseReward(difficulty: Int): Int = when (difficulty) { 1 -> 10; 2 -> 25; else -> 50 }
const val FOCUS_BONUS_COINS = 15
const val STEP_SKIP_COST = 5
const val DAILY_LOGIN_COINS = 10

const val DEFAULT_RATE_PER_5 = 5
data class UnlockResult(
    val ok: Boolean, val charged: Boolean = false, val cost: Int = 0,
    val before: Int = 0, val after: Int = 0, val minutes: Int = 0, val msBought: Long = 0L,
)

const val MAX_UNLOCK_BLOCKS = 144   // 12 h of 5-minute blocks
private const val DOUBLE_PAY_WINDOW_MS = 5_000L

/** Cost of [blocks] 5-minute blocks: blocks × rate (the app's own rate, else the global one) × price %. */
fun unlockCost(blocks: Int, globalRate: Int, app: BlockedApp?): Int {
    val rate = app?.ratePer5?.takeIf { it > 0 } ?: globalRate
    return ((blocks * rate * (app?.priceMult ?: 100)) / 100.0).roundToInt().coerceAtLeast(1)
}

/** [pct] percent on top of [cost] (used by "Routine first"). */
fun withSurcharge(cost: Int, pct: Int): Int = if (pct <= 0) cost else ((cost * (100 + pct)) / 100.0).roundToInt()

fun fmtBlocks(blocks: Int): String {
    val m = blocks * 5
    return if (m >= 60) "${m / 60}h" + (if (m % 60 != 0) " ${m % 60}m" else "") else "${m}m"
}

/** Everything the quest form can set. */
data class QuestDraft(
    val title: String,
    val difficulty: Int = 1,
    val category: String = "Personal",
    val priority: Int = 2,
    val notes: String = "",
    val dueAt: Long? = null,
    val isRecurring: Boolean = false,
    val recurType: String = "",
    val linkedApps: String = "",
    val isTimerQuest: Boolean = false,
    val timerDurationMs: Long = 0L,
    val daysMask: Int = 0,
    val questType: String = "onetime",
    val challengeDays: Int = 0,
    val steps: List<String> = emptyList(),
)

fun dayNumber(now: Long): Long = (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L

val ACHIEVEMENTS_CATALOG = listOf(
    Achievement("first_quest",  "First Step",    "👣", "Complete your first quest"),
    Achievement("streak_3",     "On Fire",       "🔥", "3-day streak"),
    Achievement("streak_7",     "Week Warrior",  "⚔️", "7-day streak"),
    Achievement("streak_30",    "Legend",        "🏆", "30-day streak"),
    Achievement("quests_10",    "Adventurer",    "🗺️", "Complete 10 quests"),
    Achievement("quests_50",    "Hero",          "🦸", "Complete 50 quests"),
    Achievement("quests_100",   "Champion",      "👑", "Complete 100 quests"),
    Achievement("coins_100",    "Coin Hoarder",  "🪙", "Earn 100 coins total"),
    Achievement("level_5",      "Level 5",       "⭐", "Reach level 5"),
    Achievement("level_10",     "Level 10",      "🌟", "Reach level 10"),
    Achievement("focus_1",      "Focused",       "🎯", "Complete first Focus session"),
    Achievement("timer_quest_1","Timer Master",  "⏱️", "Complete first Timer Quest"),
)

@Singleton
class GameRepository @Inject constructor(
    private val db: AppDatabase,
    private val taskDao: TaskDao,
    private val subTaskDao: SubTaskDao,
    private val appDao: BlockedAppDao,
    private val playerDao: PlayerDao,
    private val unlockEventDao: UnlockEventDao,
    private val inventoryDao: InventoryDao,
    private val studySessionDao: StudySessionDao,
    private val achievementDao: AchievementDao,
    private val customRewardDao: CustomRewardDao,
    private val focusLogDao: FocusLogDao,
    private val coinLogDao: CoinLogDao,
    private val unlockSessionDao: UnlockSessionDao,
    private val prefs: Prefs,
    @ApplicationContext private val ctx: Context,
) {
    val tasks: Flow<List<TaskEntity>> = taskDao.observeAll()
    val blockedApps: Flow<List<BlockedApp>> = appDao.observeAll()
    val recentUnlocks: Flow<List<UnlockEvent>> = unlockEventDao.observeRecent()
    val totalCoinsSpent: Flow<Int?> = unlockEventDao.observeTotalSpent()
    val inventory: Flow<List<InventoryItem>> = inventoryDao.observeAll()
    val studySessions: Flow<List<StudySession>> = studySessionDao.observeRecent()
    val totalStudyCoins: Flow<Int?> = studySessionDao.observeTotalEarned()
    val achievements: Flow<List<Achievement>> = achievementDao.observeAll()
    val customRewards: Flow<List<CustomReward>> = customRewardDao.observeAll()
    val focusLogs: Flow<List<FocusLog>> = focusLogDao.observeRecent()
    val coinLog: Flow<List<CoinLog>> = coinLogDao.observeRecent()
    val allSubTasks: Flow<List<SubTask>> = subTaskDao.observeAll()
    val unlockSessions: Flow<List<UnlockSession>> = unlockSessionDao.observeRecent()

    // ── Settings shortcuts ────────────────────────────────────────────────
    fun ratePer5(): Int = prefs.ratePer5
    fun siteUnlockedUntil(domain: String): Long = prefs.siteUnlockedUntil(domain)
    fun awayAt(pkg: String): Long = prefs.awayAt(pkg)
    fun shortPicked(kind: String): Boolean = prefs.shortPicked(kind)
    fun changeShortPicked(kind: String, on: Boolean) = prefs.changeShortPicked(kind, on)
    fun shortPending(kind: String): Boolean? = prefs.pendingFlag("opt:short_$kind")
    fun studyMinPerCoin(): Int = prefs.studyMinPerCoin
    fun focusBonus(): Int = prefs.focusBonus
    fun holdSeconds(): Int = prefs.holdSeconds
    fun showQuotes(): Boolean = prefs.showQuotes

    val player: Flow<Player> = playerDao.observe().map { stored ->
        val p = stored ?: Player()
        val now = System.currentTimeMillis()
        if (p.lastCompletionDay < dayNumber(now) - 1 && !p.hasStreakShield())
            p.copy(streak = 0) else p
    }

    fun subTasks(taskId: Long): Flow<List<SubTask>> = subTaskDao.observe(taskId)

    private suspend fun logCoin(amount: Int, source: String, detail: String = "") {
        coinLogDao.insert(CoinLog(amount = amount, source = source, detail = detail))
    }

    /** A free app access (daily-limit app, study app, Focus Quest, outside lock hours): 0 coins, with the reason. */
    suspend fun logFreeAccess(reason: String, detail: String) = logCoin(0, reason, detail)

    /** Is [pkg] currently open because a running Focus Quest links it? */
    suspend fun focusUnlockedPkg(pkg: String): Boolean {
        val p = playerDao.get() ?: return false
        if (p.focusModeUntil <= System.currentTimeMillis() || p.focusQuestId <= 0L) return false
        return taskDao.get(p.focusQuestId)?.linkedApps?.split(",")?.contains(pkg) == true
    }

    // ── Profile ───────────────────────────────────────────────────────────
    suspend fun saveProfile(name: String, goal: String, dob: String) {
        val p = playerDao.get() ?: Player()
        playerDao.upsert(p.copy(name = name.trim(), goal = goal.trim(), dob = dob.trim()))
    }

    // ── Tasks ─────────────────────────────────────────────────────────────
    suspend fun addTask(title: String, difficulty: Int): Long = addTask(QuestDraft(title = title, difficulty = difficulty))

    suspend fun addTask(d: QuestDraft): Long {
        val id = taskDao.insert(
            TaskEntity(title = d.title.trim(), difficulty = d.difficulty, category = d.category,
                priority = d.priority, notes = d.notes, dueAt = d.dueAt,
                isRecurring = d.isRecurring, recurType = d.recurType,
                linkedApps = d.linkedApps, isTimerQuest = d.isTimerQuest, timerDurationMs = d.timerDurationMs,
                daysMask = d.daysMask, questType = d.questType, challengeDays = d.challengeDays)
        )
        if (d.questType == "routine")
            d.steps.map { it.trim() }.filter { it.isNotEmpty() }.forEach { subTaskDao.insert(SubTask(taskId = id, title = it)) }
        taskDao.get(id)?.let { scheduleReminder(it) }
        return id
    }

    suspend fun getTask(id: Long): TaskEntity? = taskDao.get(id)
    suspend fun getSubTasks(taskId: Long): List<SubTask> = subTaskDao.getAll(taskId)

    suspend fun updateTask(id: Long, d: QuestDraft) {
        val t = taskDao.get(id) ?: return
        val updated = t.copy(
            title = d.title.trim(), difficulty = d.difficulty, category = d.category, priority = d.priority,
            notes = d.notes, dueAt = d.dueAt, isRecurring = d.isRecurring, recurType = d.recurType,
            linkedApps = d.linkedApps, isTimerQuest = d.isTimerQuest, timerDurationMs = d.timerDurationMs,
            daysMask = d.daysMask, questType = d.questType, challengeDays = d.challengeDays,
        )
        taskDao.update(updated)
        val old = subTaskDao.getAll(id)
        subTaskDao.deleteAll(id)
        if (d.questType == "routine") {
            d.steps.map { it.trim() }.filter { it.isNotEmpty() }.forEach { step ->
                subTaskDao.insert(SubTask(taskId = id, title = step, done = old.firstOrNull { it.title == step }?.done == true))
            }
        }
        cancelReminder(id)
        scheduleReminder(updated)
    }

    // ── Reminders ─────────────────────────────────────────────────────────
    private fun scheduleReminder(task: TaskEntity) {
        if (!prefs.remindersEnabled || task.done) return
        val due = task.dueAt ?: return
        val at = due - prefs.reminderLeadMin * 60_000L
        if (at > System.currentTimeMillis()) Reminders.schedule(ctx, task.id, task.title, at)
    }
    private fun cancelReminder(id: Long) = Reminders.cancel(ctx, id)

    suspend fun rescheduleAllReminders() {
        taskDao.observeAll().first().forEach { if (!it.done) scheduleReminder(it) }
    }

    suspend fun addSubTask(taskId: Long, title: String) =
        subTaskDao.insert(SubTask(taskId = taskId, title = title))

    /** Pay [STEP_SKIP_COST] coins to skip a routine step without breaking the routine. No reward for a skipped step. */
    suspend fun skipStep(task: TaskEntity, sub: SubTask): Boolean {
        if (sub.done) return false
        val p = playerDao.get() ?: Player()
        if (p.coins < STEP_SKIP_COST) return false
        playerDao.upsert(p.copy(coins = p.coins - STEP_SKIP_COST))
        logCoin(-STEP_SKIP_COST, "Skipped step", sub.title)
        prefs.markStepPaid(sub.id)
        subTaskDao.update(sub.copy(done = true))
        return true
    }

    /** 50 while "Routine first" is on and a routine due today is unfinished, else 0. */
    suspend fun routineSurchargePct(): Int {
        if (!prefs.routineFirst) return 0
        val now = System.currentTimeMillis()
        val dayEnd = nextRunDayStart(0, now) - 1
        val open = taskDao.observeAll().first().any {
            !it.done && it.questType == "routine" && it.isAvailableNow(now) && it.isForToday(dayEnd)
        }
        return if (open) 50 else 0
    }

    suspend fun toggleSubTask(sub: SubTask) {
        if (!sub.done) { taskDao.get(sub.taskId)?.let { completeStep(it, sub); return } }
        subTaskDao.update(sub.copy(done = !sub.done))
    }

    /** Ticks one routine step. Each step is worth an equal share of the routine's reward (paid once per step). */
    suspend fun completeStep(task: TaskEntity, sub: SubTask) {
        if (sub.done) return
        subTaskDao.update(sub.copy(done = true))
        if (prefs.stepPaid(sub.id)) return
        prefs.markStepPaid(sub.id)
        val n = subTaskDao.getAll(task.id).size.coerceAtLeast(1)
        val share = maxOf(1, (baseReward(task.difficulty) + n - 1) / n)
        val p = playerDao.get() ?: Player()
        val earned = (share * (if (p.hasCoinMultiplier()) 1.5 else 1.0)).roundToInt()
        val xp = (share * (if (p.hasXpBoost()) 2.0 else 1.0)).roundToInt()
        playerDao.upsert(p.copy(coins = p.coins + earned, xp = p.xp + xp))
        logCoin(earned, "Routine step", sub.title)
    }

    // ── v73: +5 min of fun time, and undo for a finished quest ──
    suspend fun buyFunExtension(): Boolean = db.withTransaction {
        val p = playerDao.get() ?: Player()
        if (p.coins < FUN_EXTEND_COST) return@withTransaction false
        playerDao.upsert(p.copy(coins = p.coins - FUN_EXTEND_COST))
        logCoin(-FUN_EXTEND_COST, "Fun +$FUN_EXTEND_MIN min", "")
        prefs.addFunBonusToday(FUN_EXTEND_MIN * 60_000L)
        true
    }

    /** Everything needed to take a quest completion back. */
    class CompletionUndo(
        val task: TaskEntity, val before: Player, val coinsGained: Int, val xpGained: Int,
        val knownIds: Set<Long>, val freezes: Int,
    )

    /** Like [completeTask], but returns what is needed to undo it (null if nothing was completed). */
    suspend fun completeTaskUndoable(task: TaskEntity): CompletionUndo? {
        val fresh = taskDao.get(task.id) ?: return null
        if (fresh.done) return null
        val before = playerDao.get() ?: Player()
        val ids = taskDao.observeAll().first().map { it.id }.toSet()
        val freezes = prefs.streakFreezes
        completeTask(task)
        if (taskDao.get(task.id)?.done != true) return null
        val after = playerDao.get() ?: before
        return CompletionUndo(fresh, before, after.coins - before.coins, after.xp - before.xp, ids, freezes)
    }

    suspend fun undoCompletion(u: CompletionUndo) = db.withTransaction {
        // the next copy of a recurring quest was created by the completion: remove it
        taskDao.observeAll().first().filter { it.id !in u.knownIds }.forEach {
            cancelReminder(it.id); subTaskDao.deleteAll(it.id); taskDao.delete(it)
        }
        taskDao.update(u.task)
        scheduleReminder(u.task)
        val now = playerDao.get() ?: u.before
        playerDao.upsert(now.copy(
            coins = (now.coins - u.coinsGained).coerceAtLeast(0), xp = (now.xp - u.xpGained).coerceAtLeast(0),
            streak = u.before.streak, lastCompletionDay = u.before.lastCompletionDay,
            xpBoostUntil = u.before.xpBoostUntil, weeklyXpLog = u.before.weeklyXpLog,
            focusModeUntil = u.before.focusModeUntil, focusQuestId = u.before.focusQuestId, focusTotalMs = u.before.focusTotalMs,
        ))
        prefs.streakFreezes = u.freezes
        if (u.coinsGained != 0) logCoin(-u.coinsGained, "Undo quest", u.task.title)
    }

    suspend fun deleteTask(task: TaskEntity) {
        cancelReminder(task.id)
        subTaskDao.deleteAll(task.id)
        taskDao.delete(task)
    }

    suspend fun completeTask(task: TaskEntity) {
        db.withTransaction {
            val fresh = taskDao.get(task.id) ?: return@withTransaction
            if (fresh.done) return@withTransaction
            val now = System.currentTimeMillis()
            if (!fresh.isAvailableNow(now)) return@withTransaction
            if (fresh.questType == "routine" && subTaskDao.getAll(fresh.id).any { !it.done }) return@withTransaction

            val today = dayNumber(now)
            val qStreak = when {
                fresh.lastDoneDay == today -> fresh.streak
                streakContinues(fresh.daysMask, fresh.lastDoneDay, today) -> fresh.streak + 1
                else -> 1
            }
            taskDao.update(fresh.copy(done = true, completedAt = now, timerQuestActive = false,
                streak = qStreak, lastDoneDay = today))
            cancelReminder(fresh.id)

            val p = playerDao.get() ?: Player()
            val newStreak = when (p.lastCompletionDay) {
                today -> p.streak
                today - 1 -> p.streak + 1
                // a Streak Freeze covers exactly one missed day
                else -> if (p.lastCompletionDay == today - 2 && p.streak > 0 && prefs.streakFreezes > 0) {
                    prefs.streakFreezes = prefs.streakFreezes - 1; p.streak + 1
                } else 1
            }
            val base = baseReward(fresh.difficulty)
            val xpMulti  = if (p.hasXpBoost()) 2.0 else 1.0
            val coinMulti = if (p.hasCoinMultiplier()) 1.5 else 1.0
            val streakMulti = 1.0 + (newStreak - 1).coerceIn(0, 5) * 0.1
            // a routine's reward is paid step by step, so finishing it adds nothing on top
            val isRoutine = fresh.questType == "routine"
            val earned = if (isRoutine) 0 else (base * streakMulti * coinMulti).roundToInt()
            val xpEarned = if (isRoutine) 0 else (base * xpMulti).roundToInt()

            val log = p.weeklyXpLog.split(",").filter { it.isNotBlank() }.toMutableList()
            val todayEntry = "$today:${xpEarned}"
            log.add(todayEntry)
            if (log.size > 70) log.removeAt(0)

            // Risk/reward: streak milestones pay a bonus; 3 hard quests in a day switch on an hour of XP boost.
            val milestoneXp = if (newStreak > p.streak) when (newStreak) { 7 -> 100; 14 -> 200; 30 -> 500; else -> 0 } else 0
            val hardCount = if (fresh.difficulty == 3) taskDao.hardDoneSince(startOfToday(now)) else 0
            val hardBoost = if (hardCount == 3) maxOf(p.xpBoostUntil, now + 3_600_000L) else p.xpBoostUntil

            val wasFocusQuest = p.focusQuestId == fresh.id
            playerDao.upsert(p.copy(
                coins = p.coins + earned + milestoneXp / 5, xp = p.xp + xpEarned + milestoneXp,
                xpBoostUntil = hardBoost,
                streak = newStreak, lastCompletionDay = today,
                weeklyXpLog = log.joinToString(","),
                focusModeUntil = if (wasFocusQuest) 0L else p.focusModeUntil,
                focusQuestId   = if (wasFocusQuest) -1L else p.focusQuestId,
                focusTotalMs   = if (wasFocusQuest) 0L else p.focusTotalMs,
            ))
            if (earned > 0) logCoin(earned, "Quest", fresh.title)
            if (milestoneXp > 0) logCoin(milestoneXp / 5, "Streak bonus", "$newStreak-day streak")
            checkAchievements(p.copy(xp = p.xp + xpEarned, streak = newStreak))

            val challengeDone = fresh.questType == "challenge" && fresh.challengeDays > 0 && qStreak >= fresh.challengeDays
            if (fresh.isRecurring && !challengeDone) {
                when {
                    fresh.daysMask != 0 -> {
                        val nextDay = nextRunDayStart(fresh.daysMask, now)
                        spawnNext(fresh, fresh.dueAt?.let { withTimeOfDay(nextDay, it) }, nextDay, now, qStreak, today)
                    }
                    fresh.dueAt != null -> {
                        val offset = if (fresh.recurType == "weekly") 7 * 86_400_000L else 86_400_000L
                        spawnNext(fresh, fresh.dueAt + offset, 0L, now, qStreak, today)
                    }
                    else -> {
                        val mask = if (fresh.recurType == "weekly") 1 shl dowIndex(now) else 0
                        spawnNext(fresh, null, nextRunDayStart(mask, now), now, qStreak, today)
                    }
                }
            }
        }
    }

    private suspend fun spawnNext(src: TaskEntity, dueAt: Long?, availableFrom: Long, now: Long, streak: Int, today: Long) {
        val newId = taskDao.insert(src.copy(id = 0, done = false, completedAt = null, dueAt = dueAt,
            availableFrom = availableFrom, createdAt = now, timerQuestActive = false, timerQuestStarted = null,
            streak = streak, lastDoneDay = today))
        subTaskDao.getAll(src.id).forEach { subTaskDao.insert(SubTask(taskId = newId, title = it.title)) }
        taskDao.get(newId)?.let { scheduleReminder(it) }
    }

    private suspend fun checkAchievements(p: Player) {
        suspend fun unlock(id: String) {
            val existing = achievementDao.get(id)
            val catalog = ACHIEVEMENTS_CATALOG.find { it.id == id } ?: return
            if (existing?.unlockedAt == null) {
                achievementDao.upsert(catalog.copy(unlockedAt = System.currentTimeMillis()))
            }
        }
        if (p.streak >= 3)  unlock("streak_3")
        if (p.streak >= 7)  unlock("streak_7")
        if (p.streak >= 30) unlock("streak_30")
        if (p.level() >= 5)  unlock("level_5")
        if (p.level() >= 10) unlock("level_10")
        if (p.xp >= 100) unlock("coins_100")
    }

    suspend fun unlockFirstQuestAchievement() {
        val catalog = ACHIEVEMENTS_CATALOG.find { it.id == "first_quest" } ?: return
        val existing = achievementDao.get("first_quest")
        if (existing?.unlockedAt == null)
            achievementDao.upsert(catalog.copy(unlockedAt = System.currentTimeMillis()))
    }

    // ── Daily login ───────────────────────────────────────────────────────
    suspend fun claimDailyLogin(): Int = db.withTransaction {
        if (!prefs.dailyLoginEnabled) return@withTransaction 0
        val today = dayNumber(System.currentTimeMillis())
        val p = playerDao.get() ?: Player()
        if (p.lastLoginDay >= today) return@withTransaction 0
        val coins = prefs.dailyLoginCoins
        playerDao.upsert(p.copy(coins = p.coins + coins, lastLoginDay = today))
        logCoin(coins, "Daily login")
        coins
    }

    /** Reward for a finished challenge. */
    suspend fun grantBonus(coins: Int, xp: Int, source: String, detail: String = "") = db.withTransaction {
        val p = playerDao.get() ?: Player()
        playerDao.upsert(p.copy(coins = p.coins + coins, xp = p.xp + xp))
        logCoin(coins, source, detail)
    }

    /** Attendance coins: +delta when marked Present, -delta when changed back. Never takes the balance below 0. */
    suspend fun attendanceCoins(delta: Int, detail: String) = db.withTransaction {
        val p = playerDao.get() ?: Player()
        val applied = if (delta < 0) -minOf(-delta, p.coins) else delta
        if (applied != 0) {
            playerDao.upsert(p.copy(coins = p.coins + applied))
            logCoin(applied, "Attendance", detail)
        }
    }

    // ── Focus ─────────────────────────────────────────────────────────────
    suspend fun startFocus(durationMs: Long, task: TaskEntity? = null): Boolean = db.withTransaction {
        val now = System.currentTimeMillis()
        prefs.breakUntil = 0L
        val p = playerDao.get() ?: Player()
        if (p.focusModeUntil > now) return@withTransaction false
        val end = now + durationMs
        playerDao.upsert(p.copy(focusModeUntil = end, focusQuestId = task?.id ?: -1L, focusTotalMs = durationMs))
        val fresh = task?.let { taskDao.get(it.id) }
        if (fresh != null) {
            taskDao.update(fresh.copy(timerQuestActive = true, timerQuestStarted = now))
            fresh.linkedApps.split(",").filter { it.isNotBlank() }.forEach { pkg ->
                appDao.get(pkg)?.let { appDao.upsert(it.copy(unlockedUntil = end)) }
            }
        }
        true
    }

    suspend fun finishFocus(completed: Boolean) {
        var questId = -1L
        var end = 0L
        var total = 0L
        val ended = db.withTransaction {
            val p = playerDao.get() ?: return@withTransaction false
            if (p.focusModeUntil == 0L) return@withTransaction false
            questId = p.focusQuestId
            end = p.focusModeUntil
            total = p.focusTotalMs
            playerDao.upsert(p.copy(
                focusModeUntil = 0L, focusQuestId = -1L, focusTotalMs = 0L,
                coins = p.coins + if (completed) prefs.focusBonus else 0,
                xp = p.xp + if (completed) (p.focusTotalMs / 300_000L).toInt().coerceIn(0, 30) else 0,
            ))
            if (completed && prefs.focusBonus > 0) logCoin(prefs.focusBonus, "Focus bonus")
            true
        }
        if (!ended) return

        val task = if (questId > 0) taskDao.get(questId) else null
        run {
            val startedAt = end - total
            val elapsed = if (completed) total else (System.currentTimeMillis() - startedAt).coerceIn(0L, total)
            focusLogDao.insert(FocusLog(startedAt = startedAt, minutes = (elapsed / 60_000L).toInt(),
                completed = completed, questTitle = task?.title.orEmpty()))
        }
        if (task != null) {
            task.linkedApps.split(",").filter { it.isNotBlank() }.forEach { pkg ->
                val app = appDao.get(pkg)
                if (app != null && app.unlockedUntil <= end) appDao.upsert(app.copy(unlockedUntil = 0L))
            }
            taskDao.get(task.id)?.let { taskDao.update(it.copy(timerQuestActive = false)) }
        }
        if (completed) {
            // Break cycle: 5 min after a session, 15 min after every 4th one today
            val day = dayNumber(System.currentTimeMillis())
            val n = prefs.focusDoneToday(day) + 1
            prefs.setFocusDoneToday(day, n)
            prefs.breakIsLong = n % 4 == 0
            prefs.breakUntil = System.currentTimeMillis() + (if (n % 4 == 0) 15 else 5) * 60_000L
            unlockAchievement("focus_1")
            if (task != null) {
                unlockAchievement("timer_quest_1")
                if (!task.done) completeTask(task)
            }
        }
    }

    fun focusPenalty(): Int = prefs.focusExitPenalty

    /** Giving up a running session. Free in the first minute, then costs [Prefs.focusExitPenalty] coins (never below 0). Returns coins lost. */
    suspend fun abandonFocus(): Int {
        val p0 = playerDao.get() ?: return 0
        val now = System.currentTimeMillis()
        val running = p0.focusModeUntil > now
        val elapsed = now - (p0.focusModeUntil - p0.focusTotalMs)
        finishFocus(completed = false)
        if (!running || elapsed < 60_000L || prefs.focusExitPenalty <= 0) return 0
        return db.withTransaction {
            val p = playerDao.get() ?: return@withTransaction 0
            val pen = minOf(p.coins, prefs.focusExitPenalty)
            if (pen > 0) {
                playerDao.upsert(p.copy(coins = p.coins - pen))
                logCoin(-pen, "Focus exit", "Gave up early")
            }
            pen
        }
    }

    suspend fun settleFocus() {
        val p = playerDao.get() ?: return
        if (p.focusModeUntil > 0L && System.currentTimeMillis() >= p.focusModeUntil) finishFocus(true)
    }

    private suspend fun unlockAchievement(id: String) {
        val catalog = ACHIEVEMENTS_CATALOG.find { it.id == id } ?: return
        if (achievementDao.get(id)?.unlockedAt == null)
            achievementDao.upsert(catalog.copy(unlockedAt = System.currentTimeMillis()))
    }

    // ── Apps ──────────────────────────────────────────────────────────────
    suspend fun getBlockedApp(pkg: String) = appDao.get(pkg)
    suspend fun setBlocked(pkg: String, label: String, blocked: Boolean) {
        if (blocked) appDao.upsert((appDao.get(pkg) ?: BlockedApp(packageName = pkg, label = label)).copy(isStudyApp = false))
        else appDao.delete(pkg)
    }
    /** One-time starter Vault (v102): locks games and time-sink apps, marks PW as a study app. Skips apps already in the Vault. */
    suspend fun seedStarterVault(labels: Map<String, String>) {
        val lock = listOf("com.JindoBlu.TwoPlayerGamesChallenge", "com.supercell.clashofclans", "com.ohbibi.fps", "com.freefire.biocodes",
            "org.telegram.messenger", "com.radio.pocketfm", "in.startv.hotstar", "com.pinterest", "com.snaptube.premium",
            "com.instantbits.cast.webvideo", "org.torproject.torbrowser")
        for (pkg in lock) { val l = labels[pkg] ?: continue; if (appDao.get(pkg) == null) appDao.upsert(BlockedApp(packageName = pkg, label = l)) }
        val coding = listOf("com.haibison.apksigner", "com.anthropic.claude", "com.termux", "com.termux.api", "ru.zdevs.zarchiver",
            "com.openai.chatgpt", "com.github.android", "ru.iiec.pydroid3", "com.tomminosoftware.sqliteeditor")
        for (pkg in coding) { val l = labels[pkg] ?: continue; if (appDao.get(pkg) == null) appDao.upsert(BlockedApp(packageName = pkg, label = l, priceMult = 50)) }
        val pw = "xyz.penpencil.physicswala"
        labels[pw]?.let { if (appDao.get(pw) == null) appDao.upsert(BlockedApp(packageName = pw, label = it, isStudyApp = true)) }
    }
    /** v106 study-first: fun apps in the Vault cost double. Coding stays at half price. */
    suspend fun makeFunExpensive() {
        listOf("com.JindoBlu.TwoPlayerGamesChallenge", "com.supercell.clashofclans", "com.ohbibi.fps", "com.freefire.biocodes",
            "org.telegram.messenger", "com.radio.pocketfm", "in.startv.hotstar", "com.pinterest", "com.snaptube.premium",
            "com.instantbits.cast.webvideo", "org.torproject.torbrowser").forEach { pkg ->
            appDao.get(pkg)?.let { appDao.upsert(it.copy(priceMult = 200)) }
        }
    }
    suspend fun setStudyApp(pkg: String, label: String, isStudy: Boolean) {
        if (isStudy) appDao.upsert((appDao.get(pkg) ?: BlockedApp(packageName = pkg, label = label))
            .copy(isStudyApp = true, unlockedUntil = 0L))
        else appDao.delete(pkg)
    }
    suspend fun setAppOptions(pkg: String, dailyLimitMin: Int, schedStart: Int, schedEnd: Int, priceMult: Int, ratePer5: Int) {
        val app = appDao.get(pkg) ?: return
        appDao.upsert(app.copy(dailyLimitMin = dailyLimitMin, schedStart = schedStart,
            schedEnd = schedEnd, priceMult = priceMult, ratePer5 = ratePer5.coerceAtLeast(0)))
    }

    suspend fun addUsage(pkg: String, ms: Long): Boolean = db.withTransaction {
        val app = appDao.get(pkg) ?: return@withTransaction false
        val today = dayNumber(System.currentTimeMillis())
        val used = (if (app.usedDay == today) app.usedTodayMs else 0L) + ms
        appDao.upsert(app.copy(usedTodayMs = used, usedDay = today))
        app.dailyLimitMin > 0 && used >= app.dailyLimitMin * 60_000L
    }

    private val lastPaidAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Buys [blocks] × 5 minutes of access. Cost = blocks × rate.
     * Also opens an UnlockSession to track actual time spent.
     * Returns true on success.
     */
    private val unlockMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun unlock(pkg: String, blocks: Int): UnlockResult = unlockMutex.withLock {
        if (pkg.startsWith("site:")) return@withLock unlockSite(pkg.removePrefix("site:"), blocks)
        if (pkg.startsWith("short:")) return@withLock unlockSite(pkg, blocks)
        val b = blocks.coerceIn(1, MAX_UNLOCK_BLOCKS)
        val up = android.os.SystemClock.elapsedRealtime()
        var label = ""
        var result = UnlockResult(ok = false)
        // CRITICAL part: take coins + save unlock time. Nothing optional lives in here.
        val pct = routineSurchargePct()
        db.withTransaction {
            val app = appDao.get(pkg) ?: error("$pkg is not in the blocked list")
            val prev = lastPaidAt[pkg]
            if (prev != null && up - prev < DOUBLE_PAY_WINDOW_MS && app.unlockedUntil > System.currentTimeMillis()) {
                com.krishna.questblock.util.DebugLog.d("repo.unlock: double-pay guard hit, NOT charging")
                result = UnlockResult(ok = true, charged = false)
                return@withTransaction
            }
            val p = playerDao.get() ?: Player().also { playerDao.upsert(it) }
            val cost = withSurcharge(unlockCost(b, prefs.ratePer5, app), pct)
            // atomic UPDATE ... WHERE coins >= cost: no read-modify-write, can't be overwritten by a stale copy
            com.krishna.questblock.util.DebugLog.d("repo.unlock: pkg=$pkg blocks=$b cost=$cost coins=${p.coins}")
            if (playerDao.spend(cost) == 0) { result = UnlockResult(ok = false, cost = cost, before = p.coins, after = p.coins); return@withTransaction }
            val now = System.currentTimeMillis()
            val msBought = b * 5 * 60_000L
            appDao.upsert(app.copy(unlockedUntil = maxOf(now, app.unlockedUntil) + msBought))
            unlockEventDao.insert(UnlockEvent(packageName = app.packageName, label = app.label, coinsSpent = cost))
            logCoin(-cost, "Unlock", "${app.label} \u00b7 ${fmtBlocks(b)}")
            lastPaidAt[pkg] = up
            label = app.label
            result = UnlockResult(ok = true, charged = true, cost = cost, before = p.coins, after = p.coins - cost, minutes = b * 5, msBought = msBought)
        }
        // Re-read the real balance so the confirmation shows what is actually stored.
        if (result.ok && result.charged) {
            val real = playerDao.get()?.coins ?: result.after
            result = result.copy(after = real)
            runCatching {
                closeUnlockSession(pkg)
                unlockSessionDao.insert(UnlockSession(
                    packageName = pkg, label = label,
                    startedAt = System.currentTimeMillis(), msBought = result.msBought, coinsSpent = result.cost,
                ))
            }.onFailure { android.util.Log.e("QuestBlock", "unlock session tracking failed", it) }
        }
        result
    }

    /** Same coin price as an app (blocks of 5 minutes at the global rate). Websites have no database row, the open time lives in Prefs. */
    private suspend fun unlockSite(domain: String, blocks: Int): UnlockResult {
        val b = blocks.coerceIn(1, MAX_UNLOCK_BLOCKS)
        var result = UnlockResult(ok = false)
        val pct = routineSurchargePct()
        db.withTransaction {
            val p = playerDao.get() ?: Player().also { playerDao.upsert(it) }
            val cost = withSurcharge(unlockCost(b, prefs.ratePer5, null), pct)
            if (playerDao.spend(cost) == 0) {
                result = UnlockResult(ok = false, cost = cost, before = p.coins, after = p.coins)
                return@withTransaction
            }
            val now = System.currentTimeMillis()
            val ms = b * 5 * 60_000L
            prefs.setSiteUnlockedUntil(domain, maxOf(now, prefs.siteUnlockedUntil(domain)) + ms)
            logCoin(-cost, "Unlock", "${pseudoLabel(domain) ?: domain} \u00b7 ${fmtBlocks(b)}")
            result = UnlockResult(ok = true, charged = true, cost = cost, before = p.coins, after = p.coins - cost, minutes = b * 5, msBought = ms)
        }
        return result
    }

    // ── Unlock session tracking (v17) ────────────────────────────────────

    /** Call from the accessibility service when the user enters an unlocked blocked app. */
    suspend fun onEnterUnlockedApp(pkg: String) {
        // persist entry time in prefs for crash recovery
        prefs.activeUnlockPkg = pkg
        prefs.activeUnlockEnteredAt = System.currentTimeMillis()
    }

    /** Call from the accessibility service when the user leaves an unlocked blocked app (or it locks). */
    suspend fun onLeaveUnlockedApp(pkg: String) {
        val enteredAt = prefs.activeUnlockEnteredAt
        val activePkg = prefs.activeUnlockPkg
        if (activePkg != pkg || enteredAt <= 0L) return
        prefs.activeUnlockPkg = ""
        prefs.activeUnlockEnteredAt = 0L
        val spent = System.currentTimeMillis() - enteredAt
        if (spent <= 0L) return
        // Find the open session and accumulate
        val session = unlockSessionDao.getOpenSession(pkg) ?: return
        val totalMs = session.msUsed + spent
        unlockSessionDao.updateMsUsed(session.id, totalMs)
    }

    /** Close any open session for [pkg] and record endedAt. */
    private suspend fun closeUnlockSession(pkg: String) {
        val session = unlockSessionDao.getOpenSession(pkg) ?: return
        // If there's an in-progress visit, flush it first
        val enteredAt = prefs.activeUnlockEnteredAt
        val activePkg = prefs.activeUnlockPkg
        val now = System.currentTimeMillis()
        val extraMs = if (activePkg == pkg && enteredAt > 0L) (now - enteredAt).coerceAtLeast(0L) else 0L
        unlockSessionDao.close(session.id, now, session.msUsed + extraMs)
        if (activePkg == pkg) {
            prefs.activeUnlockPkg = ""
            prefs.activeUnlockEnteredAt = 0L
        }
    }

    /** Recover partial session after app/service restart. Call from service onConnected. */
    suspend fun recoverUnlockSessions() {
        val now = System.currentTimeMillis()
        // Close any sessions whose unlockedUntil has already passed
        unlockSessionDao.getAllOpen().forEach { session ->
            val app = appDao.get(session.packageName)
            if (app == null || app.unlockedUntil <= now) {
                // Recover time from prefs if this was the active session
                val extraMs = if (prefs.activeUnlockPkg == session.packageName && prefs.activeUnlockEnteredAt > 0L)
                    (now - prefs.activeUnlockEnteredAt).coerceAtLeast(0L) else 0L
                unlockSessionDao.close(session.id, now, session.msUsed + extraMs)
            }
        }
        // Reset stale prefs
        val activePkg = prefs.activeUnlockPkg
        if (activePkg.isNotEmpty()) {
            val app = appDao.get(activePkg)
            if (app == null || app.unlockedUntil <= now) {
                prefs.activeUnlockPkg = ""
                prefs.activeUnlockEnteredAt = 0L
            }
        }
    }

    // ── Study coins ───────────────────────────────────────────────────────
    suspend fun studyAppsOnce(): List<BlockedApp> = appDao.studyAppsOnce()

    /** Unlock carry-over: move an app's unlock deadline (used when the user comes back to it). */
    suspend fun setUnlockedUntil(pkg: String, until: Long) {
        db.withTransaction {
            val a = appDao.get(pkg) ?: return@withTransaction
            appDao.upsert(a.copy(unlockedUntil = until))
        }
    }

    suspend fun startStudySession(pkg: String, label: String) = studySessionDao.insert(StudySession(packageName = pkg, label = label))
    suspend fun creditStudyCoins(sessionId: Long, minutes: Int, label: String = "") {
        db.withTransaction {
            val p = playerDao.get() ?: Player()
            val earned = ((minutes / prefs.studyMinPerCoin) * (if (p.hasCoinMultiplier()) 1.5 else 1.0)).roundToInt()
            if (earned > 0) {
                playerDao.upsert(p.copy(coins = p.coins + earned))
                studySessionDao.addEarned(sessionId, earned)
                logCoin(earned, "Study", label)
            }
        }
    }

    // ── Shop / Inventory ─────────────────────────────────────────────────
    /** A streak can be repaired when it broke within the last 3 days and no shield is running. */
    suspend fun canRepairStreak(): Boolean {
        val p = playerDao.get() ?: return false
        val today = dayNumber(System.currentTimeMillis())
        return p.streak > 0 && !p.hasStreakShield() && p.lastCompletionDay < today - 1 && p.lastCompletionDay >= today - 3
    }

    // ── Focus breaks ─────────────────────────────────────────────────────
    fun breakUntil(): Long = prefs.breakUntil
    fun keepScreenAwakeInFocus(): Boolean = prefs.optFocusAwake
    fun breakIsLong(): Boolean = prefs.breakIsLong
    fun skipBreak() { prefs.breakUntil = 0L }

    suspend fun buyItem(item: ShopItem): Boolean = db.withTransaction {
        val p = playerDao.get() ?: Player()
        if (p.coins < item.price) return@withTransaction false
        playerDao.upsert(p.copy(coins = p.coins - item.price))
        logCoin(-item.price, "Shop", item.name)
        when (item.category) {
            "powerup" -> {
                val now = System.currentTimeMillis(); val expiry = now + item.durationMs
                val updated = when (item.id) {
                    "xp_boost_1h","xp_boost_3h"  -> p.copy(xpBoostUntil = expiry, coins = p.coins - item.price)
                    "coin_mult_1h","coin_mult_3h" -> p.copy(coinMultiplierUntil = expiry, coins = p.coins - item.price)
                    "streak_shield"               -> p.copy(streakShieldUntil = expiry, coins = p.coins - item.price)
                    "streak_freeze"               -> { prefs.streakFreezes = prefs.streakFreezes + 1; p.copy(coins = p.coins - item.price) }
                    // Streak Repair: pretend yesterday was done, so the next finished quest carries the old streak on
                    "streak_repair"               -> p.copy(lastCompletionDay = dayNumber(now) - 1, coins = p.coins - item.price)
                    else -> p.copy(coins = p.coins - item.price)
                }
                playerDao.upsert(updated)
                inventoryDao.insert(InventoryItem(itemId = item.id, category = item.category, name = item.name, emoji = item.emoji, usedAt = now))
            }
            else -> inventoryDao.insert(InventoryItem(itemId = item.id, category = item.category, name = item.name, emoji = item.emoji))
        }
        true
    }

    suspend fun addCustomReward(name: String, emoji: String, price: Int, description: String, type: String, oncePerDay: Boolean) {
        customRewardDao.insert(CustomReward(name = name.trim(), emoji = emoji.trim().ifBlank { "🎁" },
            price = price.coerceAtLeast(1), description = description.trim(), type = type, oncePerDay = oncePerDay))
    }
    suspend fun updateCustomReward(r: CustomReward) = customRewardDao.update(r.copy(
        name = r.name.trim(), emoji = r.emoji.trim().ifBlank { "🎁" }, price = r.price.coerceAtLeast(1),
        description = r.description.trim()))
    suspend fun deleteCustomReward(r: CustomReward) = customRewardDao.delete(r)

    suspend fun buyCustomReward(r: CustomReward): Int = db.withTransaction {
        val p = playerDao.get() ?: Player()
        if (r.oncePerDay && inventoryDao.countSince("custom_${r.id}", startOfToday()) > 0) return@withTransaction 2
        if (p.coins < r.price) return@withTransaction 1
        playerDao.upsert(p.copy(coins = p.coins - r.price))
        logCoin(-r.price, "Gift for me", r.name)
        inventoryDao.insert(InventoryItem(
            itemId = "custom_${r.id}", category = "custom", name = r.name, emoji = r.emoji,
            usedAt = if (r.type == "reward") System.currentTimeMillis() else null,
        ))
        0
    }
    suspend fun redeemItem(inv: InventoryItem) =
        inventoryDao.update(inv.copy(usedAt = System.currentTimeMillis()))

    // ── Settings helpers ─────────────────────────────────────────────────
    suspend fun resetAll() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        prefs.clearAll()
    }

    suspend fun unequipItem(inv: InventoryItem) = db.withTransaction {
        inventoryDao.unequipAll(inv.category)
        val p = playerDao.get() ?: Player()
        playerDao.upsert(when (inv.category) {
            "cosmetic_frame" -> p.copy(equippedFrame = "")
            "cosmetic_theme" -> p.copy(equippedTheme = "default")
            "cosmetic_badge" -> p.copy(equippedBadge = "none")
            else -> p
        })
    }

    suspend fun equipItem(inv: InventoryItem) = db.withTransaction {
        if (inv.id == -1L) {   // the free Beginner badge is not an inventory row
            inventoryDao.unequipAll("cosmetic_badge")
            playerDao.upsert((playerDao.get() ?: Player()).copy(equippedBadge = "badge_beginner"))
            return@withTransaction
        }
        inventoryDao.unequipAll(inv.category); inventoryDao.equip(inv.id)
        val p = playerDao.get() ?: Player()
        playerDao.upsert(when (inv.category) {
            "cosmetic_frame" -> p.copy(equippedFrame = inv.itemId)
            "cosmetic_theme" -> p.copy(equippedTheme = inv.itemId)
            "cosmetic_badge" -> p.copy(equippedBadge = inv.itemId)
            else -> p
        })
    }
}
