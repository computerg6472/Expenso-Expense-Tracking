package com.spendwise.app.data.local.dao

import androidx.room.*
import com.spendwise.app.data.local.entity.IncomeMonth
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeMonthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incomeMonth: IncomeMonth)

    @Update
    suspend fun update(incomeMonth: IncomeMonth)

    @Delete
    suspend fun delete(incomeMonth: IncomeMonth)

    @Query("UPDATE income_month SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM income_month WHERE year = :year AND month = :month AND is_deleted = 0 LIMIT 1")
    suspend fun getByYearMonth(year: Int, month: Int): IncomeMonth?

    @Query("SELECT * FROM income_month WHERE month_key = :monthKey AND is_deleted = 0 LIMIT 1")
    suspend fun getByMonthKey(monthKey: String): IncomeMonth?

    @Query("SELECT * FROM income_month WHERE year = :year AND month = :month AND is_deleted = 0 LIMIT 1")
    fun observeByYearMonth(year: Int, month: Int): Flow<IncomeMonth?>

    @Query("SELECT * FROM income_month WHERE month_key = :monthKey AND is_deleted = 0 LIMIT 1")
    fun observeByMonthKey(monthKey: String): Flow<IncomeMonth?>

    @Query("SELECT * FROM income_month WHERE is_deleted = 0 ORDER BY year DESC, month DESC")
    fun observeAll(): Flow<List<IncomeMonth>>

    @Query("SELECT * FROM income_month WHERE is_deleted = 0 ORDER BY year DESC, month DESC LIMIT 1")
    suspend fun getLatest(): IncomeMonth?

    @Query("SELECT * FROM income_month WHERE (year < :year OR (year = :year AND month < :month)) AND is_deleted = 0 ORDER BY year DESC, month DESC LIMIT 1")
    suspend fun getPreviousMonth(year: Int, month: Int): IncomeMonth?

    // Sync helpers
    @Query("SELECT * FROM income_month WHERE updated_at > :sinceEpoch")
    suspend fun getUpdatedSince(sinceEpoch: Long): List<IncomeMonth>

    @Query("SELECT * FROM income_month")
    suspend fun getAllIncludingDeleted(): List<IncomeMonth>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(income: IncomeMonth)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(incomes: List<IncomeMonth>)

    @Query("DELETE FROM income_month")
    suspend fun clearAll()
}
