package com.spendwise.app.data.local.dao

import androidx.room.*
import com.spendwise.app.data.local.entity.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Upsert
    suspend fun insert(category: Category)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("UPDATE category SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE category SET is_deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM category WHERE is_deleted = 0 ORDER BY CASE WHEN id = 'cat_others' THEN 1 ELSE 0 END ASC, sort_order ASC, is_default DESC, name ASC")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM category WHERE is_deleted = 0 ORDER BY CASE WHEN id = 'cat_others' THEN 1 ELSE 0 END ASC, sort_order ASC, is_default DESC, name ASC")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM category WHERE is_deleted = 1 ORDER BY updated_at DESC")
    fun observeRecentlyDeleted(): Flow<List<Category>>

    @Query("SELECT * FROM category WHERE is_deleted = 1 ORDER BY updated_at DESC")
    suspend fun getRecentlyDeleted(): List<Category>

    @Query("SELECT * FROM category WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getById(id: String): Category?

    @Query("SELECT * FROM category WHERE id = 'cat_others' LIMIT 1")
    suspend fun getOthers(): Category?

    @Query("SELECT * FROM category WHERE id = :id LIMIT 1")
    suspend fun getByIdRaw(id: String): Category?

    @Query("SELECT * FROM category WHERE is_default = 1 AND is_deleted = 0 ORDER BY CASE WHEN id = 'cat_others' THEN 1 ELSE 0 END ASC, sort_order ASC, name ASC")
    suspend fun getDefaults(): List<Category>

    @Query("SELECT COUNT(*) FROM category WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND id != :excludeId AND is_deleted = 0")
    suspend fun countByName(name: String, excludeId: String = ""): Int

    @Query("UPDATE category SET sort_order = :sortOrder, updated_at = :now WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, now: Long = System.currentTimeMillis())

    // Sync helpers
    @Query("SELECT * FROM category WHERE updated_at > :sinceEpoch")
    suspend fun getUpdatedSince(sinceEpoch: Long): List<Category>

    @Query("SELECT * FROM category")
    suspend fun getAllIncludingDeleted(): List<Category>

    @Upsert
    suspend fun upsert(category: Category)

    @Upsert
    suspend fun upsertAll(categories: List<Category>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnoreAll(categories: List<Category>): List<Long>

    @Query("DELETE FROM category")
    suspend fun clearAll()
}
