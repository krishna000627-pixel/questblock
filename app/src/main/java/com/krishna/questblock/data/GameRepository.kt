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
) {
    val tasks: Flow<List<TaskEntity>> = taskDao.observeAll()
    val blockedApps: Flow<List<BlockedApp>> = appDao.observeAll()
    val recentUnlocks: Flow<List<UnlockEvent>> = unlockEventDao.observeRecent()
    val totalCoinsSpent: Flow<Int?> = unlockEventDao.observeTotalSpent()

    val player: Flow<Player> = playerDao.observe().map { stored ->
        val p = stored ?: Player()
        if (p.lastCompletionDay < dayNumber(System.currentTimeMillis()) - 1) p.copy(streak = 0) else p
    }

    suspend fun addTask(title: String, difficulty: Int) {
        taskDao.insert(TaskEntity(title = title.trim(), difficulty = difficulty))
    }

    suspend fun deleteTask(task: TaskEntity) = taskDao.delete(task)

    suspend fun completeTask(task: TaskEntity) {
        db.withTransaction {
            val fresh = taskDao.get(task.id)
            if (fresh != null && !fresh.done) {
                val now = System.currentTimeMillis()
                taskDao.update(fresh.copy(done = true, completedAt = now))

                val p = playerDao.get() ?: Player()
                val today = dayNumber(now)
                val newStreak = when (p.lastCompletionDay) {
                    today      -> p.streak
                    today - 1  -> p.streak + 1
                    else       -> 1
                }
                val base = baseReward(fresh.difficulty)
                val multiplier = 1.0 + (newStreak - 1).coerceIn(0, 5) * 0.1
                playerDao.upsert(
                    p.copy(
                        coins = p.coins + (base * multiplier).roundToInt(),
                        xp = p.xp + base,
                        streak = newStreak,
                        lastCompletionDay = today,
                    )
                )
            }
        }
    }

    suspend fun getBlockedApp(pkg: String): BlockedApp? = appDao.get(pkg)

    suspend fun setBlocked(pkg: String, label: String, blocked: Boolean) {
        if (blocked) appDao.upsert(appDao.get(pkg) ?: BlockedApp(packageName = pkg, label = label))
        else appDao.delete(pkg)
    }

    suspend fun updateRule(app: BlockedApp, cost: Int, minutes: Int) {
        appDao.upsert(app.copy(cost = cost, unlockMinutes = minutes))
    }

    suspend fun unlock(pkg: String): Boolean = db.withTransaction {
        val app = appDao.get(pkg)
        val p = playerDao.get() ?: Player()
        if (app == null || p.coins < app.cost) {
            false
        } else {
            playerDao.upsert(p.copy(coins = p.coins - app.cost))
            appDao.upsert(app.copy(unlockedUntil = System.currentTimeMillis() + app.unlockMinutes * 60_000L))
            unlockEventDao.insert(UnlockEvent(packageName = app.packageName, label = app.label, coinsSpent = app.cost))
            true
        }
    }
}
