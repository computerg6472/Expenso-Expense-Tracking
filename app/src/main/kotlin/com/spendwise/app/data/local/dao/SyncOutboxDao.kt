package com.spendwise.app.data.local.dao

import androidx.room.*
import com.spendwise.app.data.local.entity.SyncOutbox
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncOutbox)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SyncOutbox>)

    @Query("SELECT * FROM sync_outbox ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 50): List<SyncOutbox>

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sync_outbox WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM sync_outbox WHERE entity_id = :entityId")
    suspend fun deleteByEntityId(entityId: String)

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun countPending(): Flow<Int>

    @Query("DELETE FROM sync_outbox")
    suspend fun clearAll()
}
