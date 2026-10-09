package com.spendwise.app.data.local.dao

import androidx.room.*
import com.spendwise.app.data.local.entity.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Upsert
    suspend fun insert(budget: Budget)

    @Update
    suspend fun update(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("UPDATE budget SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM budget WHERE year = :year AND month = :month AND is_deleted = 0")
    fun observeByYearMonth(year: Int, month: Int): Flow<List<Budget>>

    @Query("SELECT * FROM budget WHERE month_key = :monthKey AND is_deleted = 0")
    fun observeByMonthKey(monthKey: String): Flow<List<Budget>>

    @Query("SELECT * FROM budget WHERE category_id = :categoryId AND year = :year AND month = :month AND is_deleted = 0 LIMIT 1")
    suspend fun getBudgetForCategoryMonth(categoryId: String, year: Int, month: Int): Budget?

    @Query("SELECT * FROM budget WHERE category_id = :categoryId AND year = :year AND month = :month AND is_deleted = 0 LIMIT 1")
    fun observeBudgetForCategoryMonth(categoryId: String, year: Int, month: Int): Flow<Budget?>

    // Sync helpers
    @Query("SELECT * FROM budget WHERE updated_at > :sinceEpoch")
    suspend fun getUpdatedSince(sinceEpoch: Long): List<Budget>

    @Query("SELECT * FROM budget")
    suspend fun getAllIncludingDeleted(): List<Budget>

    @Upsert
    suspend fun upsert(budget: Budget)

    @Upsert
    suspend fun upsertAll(budgets: List<Budget>)

    @Query("DELETE FROM budget")
    suspend fun clearAll()
}
