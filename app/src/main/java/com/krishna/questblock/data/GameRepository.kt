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
    Achievement("first_quest",   "First Step",    "👣", "Complete your first quest"),
    Achievement("streak_3",      "On Fire",       "🔥", "3-day streak"),
    Achievement("streak_7",      "Week Warrior",  "⚔️", "7-day streak"),
    Achievement("streak_30",     "Legend",        "🏆", "30-day streak"),
    Achievement("quests_10",     "Adventurer",    "🗺️", "Complete 10 quests"),
    Achievement("quests_50",     "Hero",          "🦸", "Complete 50 quests"),
    Achievement("quests_100",    "Champion",      "👑", "Complete 100 quests"),
    Achievement("coins_500",     "Coin Hoarder",  "🪙", "Earn 500 coins total"),
    Achievement("level_5",       "Level 5",       "⭐", "Reach level 5"),
    Achievement("level_10",      "Level 10",      "🌟", "Reach level 10"),
    Achievement("focus_1",       "Focused",       "🎯", "Complete first Focus session"),
    Achievement("custom_shop_1", "Shopkeeper",    "🛍️", "Create your first custom reward"),
)

@Singleton
class GameRepository @Inject constructor(
    private val db: AppDatabase,
    private val taskDao: TaskDao,
    private val subTaskDao: SubTaskDao,
    private val appDao: BlockedAppDao,
    private val playerDao: PlayerDao,
    private val unlockSessionDao: UnlockSessionDao,
    private val studySessionDao: StudySessionDao,
    private val inventoryDao: InventoryDao,
    private val achievementDao: AchievementDao,
    private val customShopDao: CustomShopDao,
    private val customPurchaseDao: CustomPurchaseDao,
) {
    val tasks          = taskDao.observeAll()
    val blockedApps    = appDao.observeAll()
    val recentUnlocks  = unlockSessionDao.observeRecent()
    val totalSpent     = unlockSessionDao.observeTotalSpent()
    val inventory      = inventoryDao.observeAll()
    val studySessions  = studySessionDao.observeRecent()
    val totalStudyCoins= studySessionDao.observeTotalEarned()
    val achievements   = achievementDao.observeAll()
    val customShopItems= customShopDao.observeAll()
    val customPurchases= customPurchaseDao.observeRecent()

    val player: Flow<Player> = playerDao.observe().map { stored ->
        val p = stored ?: Player()
        val now = System.currentTimeMillis()
        if (p.lastCompletionDay < dayNumber(now) - 1 && !p.hasStreakShield())
            p.copy(streak = 0) else p
    }

    fun subTasks(taskId: Long) = subTaskDao.observe(taskId)

    // ── Daily login bonus ─────────────────────────────────────────────────
    suspend fun claimDailyLogin(): Boolean {
        val p = playerDao.get() ?: Player()
        val today = dayNumber(System.currentTimeMillis())
        if (p.lastLoginDay == today) return false
        playerDao.upsert(p.copy(coins = p.coins + 5, lastLoginDay = today,
            totalCoinsEarned = p.totalCoinsEarned + 5))
        return true
    }

    // ── Profile ───────────────────────────────────────────────────────────
    suspend fun saveProfile(name: String, goal: String, dob: String) {
        val p = playerDao.get() ?: Player()
        playerDao.upsert(p.copy(name = name.trim(), goal = goal.trim(), dob = dob.trim()))
    }

    // ── Tasks ─────────────────────────────────────────────────────────────
    suspend fun addTask(
        title: String, difficulty: Int, category: String = "Personal",
        priority: Int = 2, notes: String = "", dueAt: Long? = null,
        isRecurring: Boolean = false, recurType: String = "daily",
        linkedApps: String = "", isFocusQuest: Boolean = false,
        focusDurationMs: Long = 25 * 60_000L,
    ): Long {
        val id = taskDao.insert(TaskEntity(
            title = title.trim(), difficulty = difficulty, category = category,
            priority = priority, notes = notes, dueAt = dueAt,
            isRecurring = isRecurring, recurType = recurType,
            linkedApps = linkedApps, isFocusQuest = isFocusQuest,
            focusDurationMs = focusDurationMs,
        ))
        tryUnlock("first_quest")
        return id
    }

    suspend fun addSubTask(taskId: Long, title: String) =
        subTaskDao.insert(SubTask(taskId = taskId, title = title))
    suspend fun toggleSubTask(sub: SubTask) = subTaskDao.update(sub.copy(done = !sub.done))
    suspend fun deleteTask(task: TaskEntity) { subTaskDao.deleteAll(task.id); taskDao.delete(task) }
    suspend fun updateTask(task: TaskEntity) = taskDao.update(task)

    suspend fun completeTask(task: TaskEntity) = db.withTransaction {
        val fresh = taskDao.get(task.id) ?: return@withTransaction
        if (fresh.done) return@withTransaction
        val now = System.currentTimeMillis()
        taskDao.update(fresh.copy(done = true, completedAt = now, focusActive = false))

        val p = playerDao.get() ?: Player()
        val today = dayNumber(now)
        val newStreak = when (p.lastCompletionDay) {
            today -> p.streak; today - 1 -> p.streak + 1; else -> 1
        }
        val base = baseReward(fresh.difficulty)
        val streakMulti = 1.0 + (newStreak - 1).coerceIn(0, 5) * 0.1
        val coinMulti   = if (p.hasCoinMultiplier()) 1.5 else 1.0
        val xpMulti     = if (p.hasXpBoost() || p.isFocusMode()) 2.0 else 1.0
        val coins = (base * streakMulti * coinMulti).roundToInt()
        val xp    = (base * xpMulti).roundToInt()

        val log = p.weeklyXpLog.split(",").filter { it.isNotBlank() }.toMutableList()
        log.add("${dayNumber(now)}:$xp"); if (log.size > 70) log.removeAt(0)

        playerDao.upsert(p.copy(
            coins = p.coins + coins, xp = p.xp + xp,
            streak = newStreak, lastCompletionDay = today,
            totalCoinsEarned = p.totalCoinsEarned + coins,
            weeklyXpLog = log.joinToString(","),
        ))

        // Stop linked apps if focus quest
        if (fresh.isFocusQuest) {
            fresh.linkedApps.split(",").filter { it.isNotBlank() }.forEach { pkg ->
                val app = appDao.get(pkg)
                if (app != null) appDao.upsert(app.copy(unlockedUntil = 0L))
            }
        }

        checkAchievements(newStreak, p.xp + xp)
        if (fresh.isRecurring && fresh.dueAt != null) {
            val offset = if (fresh.recurType == "weekly") 7 * 86_400_000L else 86_400_000L
            taskDao.insert(fresh.copy(id = 0, done = false, completedAt = null,
                focusActive = false, focusStarted = null,
                dueAt = fresh.dueAt + offset, createdAt = now))
        }
    }

    private suspend fun checkAchievements(streak: Int, xp: Int) {
        if (streak >= 3) tryUnlock("streak_3")
        if (streak >= 7) tryUnlock("streak_7")
        if (streak >= 30) tryUnlock("streak_30")
        if ((xp / 100 + 1) >= 5) tryUnlock("level_5")
        if ((xp / 100 + 1) >= 10) tryUnlock("level_10")
    }

    private suspend fun tryUnlock(id: String) {
        val ex = achievementDao.get(id); if (ex?.unlockedAt != null) return
        val cat = ACHIEVEMENTS_CATALOG.find { it.id == id } ?: return
        achievementDao.upsert(cat.copy(unlockedAt = System.currentTimeMillis()))
    }

    // ── Focus quest (merged Timer+Focus) ──────────────────────────────────
    suspend fun startFocusQuest(task: TaskEntity) = db.withTransaction {
        val now = System.currentTimeMillis()
        taskDao.update(task.copy(focusActive = true, focusStarted = now))
        task.linkedApps.split(",").filter { it.isNotBlank() }.forEach { pkg ->
            val app = appDao.get(pkg) ?: BlockedApp(pkg, pkg)
            appDao.upsert(app.copy(unlockedUntil = now + task.focusDurationMs))
        }
    }

    suspend fun stopFocusQuest(task: TaskEntity) = db.withTransaction {
        taskDao.update(task.copy(focusActive = false, focusStarted = null))
        task.linkedApps.split(",").filter { it.isNotBlank() }.forEach { pkg ->
            val app = appDao.get(pkg); if (app != null) appDao.upsert(app.copy(unlockedUntil = 0L))
        }
    }

    suspend fun startGlobalFocus(durationMs: Long) {
        val p = playerDao.get() ?: Player()
        playerDao.upsert(p.copy(focusModeUntil = System.currentTimeMillis() + durationMs))
        tryUnlock("focus_1")
    }

    suspend fun endGlobalFocus() {
        val p = playerDao.get() ?: Player()
        // Bonus coins for completing focus
        playerDao.upsert(p.copy(focusModeUntil = 0L, focusQuestId = -1L,
            coins = p.coins + 15, totalCoinsEarned = p.totalCoinsEarned + 15))
    }

    // ── Blocked apps ──────────────────────────────────────────────────────
    suspend fun getBlockedApp(pkg: String) = appDao.get(pkg)

    suspend fun setBlocked(pkg: String, label: String, blocked: Boolean) {
        if (blocked) appDao.upsert(appDao.get(pkg) ?: BlockedApp(pkg, label))
        else appDao.delete(pkg)
    }

    suspend fun setStudyApp(pkg: String, label: String, study: Boolean) {
        val ex = appDao.get(pkg)
        if (study) appDao.upsert((ex ?: BlockedApp(pkg, label)).copy(isStudyApp = true))
        else if (ex != null) appDao.upsert(ex.copy(isStudyApp = false))
    }

    // Flexible unlock with chosen duration
    suspend fun unlockApp(pkg: String, label: String, option: UnlockOption): Boolean = db.withTransaction {
        val p = playerDao.get() ?: Player()
        if (p.coins < option.cost) return@withTransaction false
        val now = System.currentTimeMillis()
        val expiry = now + option.durationMs
        playerDao.upsert(p.copy(coins = p.coins - option.cost))
        val app = appDao.get(pkg) ?: BlockedApp(pkg, label)
        appDao.upsert(app.copy(unlockedUntil = expiry))
        unlockSessionDao.insert(UnlockSession(packageName = pkg, label = label,
            coinsSpent = option.cost, durationMs = option.durationMs, expiresAt = expiry))
        true
    }

    suspend fun isAppUnlocked(pkg: String): Boolean {
        val app = appDao.get(pkg) ?: return false
        return app.unlockedUntil > System.currentTimeMillis() || app.isStudyApp
    }

    // Study coins
    suspend fun creditStudyCoins(pkg: String, label: String, minutes: Int) = db.withTransaction {
        val p = playerDao.get() ?: Player()
        val earned = ((minutes / 5) * (if (p.hasCoinMultiplier()) 1.5 else 1.0)).roundToInt()
        if (earned > 0) {
            playerDao.upsert(p.copy(coins = p.coins + earned, totalCoinsEarned = p.totalCoinsEarned + earned))
            studySessionDao.insert(StudySession(packageName = pkg, label = label, coinsEarned = earned))
        }
    }

    // ── Shop / Inventory ─────────────────────────────────────────────────
    suspend fun buyBuiltInItem(item: ShopItem): Boolean = db.withTransaction {
        val p = playerDao.get() ?: Player()
        if (p.coins < item.price) return@withTransaction false
        val now = System.currentTimeMillis()
        val expiry = now + item.durationMs
        val updated = when (item.id) {
            "xp_boost_1h","xp_boost_3h"   -> p.copy(xpBoostUntil = expiry)
            "coin_mult_1h","coin_mult_3h"  -> p.copy(coinMultiplierUntil = expiry)
            "streak_shield"                -> p.copy(streakShieldUntil = expiry)
            else -> p
        }
        playerDao.upsert(updated.copy(coins = updated.coins - item.price))
        inventoryDao.insert(InventoryItem(itemId = item.id, category = item.category,
            name = item.name, emoji = item.emoji,
            usedAt = if (item.category == "powerup") now else null))
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

    // Custom shop
    suspend fun addCustomItem(name: String, emoji: String, desc: String, price: Int) {
        customShopDao.insert(CustomShopItem(name = name, emoji = emoji, description = desc, price = price))
        tryUnlock("custom_shop_1")
    }
    suspend fun deleteCustomItem(item: CustomShopItem) = customShopDao.delete(item)

    suspend fun buyCustomItem(item: CustomShopItem): Boolean = db.withTransaction {
        val p = playerDao.get() ?: Player()
        if (p.coins < item.price) return@withTransaction false
        playerDao.upsert(p.copy(coins = p.coins - item.price))
        customPurchaseDao.insert(CustomPurchase(itemId = item.id, itemName = item.name, coinsSpent = item.price))
        true
    }

    suspend fun redeemCustomPurchase(p: CustomPurchase) = customPurchaseDao.update(p.copy(redeemed = true))
}
