package com.krishna.questblock.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done ASC, CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt ASC, priority ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun get(id: Long): TaskEntity?
    @Insert suspend fun insert(task: TaskEntity): Long
    @Update suspend fun update(task: TaskEntity)
    @Delete suspend fun delete(task: TaskEntity)
}

@Dao interface SubTaskDao {
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId") fun observe(taskId: Long): Flow<List<SubTask>>
    @Insert suspend fun insert(sub: SubTask): Long
    @Update suspend fun update(sub: SubTask)
    @Delete suspend fun delete(sub: SubTask)
    @Query("DELETE FROM subtasks WHERE taskId = :taskId") suspend fun deleteAll(taskId: Long)
}

@Dao interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps ORDER BY isStudyApp DESC, label COLLATE NOCASE")
    fun observeAll(): Flow<List<BlockedApp>>
    @Query("SELECT * FROM blocked_apps WHERE packageName = :pkg") suspend fun get(pkg: String): BlockedApp?
    @Upsert suspend fun upsert(app: BlockedApp)
    @Query("DELETE FROM blocked_apps WHERE packageName = :pkg") suspend fun delete(pkg: String)
}

@Dao interface PlayerDao {
    @Query("SELECT * FROM player WHERE id = 1") fun observe(): Flow<Player?>
    @Query("SELECT * FROM player WHERE id = 1") suspend fun get(): Player?
    @Upsert suspend fun upsert(player: Player)
}

@Dao interface UnlockSessionDao {
    @Insert suspend fun insert(s: UnlockSession): Long
    @Query("SELECT * FROM unlock_sessions ORDER BY startedAt DESC LIMIT 50") fun observeRecent(): Flow<List<UnlockSession>>
    @Query("SELECT * FROM unlock_sessions WHERE packageName = :pkg ORDER BY expiresAt DESC LIMIT 1") suspend fun getLatest(pkg: String): UnlockSession?
    @Query("SELECT SUM(coinsSpent) FROM unlock_sessions") fun observeTotalSpent(): Flow<Int?>
}

@Dao interface StudySessionDao {
    @Insert suspend fun insert(s: StudySession): Long
    @Query("SELECT * FROM study_sessions ORDER BY startedAt DESC LIMIT 50") fun observeRecent(): Flow<List<StudySession>>
    @Query("SELECT SUM(coinsEarned) FROM study_sessions") fun observeTotalEarned(): Flow<Int?>
}

@Dao interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY acquiredAt DESC") fun observeAll(): Flow<List<InventoryItem>>
    @Insert suspend fun insert(item: InventoryItem): Long
    @Update suspend fun update(item: InventoryItem)
    @Query("UPDATE inventory_items SET isEquipped = 0 WHERE category = :cat") suspend fun unequipAll(cat: String)
    @Query("UPDATE inventory_items SET isEquipped = 1 WHERE id = :id") suspend fun equip(id: Long)
}

@Dao interface AchievementDao {
    @Query("SELECT * FROM achievements") fun observeAll(): Flow<List<Achievement>>
    @Query("SELECT * FROM achievements WHERE id = :id") suspend fun get(id: String): Achievement?
    @Upsert suspend fun upsert(a: Achievement)
}

@Dao interface CustomShopDao {
    @Query("SELECT * FROM custom_shop_items ORDER BY createdAt DESC") fun observeAll(): Flow<List<CustomShopItem>>
    @Insert suspend fun insert(item: CustomShopItem): Long
    @Update suspend fun update(item: CustomShopItem)
    @Delete suspend fun delete(item: CustomShopItem)
}

@Dao interface CustomPurchaseDao {
    @Query("SELECT * FROM custom_purchases ORDER BY purchasedAt DESC LIMIT 30") fun observeRecent(): Flow<List<CustomPurchase>>
    @Insert suspend fun insert(p: CustomPurchase): Long
    @Update suspend fun update(p: CustomPurchase)
}
