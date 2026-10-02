package com.krishna.questblock.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done ASC, CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun get(id: Long): TaskEntity?
    @Insert suspend fun insert(task: TaskEntity): Long
    @Update suspend fun update(task: TaskEntity)
    @Delete suspend fun delete(task: TaskEntity)
    @Query("SELECT * FROM tasks WHERE isTimerQuest = 1 AND timerQuestActive = 1 LIMIT 1")
    suspend fun getActiveTimerQuest(): TaskEntity?
    @Query("SELECT * FROM tasks WHERE done = 0 AND dueAt IS NOT NULL AND dueAt < :now")
    suspend fun getOverdue(now: Long): List<TaskEntity>
}

@Dao interface SubTaskDao {
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId") fun observe(taskId: Long): Flow<List<SubTask>>
    @Query("SELECT * FROM subtasks") fun observeAll(): Flow<List<SubTask>>
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY id ASC") suspend fun getAll(taskId: Long): List<SubTask>
    @Insert suspend fun insert(sub: SubTask): Long
    @Update suspend fun update(sub: SubTask)
    @Delete suspend fun delete(sub: SubTask)
    @Query("DELETE FROM subtasks WHERE taskId = :taskId") suspend fun deleteAll(taskId: Long)
}

@Dao interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps ORDER BY label COLLATE NOCASE") fun observeAll(): Flow<List<BlockedApp>>
    @Query("SELECT * FROM blocked_apps WHERE isStudyApp = 1") fun observeStudyApps(): Flow<List<BlockedApp>>
    @Query("SELECT * FROM blocked_apps WHERE packageName = :pkg") suspend fun get(pkg: String): BlockedApp?
    @Upsert suspend fun upsert(app: BlockedApp)
    @Query("DELETE FROM blocked_apps WHERE packageName = :pkg") suspend fun delete(pkg: String)
}

@Dao interface PlayerDao {
    @Query("SELECT * FROM player WHERE id = 1") fun observe(): Flow<Player?>
    @Query("SELECT * FROM player WHERE id = 1") suspend fun get(): Player?
    @Upsert suspend fun upsert(player: Player)
    /** Atomic: subtract coins only if the balance covers it. Returns rows changed (0 = not enough). */
    @Query("UPDATE player SET coins = coins - :cost WHERE id = 1 AND coins >= :cost") suspend fun spend(cost: Int): Int
}

@Dao interface UnlockEventDao {
    @Insert suspend fun insert(event: UnlockEvent)
    @Query("SELECT * FROM unlock_events ORDER BY timestamp DESC LIMIT 100") fun observeRecent(): Flow<List<UnlockEvent>>
    @Query("SELECT SUM(coinsSpent) FROM unlock_events") fun observeTotalSpent(): Flow<Int?>
}

@Dao interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY acquiredAt DESC") fun observeAll(): Flow<List<InventoryItem>>
    @Query("SELECT * FROM inventory_items WHERE itemId = :itemId LIMIT 1") suspend fun getByItemId(itemId: String): InventoryItem?
    @Query("SELECT COUNT(*) FROM inventory_items WHERE itemId = :itemId AND acquiredAt >= :since") suspend fun countSince(itemId: String, since: Long): Int
    @Insert suspend fun insert(item: InventoryItem): Long
    @Update suspend fun update(item: InventoryItem)
    @Query("UPDATE inventory_items SET isEquipped = 0 WHERE category = :category") suspend fun unequipAll(category: String)
    @Query("UPDATE inventory_items SET isEquipped = 1 WHERE id = :id") suspend fun equip(id: Long)
}

@Dao interface StudySessionDao {
    @Insert suspend fun insert(session: StudySession): Long
    @Update suspend fun update(session: StudySession)
    @Query("SELECT * FROM study_sessions ORDER BY startedAt DESC LIMIT 50") fun observeRecent(): Flow<List<StudySession>>
    @Query("SELECT SUM(coinsEarned) FROM study_sessions") fun observeTotalEarned(): Flow<Int?>
    @Query("SELECT * FROM study_sessions WHERE endedAt IS NULL LIMIT 1") suspend fun getActiveSession(): StudySession?
    @Query("UPDATE study_sessions SET coinsEarned = coinsEarned + :coins WHERE id = :id") suspend fun addEarned(id: Long, coins: Int)
}

/** v17: unlock session DAO — tracks real time spent inside unlocked apps. */
@Dao interface UnlockSessionDao {
    @Insert suspend fun insert(session: UnlockSession): Long
    @Update suspend fun update(session: UnlockSession)
    @Query("SELECT * FROM unlock_sessions ORDER BY startedAt DESC LIMIT 200") fun observeRecent(): Flow<List<UnlockSession>>
    @Query("SELECT * FROM unlock_sessions WHERE packageName = :pkg AND endedAt IS NULL LIMIT 1")
    suspend fun getOpenSession(pkg: String): UnlockSession?
    @Query("SELECT * FROM unlock_sessions WHERE endedAt IS NULL")
    suspend fun getAllOpen(): List<UnlockSession>
    @Query("UPDATE unlock_sessions SET msUsed = :ms WHERE id = :id")
    suspend fun updateMsUsed(id: Long, ms: Long)
    @Query("UPDATE unlock_sessions SET endedAt = :endedAt, msUsed = :ms WHERE id = :id")
    suspend fun close(id: Long, endedAt: Long, ms: Long)
}

@Dao interface CustomRewardDao {
    @Query("SELECT * FROM custom_rewards ORDER BY createdAt DESC") fun observeAll(): Flow<List<CustomReward>>
    @Insert suspend fun insert(r: CustomReward): Long
    @Update suspend fun update(r: CustomReward)
    @Delete suspend fun delete(r: CustomReward)
}

@Dao interface FocusLogDao {
    @Insert suspend fun insert(l: FocusLog): Long
    @Query("SELECT * FROM focus_log ORDER BY startedAt DESC LIMIT 500") fun observeRecent(): Flow<List<FocusLog>>
}

@Dao interface CoinLogDao {
    @Insert suspend fun insert(l: CoinLog): Long
    @Query("SELECT * FROM coin_log ORDER BY timestamp DESC, id DESC LIMIT 500") fun observeRecent(): Flow<List<CoinLog>>
}

@Dao interface AchievementDao {
    @Query("SELECT * FROM achievements") fun observeAll(): Flow<List<Achievement>>
    @Query("SELECT * FROM achievements WHERE id = :id") suspend fun get(id: String): Achievement?
    @Upsert suspend fun upsert(a: Achievement)
}
