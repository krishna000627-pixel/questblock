package com.krishna.questblock.data

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
import kotlin.math.roundToInt

fun baseReward(difficulty: Int): Int = when (difficulty) { 1 -> 10; 2 -> 25; else -> 50 }
const val FOCUS_BONUS_COINS = 15
const val DAILY_LOGIN_COINS = 5

data class UnlockTier(val minutes: Int, val cost: Int, val label: String)
val UNLOCK_TIERS = listOf(
    UnlockTier(15, 20, "15m"),
    UnlockTier(30, 35, "30m"),
    UnlockTier(60, 60, "1h"),
    UnlockTier(180, 120, "3h"),
    UnlockTier(600, 300, "10h"),
)

/** Tier price after the per-app price multiplier (Vault → app options). */
fun UnlockTier.costFor(app: BlockedApp?): Int =
    ((cost * (app?.priceMult ?: 100)) / 100.0).roundToInt().coerceAtLeast(1)

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

    // ── Settings shortcuts ────────────────────────────────────────────────
    fun unlockTiers(): List<UnlockTier> = prefs.unlockTiers()
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

    // ── Profile ───────────────────────────────────────────────────────────
    suspend fun saveProfile(name: String, goal: String, dob: String) {
        val p = playerDao.get() ?: Player()
        playerDao.upsert(p.copy(name = name.trim(), goal = goal.trim(), dob = dob.trim()))
    }

    // ── Tasks ─────────────────────────────────────────────────────────────
    suspend fun addTask(
        title: String, difficulty: Int, category: String = "Personal",
        priority: Int = 2, notes: String = "", dueAt: Long? = null,
        isRecurring: Boolean = false, recurType: String = "",
        linkedApps: String = "", isTimerQuest: Boolean = false, timerDurationMs: Long = 0L,
        daysMask: Int = 0,
    ): Long {
        val id = taskDao.insert(
            TaskEntity(title = title.trim(), difficulty = difficulty, category = category,
                priority = priority, notes = notes, dueAt = dueAt,
                isRecurring = isRecurring || daysMask != 0, recurType = if (daysMask != 0) "days" else recurType,
                linkedApps = linkedApps, isTimerQuest = isTimerQuest, timerDurationMs = timerDurationMs,
                daysMask = daysMask)
        )
        taskDao.get(id)?.let { scheduleReminder(it) }
        return id
    }

    suspend fun getTask(id: Long): TaskEntity? = taskDao.get(id)

    suspend fun updateTask(
        id: Long, title: String, difficulty: Int, category: String, priority: Int,
        notes: String, dueAt: Long?, isRecurring: Boolean, recurType: String,
        linkedApps: String, isTimerQuest: Boolean, timerDurationMs: Long, daysMask: Int,
    ) {
        val t = taskDao.get(id) ?: return
        val updated = t.copy(
            title = title.trim(), difficulty = difficulty, category = category, priority = priority,
            notes = notes, dueAt = dueAt,
            isRecurring = isRecurring || daysMask != 0, recurType = if (daysMask != 0) "days" else recurType,
            linkedApps = linkedApps, isTimerQuest = isTimerQuest, timerDurationMs = timerDurationMs,
            daysMask = daysMask,
        )
        taskDao.update(updated)
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

    suspend fun toggleSubTask(sub: SubTask) = subTaskDao.update(sub.copy(done = !sub.done))

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
            // Scheduled quests can only be completed on their days (no farming ahead of time)
            if (!fresh.isAvailableNow(now)) return@withTransaction
            taskDao.update(fresh.copy(done = true, completedAt = now, timerQuestActive = false))
            cancelReminder(fresh.id)

            val p = playerDao.get() ?: Player()
            val today = dayNumber(now)
            val newStreak = when (p.lastCompletionDay) {
                today -> p.streak; today - 1 -> p.streak + 1; else -> 1
            }
            val base = baseReward(fresh.difficulty)
            val xpMulti  = if (p.hasXpBoost()) 2.0 else 1.0
            val coinMulti = if (p.hasCoinMultiplier()) 1.5 else 1.0
            val streakMulti = 1.0 + (newStreak - 1).coerceIn(0, 5) * 0.1
            val earned = (base * streakMulti * coinMulti).roundToInt()
            val xpEarned = (base * xpMulti).roundToInt()

            // Update weekly XP log
            val log = p.weeklyXpLog.split(",").filter { it.isNotBlank() }.toMutableList()
            val todayEntry = "$today:${xpEarned}"
            log.add(todayEntry)
            if (log.size > 70) log.removeAt(0)

            val wasFocusQuest = p.focusQuestId == fresh.id
            playerDao.upsert(p.copy(
                coins = p.coins + earned, xp = p.xp + xpEarned,
                streak = newStreak, lastCompletionDay = today,
                weeklyXpLog = log.joinToString(","),
                focusModeUntil = if (wasFocusQuest) 0L else p.focusModeUntil,
                focusQuestId   = if (wasFocusQuest) -1L else p.focusQuestId,
                focusTotalMs   = if (wasFocusQuest) 0L else p.focusTotalMs,
            ))
            checkAchievements(p.copy(xp = p.xp + xpEarned, streak = newStreak))

            // If recurring, re-create for next period
            if (fresh.daysMask != 0) {
                // Scheduled days: the next copy appears on the next selected day
                val nextDay = nextRunDayStart(fresh.daysMask, now)
                val nextDue = fresh.dueAt?.let { withTimeOfDay(nextDay, it) }
                val newId = taskDao.insert(fresh.copy(id = 0, done = false, completedAt = null,
                    dueAt = nextDue, availableFrom = nextDay, createdAt = now,
                    timerQuestActive = false, timerQuestStarted = null))
                taskDao.get(newId)?.let { scheduleReminder(it) }
            } else if (fresh.isRecurring && fresh.dueAt != null) {
                val offset = if (fresh.recurType == "weekly") 7 * 86_400_000L else 86_400_000L
                val newId = taskDao.insert(fresh.copy(id = 0, done = false, completedAt = null,
                    dueAt = fresh.dueAt + offset, createdAt = now))
                taskDao.get(newId)?.let { scheduleReminder(it) }
            }
        }
    }

    private suspend fun checkAchievements(p: Player) {
        val done = taskDao.observeAll() // just count from DB indirectly
        // We do a quick count query inline
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
    /** Daily login coins, once per calendar day. Returns the coins granted (0 = none / disabled). */
    suspend fun claimDailyLogin(): Int = db.withTransaction {
        if (!prefs.dailyLoginEnabled) return@withTransaction 0
        val today = dayNumber(System.currentTimeMillis())
        val p = playerDao.get() ?: Player()
        if (p.lastLoginDay >= today) return@withTransaction 0
        val coins = prefs.dailyLoginCoins
        playerDao.upsert(p.copy(coins = p.coins + coins, lastLoginDay = today))
        coins
    }

    // ── Focus (Focus Mode + Timer Quest are one thing now) ───────────────
    /**
     * Starts a focus session. If [task] is a timer quest, its linked apps are unlocked for the
     * session and the quest is auto-completed when the timer finishes.
     */
    suspend fun startFocus(durationMs: Long, task: TaskEntity? = null): Boolean = db.withTransaction {
        val now = System.currentTimeMillis()
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

    /** Ends the running session. [completed] = timer ran out (bonus + quest reward), false = stopped early. */
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
            ))
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
                // only re-lock if nobody bought a longer unlock in the meantime
                if (app != null && app.unlockedUntil <= end) appDao.upsert(app.copy(unlockedUntil = 0L))
            }
            taskDao.get(task.id)?.let { taskDao.update(it.copy(timerQuestActive = false)) }
        }
        if (completed) {
            unlockAchievement("focus_1")
            if (task != null) {
                unlockAchievement("timer_quest_1")
                if (!task.done) completeTask(task)
            }
        }
    }

    /** Call when the app opens / timer hits zero: closes an expired session and pays out. */
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
    suspend fun setStudyApp(pkg: String, label: String, isStudy: Boolean) {
        if (isStudy) appDao.upsert((appDao.get(pkg) ?: BlockedApp(packageName = pkg, label = label))
            .copy(isStudyApp = true, unlockedUntil = 0L))
        else appDao.delete(pkg)
    }
    suspend fun setAppOptions(pkg: String, dailyLimitMin: Int, schedStart: Int, schedEnd: Int, priceMult: Int) {
        val app = appDao.get(pkg) ?: return
        appDao.upsert(app.copy(dailyLimitMin = dailyLimitMin, schedStart = schedStart,
            schedEnd = schedEnd, priceMult = priceMult))
    }

    /** Adds foreground time for a daily-limit app. Returns true once today's limit is used up. */
    suspend fun addUsage(pkg: String, ms: Long): Boolean = db.withTransaction {
        val app = appDao.get(pkg) ?: return@withTransaction false
        val today = dayNumber(System.currentTimeMillis())
        val used = (if (app.usedDay == today) app.usedTodayMs else 0L) + ms
        appDao.upsert(app.copy(usedTodayMs = used, usedDay = today))
        app.dailyLimitMin > 0 && used >= app.dailyLimitMin * 60_000L
    }

    suspend fun unlock(pkg: String, tier: UnlockTier): Boolean = db.withTransaction {
        val app = appDao.get(pkg) ?: return@withTransaction false
        val p = playerDao.get() ?: Player()
        val cost = tier.costFor(app)
        if (p.coins < cost) return@withTransaction false
        val now = System.currentTimeMillis()
        playerDao.upsert(p.copy(coins = p.coins - cost))
        // stacking: extend from the current unlock if one is still running
        val base = maxOf(now, app.unlockedUntil)
        appDao.upsert(app.copy(unlockedUntil = base + tier.minutes * 60_000L))
        unlockEventDao.insert(UnlockEvent(packageName = app.packageName, label = app.label, coinsSpent = cost))
        true
    }

    // ── Study coins ───────────────────────────────────────────────────────
    suspend fun startStudySession(pkg: String, label: String) = studySessionDao.insert(StudySession(packageName = pkg, label = label))
    suspend fun creditStudyCoins(sessionId: Long, minutes: Int) {
        db.withTransaction {
            val p = playerDao.get() ?: Player()
            val earned = ((minutes / prefs.studyMinPerCoin) * (if (p.hasCoinMultiplier()) 1.5 else 1.0)).roundToInt()
            if (earned > 0) {
                playerDao.upsert(p.copy(coins = p.coins + earned))
                studySessionDao.addEarned(sessionId, earned)
            }
        }
    }

    // ── Shop / Inventory ─────────────────────────────────────────────────
    suspend fun buyItem(item: ShopItem): Boolean = db.withTransaction {
        val p = playerDao.get() ?: Player()
        if (p.coins < item.price) return@withTransaction false
        playerDao.upsert(p.copy(coins = p.coins - item.price))
        when (item.category) {
            "powerup" -> {
                val now = System.currentTimeMillis(); val expiry = now + item.durationMs
                val updated = when (item.id) {
                    "xp_boost_1h","xp_boost_3h"  -> p.copy(xpBoostUntil = expiry, coins = p.coins - item.price)
                    "coin_mult_1h","coin_mult_3h" -> p.copy(coinMultiplierUntil = expiry, coins = p.coins - item.price)
                    "streak_shield"               -> p.copy(streakShieldUntil = expiry, coins = p.coins - item.price)
                    else -> p.copy(coins = p.coins - item.price)
                }
                playerDao.upsert(updated)
                inventoryDao.insert(InventoryItem(itemId = item.id, category = item.category, name = item.name, emoji = item.emoji, usedAt = now))
            }
            else -> inventoryDao.insert(InventoryItem(itemId = item.id, category = item.category, name = item.name, emoji = item.emoji))
        }
        true
    }
    // ── Custom rewards (user-made shop items) ────────────────────────────
    suspend fun addCustomReward(name: String, emoji: String, price: Int, description: String, type: String, oncePerDay: Boolean) {
        customRewardDao.insert(CustomReward(name = name.trim(), emoji = emoji.trim().ifBlank { "🎁" },
            price = price.coerceAtLeast(1), description = description.trim(), type = type, oncePerDay = oncePerDay))
    }
    suspend fun updateCustomReward(r: CustomReward) = customRewardDao.update(r.copy(
        name = r.name.trim(), emoji = r.emoji.trim().ifBlank { "🎁" }, price = r.price.coerceAtLeast(1),
        description = r.description.trim()))
    suspend fun deleteCustomReward(r: CustomReward) = customRewardDao.delete(r)

    /** 0 = bought, 1 = not enough coins, 2 = already bought today (once-per-day rewards). */
    suspend fun buyCustomReward(r: CustomReward): Int = db.withTransaction {
        val p = playerDao.get() ?: Player()
        if (r.oncePerDay && inventoryDao.countSince("custom_${r.id}", startOfToday()) > 0) return@withTransaction 2
        if (p.coins < r.price) return@withTransaction 1
        playerDao.upsert(p.copy(coins = p.coins - r.price))
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

    suspend fun equipItem(inv: InventoryItem) = db.withTransaction {
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
