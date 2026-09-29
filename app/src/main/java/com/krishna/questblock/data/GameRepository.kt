package com.krishna.questblock.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

fun baseReward(difficulty: Int): Int = when (difficulty) { 1 -> 10; 2 -> 25; else -> 50 }
private fun dayNumber(now: Long): Long = (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L

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
) {
    val tasks: Flow<List<TaskEntity>> = taskDao.observeAll()
    val blockedApps: Flow<List<BlockedApp>> = appDao.observeAll()
    val recentUnlocks: Flow<List<UnlockEvent>> = unlockEventDao.observeRecent()
    val totalCoinsSpent: Flow<Int?> = unlockEventDao.observeTotalSpent()
    val inventory: Flow<List<InventoryItem>> = inventoryDao.observeAll()
    val studySessions: Flow<List<StudySession>> = studySessionDao.observeRecent()
    val totalStudyCoins: Flow<Int?> = studySessionDao.observeTotalEarned()
    val achievements: Flow<List<Achievement>> = achievementDao.observeAll()

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
    ): Long = taskDao.insert(
        TaskEntity(title = title.trim(), difficulty = difficulty, category = category,
            priority = priority, notes = notes, dueAt = dueAt,
            isRecurring = isRecurring, recurType = recurType,
            linkedApps = linkedApps, isTimerQuest = isTimerQuest, timerDurationMs = timerDurationMs)
    )

    suspend fun addSubTask(taskId: Long, title: String) =
        subTaskDao.insert(SubTask(taskId = taskId, title = title))

    suspend fun toggleSubTask(sub: SubTask) = subTaskDao.update(sub.copy(done = !sub.done))

    suspend fun deleteTask(task: TaskEntity) {
        subTaskDao.deleteAll(task.id)
        taskDao.delete(task)
    }

    suspend fun completeTask(task: TaskEntity) {
        db.withTransaction {
            val fresh = taskDao.get(task.id) ?: return@withTransaction
            if (fresh.done) return@withTransaction
            val now = System.currentTimeMillis()
            taskDao.update(fresh.copy(done = true, completedAt = now, timerQuestActive = false))

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

            playerDao.upsert(p.copy(
                coins = p.coins + earned, xp = p.xp + xpEarned,
                streak = newStreak, lastCompletionDay = today,
                weeklyXpLog = log.joinToString(","),
            ))
            checkAchievements(p.copy(xp = p.xp + xpEarned, streak = newStreak))

            // If recurring, re-create for next period
            if (fresh.isRecurring && fresh.dueAt != null) {
                val offset = if (fresh.recurType == "weekly") 7 * 86_400_000L else 86_400_000L
                taskDao.insert(fresh.copy(id = 0, done = false, completedAt = null,
                    dueAt = fresh.dueAt + offset, createdAt = now))
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

    // ── Focus Mode ────────────────────────────────────────────────────────
    suspend fun startFocusMode(durationMs: Long, questId: Long = -1L) {
        val p = playerDao.get() ?: Player()
        playerDao.upsert(p.copy(focusModeUntil = System.currentTimeMillis() + durationMs, focusQuestId = questId))
        // unlock achievement
        val catalog = ACHIEVEMENTS_CATALOG.find { it.id == "focus_1" } ?: return
        val ex = achievementDao.get("focus_1")
        if (ex?.unlockedAt == null) achievementDao.upsert(catalog.copy(unlockedAt = System.currentTimeMillis()))
    }

    suspend fun endFocusMode() {
        val p = playerDao.get() ?: Player()
        playerDao.upsert(p.copy(focusModeUntil = 0L, focusQuestId = -1L))
    }

    // ── Timer Quest ───────────────────────────────────────────────────────
    suspend fun startTimerQuest(task: TaskEntity) {
        taskDao.update(task.copy(timerQuestActive = true, timerQuestStarted = System.currentTimeMillis()))
        // Unlock linked apps for quest duration
        val pkgs = task.linkedApps.split(",").filter { it.isNotBlank() }
        val durationMs = if (task.timerDurationMs > 0) task.timerDurationMs else 60 * 60_000L
        pkgs.forEach { pkg ->
            val app = appDao.get(pkg)
            if (app != null) appDao.upsert(app.copy(unlockedUntil = System.currentTimeMillis() + durationMs))
        }
    }

    suspend fun stopTimerQuest(task: TaskEntity) {
        taskDao.update(task.copy(timerQuestActive = false))
        // Re-lock linked apps
        val pkgs = task.linkedApps.split(",").filter { it.isNotBlank() }
        pkgs.forEach { pkg ->
            val app = appDao.get(pkg)
            if (app != null) appDao.upsert(app.copy(unlockedUntil = 0L))
        }
    }

    // ── Apps ──────────────────────────────────────────────────────────────
    suspend fun getBlockedApp(pkg: String) = appDao.get(pkg)
    suspend fun setBlocked(pkg: String, label: String, blocked: Boolean) {
        if (blocked) appDao.upsert(appDao.get(pkg) ?: BlockedApp(packageName = pkg, label = label))
        else appDao.delete(pkg)
    }
    suspend fun setStudyApp(pkg: String, label: String, isStudy: Boolean) {
        val ex = appDao.get(pkg)
        if (isStudy) appDao.upsert((ex ?: BlockedApp(pkg, label)).copy(isStudyApp = true))
        else if (ex != null) appDao.upsert(ex.copy(isStudyApp = false))
    }
    suspend fun updateRule(app: BlockedApp, cost: Int, minutes: Int) =
        appDao.upsert(app.copy(cost = cost, unlockMinutes = minutes))
    suspend fun unlock(pkg: String): Boolean = db.withTransaction {
        val app = appDao.get(pkg) ?: return@withTransaction false
        val p = playerDao.get() ?: Player()
        if (p.coins < app.cost) return@withTransaction false
        playerDao.upsert(p.copy(coins = p.coins - app.cost))
        appDao.upsert(app.copy(unlockedUntil = System.currentTimeMillis() + app.unlockMinutes * 60_000L))
        unlockEventDao.insert(UnlockEvent(packageName = app.packageName, label = app.label, coinsSpent = app.cost))
        true
    }

    // ── Study coins ───────────────────────────────────────────────────────
    suspend fun startStudySession(pkg: String, label: String) = studySessionDao.insert(StudySession(packageName = pkg, label = label))
    suspend fun creditStudyCoins(sessionId: Long, minutes: Int) {
        db.withTransaction {
            val p = playerDao.get() ?: Player()
            val earned = ((minutes / 5) * (if (p.hasCoinMultiplier()) 1.5 else 1.0)).roundToInt()
            if (earned > 0) playerDao.upsert(p.copy(coins = p.coins + earned))
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
