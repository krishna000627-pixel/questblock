package com.krishna.questblock.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

fun baseReward(difficulty: Int): Int = when (difficulty) {
    1 -> 10
    2 -> 25
    else -> 50
}

private fun dayNumber(now: Long): Long =
    (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L

@Singleton
class GameRepository @Inject constructor(
    private val db: AppDatabase,
    private val taskDao: TaskDao,
    private val appDao: BlockedAppDao,
    private val playerDao: PlayerDao,
    private val unlockEventDao: UnlockEventDao,
    private val inventoryDao: InventoryDao,
    private val studySessionDao: StudySessionDao,
) {
    val tasks: Flow<List<TaskEntity>> = taskDao.observeAll()
    val blockedApps: Flow<List<BlockedApp>> = appDao.observeAll()
    val studyApps: Flow<List<BlockedApp>> = appDao.observeStudyApps()
    val recentUnlocks: Flow<List<UnlockEvent>> = unlockEventDao.observeRecent()
    val totalCoinsSpent: Flow<Int?> = unlockEventDao.observeTotalSpent()
    val inventory: Flow<List<InventoryItem>> = inventoryDao.observeAll()
    val studySessions: Flow<List<StudySession>> = studySessionDao.observeRecent()
    val totalStudyCoins: Flow<Int?> = studySessionDao.observeTotalEarned()

    val player: Flow<Player> = playerDao.observe().map { stored ->
        val p = stored ?: Player()
        val now = System.currentTimeMillis()
        // Reset streak if no completion yesterday (and no shield active)
        if (p.lastCompletionDay < dayNumber(now) - 1 && !p.hasStreakShield()) {
            p.copy(streak = 0)
        } else p
    }

    // ── Profile ───────────────────────────────────────────────────────────
    suspend fun saveProfile(name: String, goal: String, dob: String) {
        val p = playerDao.get() ?: Player()
        playerDao.upsert(p.copy(name = name.trim(), goal = goal.trim(), dob = dob.trim()))
    }

    // ── Tasks ─────────────────────────────────────────────────────────────
    suspend fun addTask(title: String, difficulty: Int) {
        taskDao.insert(TaskEntity(title = title.trim(), difficulty = difficulty))
    }

    suspend fun deleteTask(task: TaskEntity) = taskDao.delete(task)

    suspend fun completeTask(task: TaskEntity) {
        db.withTransaction {
            val fresh = taskDao.get(task.id) ?: return@withTransaction
            if (fresh.done) return@withTransaction
            val now = System.currentTimeMillis()
            taskDao.update(fresh.copy(done = true, completedAt = now))

            val p = playerDao.get() ?: Player()
            val today = dayNumber(now)
            val newStreak = when (p.lastCompletionDay) {
                today     -> p.streak
                today - 1 -> p.streak + 1
                else      -> 1
            }
            val base = baseReward(fresh.difficulty)
            val xpMulti = if (p.hasXpBoost()) 2.0 else 1.0
            val streakMulti = 1.0 + (newStreak - 1).coerceIn(0, 5) * 0.1
            val coinMulti = if (p.hasCoinMultiplier()) 1.5 else 1.0
            playerDao.upsert(
                p.copy(
                    coins = p.coins + (base * streakMulti * coinMulti).roundToInt(),
                    xp = p.xp + (base * xpMulti).roundToInt(),
                    streak = newStreak,
                    lastCompletionDay = today,
                )
            )
        }
    }

    // ── Apps (block / study) ───────────────────────────────────────────────
    suspend fun getBlockedApp(pkg: String): BlockedApp? = appDao.get(pkg)

    suspend fun setBlocked(pkg: String, label: String, blocked: Boolean) {
        if (blocked) appDao.upsert(appDao.get(pkg) ?: BlockedApp(packageName = pkg, label = label))
        else appDao.delete(pkg)
    }

    suspend fun setStudyApp(pkg: String, label: String, isStudy: Boolean) {
        val existing = appDao.get(pkg)
        if (isStudy) {
            appDao.upsert(
                (existing ?: BlockedApp(packageName = pkg, label = label))
                    .copy(isStudyApp = true)
            )
        } else {
            if (existing != null) appDao.upsert(existing.copy(isStudyApp = false))
        }
    }

    suspend fun updateRule(app: BlockedApp, cost: Int, minutes: Int) {
        appDao.upsert(app.copy(cost = cost, unlockMinutes = minutes))
    }

    suspend fun unlock(pkg: String): Boolean = db.withTransaction {
        val app = appDao.get(pkg) ?: return@withTransaction false
        val p = playerDao.get() ?: Player()
        if (p.coins < app.cost) return@withTransaction false
        playerDao.upsert(p.copy(coins = p.coins - app.cost))
        appDao.upsert(app.copy(unlockedUntil = System.currentTimeMillis() + app.unlockMinutes * 60_000L))
        unlockEventDao.insert(UnlockEvent(packageName = app.packageName, label = app.label, coinsSpent = app.cost))
        true
    }

    // ── Study sessions (5 min = 1 coin) ──────────────────────────────────
    suspend fun startStudySession(pkg: String, label: String): Long {
        val existing = studySessionDao.getActiveSession()
        if (existing != null) endStudySession(existing.id)
        return studySessionDao.insert(StudySession(packageName = pkg, label = label))
    }

    suspend fun endStudySession(sessionId: Long) {
        // Handled by StudyTimerService — just a hook for manual end
    }

    suspend fun creditStudyCoins(sessionId: Long, minutes: Int) {
        db.withTransaction {
            val p = playerDao.get() ?: Player()
            val coinsEarned = minutes / 5  // 1 coin per 5 min
            if (coinsEarned > 0) {
                val multi = if (p.hasCoinMultiplier()) 1.5 else 1.0
                val earned = (coinsEarned * multi).roundToInt()
                playerDao.upsert(p.copy(coins = p.coins + earned))
            }
        }
    }

    // ── Shop / Inventory ─────────────────────────────────────────────────
    suspend fun buyItem(item: ShopItem): Boolean = db.withTransaction {
        val p = playerDao.get() ?: Player()
        if (p.coins < item.price) return@withTransaction false
        playerDao.upsert(p.copy(coins = p.coins - item.price))
        // Apply power-ups immediately; cosmetics go to inventory
        when (item.category) {
            "powerup" -> applyPowerUp(p, item)
            else -> {
                inventoryDao.insert(
                    InventoryItem(
                        itemId = item.id,
                        category = item.category,
                        name = item.name,
                        emoji = item.emoji,
                    )
                )
            }
        }
        true
    }

    private suspend fun applyPowerUp(p: Player, item: ShopItem) {
        val now = System.currentTimeMillis()
        val expiry = now + item.durationMs
        val updated = when (item.id) {
            "xp_boost_1h", "xp_boost_3h"   -> p.copy(xpBoostUntil = expiry)
            "coin_mult_1h", "coin_mult_3h"  -> p.copy(coinMultiplierUntil = expiry)
            "streak_shield"                 -> p.copy(streakShieldUntil = expiry)
            else                            -> p
        }
        playerDao.upsert(updated)
        inventoryDao.insert(
            InventoryItem(
                itemId = item.id,
                category = item.category,
                name = item.name,
                emoji = item.emoji,
                usedAt = now,
            )
        )
    }

    suspend fun equipItem(inventoryItem: InventoryItem) = db.withTransaction {
        inventoryDao.unequipAll(inventoryItem.category)
        inventoryDao.equip(inventoryItem.id)
        val p = playerDao.get() ?: Player()
        val updated = when (inventoryItem.category) {
            "cosmetic_frame" -> p.copy(equippedFrame = inventoryItem.itemId)
            "cosmetic_theme" -> p.copy(equippedTheme = inventoryItem.itemId)
            "cosmetic_badge" -> p.copy(equippedBadge = inventoryItem.itemId)
            else             -> p
        }
        playerDao.upsert(updated)
    }
}
