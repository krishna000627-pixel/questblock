package com.krishna.questblock.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)
}

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<BlockedApp>>

    @Query("SELECT * FROM blocked_apps WHERE isStudyApp = 1")
    fun observeStudyApps(): Flow<List<BlockedApp>>

    @Query("SELECT * FROM blocked_apps WHERE packageName = :pkg")
    suspend fun get(pkg: String): BlockedApp?

    @Upsert
    suspend fun upsert(app: BlockedApp)

    @Query("DELETE FROM blocked_apps WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

@Dao
interface PlayerDao {
    @Query("SELECT * FROM player WHERE id = 1")
    fun observe(): Flow<Player?>

    @Query("SELECT * FROM player WHERE id = 1")
    suspend fun get(): Player?

    @Upsert
    suspend fun upsert(player: Player)
}

@Dao
interface UnlockEventDao {
    @Insert
    suspend fun insert(event: UnlockEvent)

    @Query("SELECT * FROM unlock_events ORDER BY timestamp DESC LIMIT 100")
    fun observeRecent(): Flow<List<UnlockEvent>>

    @Query("SELECT SUM(coinsSpent) FROM unlock_events")
    fun observeTotalSpent(): Flow<Int?>
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY acquiredAt DESC")
    fun observeAll(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE itemId = :itemId LIMIT 1")
    suspend fun getByItemId(itemId: String): InventoryItem?

    @Insert
    suspend fun insert(item: InventoryItem): Long

    @Update
    suspend fun update(item: InventoryItem)

    @Query("UPDATE inventory_items SET isEquipped = 0 WHERE category = :category")
    suspend fun unequipAll(category: String)

    @Query("UPDATE inventory_items SET isEquipped = 1 WHERE id = :id")
    suspend fun equip(id: Long)
}

@Dao
interface StudySessionDao {
    @Insert
    suspend fun insert(session: StudySession): Long

    @Update
    suspend fun update(session: StudySession)

    @Query("SELECT * FROM study_sessions ORDER BY startedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<StudySession>>

    @Query("SELECT SUM(coinsEarned) FROM study_sessions")
    fun observeTotalEarned(): Flow<Int?>

    @Query("SELECT * FROM study_sessions WHERE endedAt IS NULL LIMIT 1")
    suspend fun getActiveSession(): StudySession?
}
