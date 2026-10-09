package com.spendwise.app.data.local.dao

import androidx.room.*
import com.spendwise.app.data.local.entity.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("UPDATE expense SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM expense WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getById(id: String): Expense?

    @Query("SELECT * FROM expense WHERE id = :id LIMIT 1")
    suspend fun getByIdRaw(id: String): Expense?

    @Query("SELECT * FROM expense WHERE year = :year AND month = :month AND is_deleted = 0 ORDER BY date_epoch DESC")
    fun observeByYearMonth(year: Int, month: Int): Flow<List<Expense>>

    @Query("SELECT * FROM expense WHERE month_key = :monthKey AND is_deleted = 0 ORDER BY date_epoch DESC")
    fun observeByMonthKey(monthKey: String): Flow<List<Expense>>

    @Query("SELECT * FROM expense WHERE year = :year AND month = :month AND payment_source = :source AND is_deleted = 0 ORDER BY date_epoch DESC")
    fun observeByYearMonthAndSource(year: Int, month: Int, source: String): Flow<List<Expense>>

    @Query("SELECT * FROM expense WHERE year = :year AND month = :month AND category_id = :categoryId AND is_deleted = 0 ORDER BY date_epoch DESC")
    fun observeByYearMonthAndCategory(year: Int, month: Int, categoryId: String): Flow<List<Expense>>

    @Query("SELECT COALESCE(SUM(CASE WHEN is_split = 1 AND is_reimbursed = 1 THEN CASE WHEN (amount - receivable_amount) > 0.0 THEN (amount - receivable_amount) WHEN net_user_expense > 0.0 THEN net_user_expense ELSE 0.0 END ELSE amount END), 0.0) FROM expense WHERE year = :year AND month = :month AND payment_source = 'INCOME' AND is_deleted = 0")
    suspend fun getTotalIncomeSpendForMonth(year: Int, month: Int): Double

    @Query("SELECT COALESCE(SUM(CASE WHEN is_split = 1 AND is_reimbursed = 1 THEN CASE WHEN (amount - receivable_amount) > 0.0 THEN (amount - receivable_amount) WHEN net_user_expense > 0.0 THEN net_user_expense ELSE 0.0 END ELSE amount END), 0.0) FROM expense WHERE year = :year AND month = :month AND payment_source = 'CREDIT_CARD' AND is_deleted = 0")
    suspend fun getTotalCreditCardSpendForMonth(year: Int, month: Int): Double

    @Query("SELECT COALESCE(SUM(CASE WHEN is_split = 1 AND is_reimbursed = 1 THEN CASE WHEN (amount - receivable_amount) > 0.0 THEN (amount - receivable_amount) WHEN net_user_expense > 0.0 THEN net_user_expense ELSE 0.0 END ELSE amount END), 0.0) FROM expense WHERE year = :year AND month = :month AND payment_source = 'INCOME' AND is_deleted = 0")
    fun observeTotalIncomeSpendForMonth(year: Int, month: Int): Flow<Double>

    @Query("SELECT COALESCE(SUM(CASE WHEN is_split = 1 AND is_reimbursed = 1 THEN CASE WHEN (amount - receivable_amount) > 0.0 THEN (amount - receivable_amount) WHEN net_user_expense > 0.0 THEN net_user_expense ELSE 0.0 END ELSE amount END), 0.0) FROM expense WHERE year = :year AND month = :month AND payment_source = 'CREDIT_CARD' AND is_deleted = 0")
    fun observeTotalCreditCardSpendForMonth(year: Int, month: Int): Flow<Double>

    @Query("SELECT * FROM expense WHERE year = :year AND month = :month AND category_id = :categoryId AND is_deleted = 0")
    suspend fun getByMonthAndCategory(year: Int, month: Int, categoryId: String): List<Expense>

    @Query("SELECT * FROM expense WHERE category_id = :categoryId AND is_deleted = 0")
    suspend fun getByCategoryId(categoryId: String): List<Expense>

    @Query("UPDATE expense SET category_id = :newCategoryId, updated_at = :now WHERE category_id = :oldCategoryId")
    suspend fun reassignCategory(oldCategoryId: String, newCategoryId: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM expense WHERE date_epoch BETWEEN :startEpoch AND :endEpoch AND is_deleted = 0 ORDER BY date_epoch DESC")
    fun observeByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expense WHERE is_deleted = 0 ORDER BY date_epoch DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expense WHERE (note LIKE '%' || :query || '%') AND is_deleted = 0 ORDER BY date_epoch DESC")
    fun searchExpenses(query: String): Flow<List<Expense>>

    @Query("SELECT year, month, COALESCE(SUM(CASE WHEN is_split = 1 AND is_reimbursed = 1 THEN CASE WHEN (amount - receivable_amount) > 0.0 THEN (amount - receivable_amount) WHEN net_user_expense > 0.0 THEN net_user_expense ELSE 0.0 END ELSE amount END), 0.0) as total FROM expense WHERE payment_source = 'INCOME' AND is_deleted = 0 GROUP BY year, month ORDER BY year DESC, month DESC LIMIT 12")
    fun getMonthlyIncomeTotals(): Flow<List<MonthlyTotal>>

    @Query("SELECT year, month, COALESCE(SUM(CASE WHEN is_split = 1 AND is_reimbursed = 1 THEN CASE WHEN (amount - receivable_amount) > 0.0 THEN (amount - receivable_amount) WHEN net_user_expense > 0.0 THEN net_user_expense ELSE 0.0 END ELSE amount END), 0.0) as total FROM expense WHERE payment_source = 'CREDIT_CARD' AND is_deleted = 0 GROUP BY year, month ORDER BY year DESC, month DESC LIMIT 12")
    fun getMonthlyCreditTotals(): Flow<List<MonthlyTotal>>

    @Query("SELECT COALESCE(SUM(CASE WHEN is_split = 1 AND is_reimbursed = 1 THEN CASE WHEN (amount - receivable_amount) > 0.0 THEN (amount - receivable_amount) WHEN net_user_expense > 0.0 THEN net_user_expense ELSE 0.0 END ELSE amount END), 0.0) FROM expense WHERE year = :year AND month = :month AND category_id = :categoryId AND is_deleted = 0")
    fun observeSpendForCategoryMonth(year: Int, month: Int, categoryId: String): Flow<Double>

    @Query("SELECT COUNT(*) FROM expense WHERE category_id = :categoryId AND is_deleted = 0")
    suspend fun getExpenseCountForCategory(categoryId: String): Int

    @Query("SELECT MIN(date_epoch) FROM expense WHERE category_id = :categoryId AND is_deleted = 0")
    suspend fun getFirstExpenseDateForCategory(categoryId: String): Long?

    @Query("SELECT MAX(date_epoch) FROM expense WHERE category_id = :categoryId AND is_deleted = 0")
    suspend fun getLastExpenseDateForCategory(categoryId: String): Long?

    @Query("SELECT COALESCE(SUM(CASE WHEN is_split = 1 AND is_reimbursed = 1 THEN CASE WHEN (amount - receivable_amount) > 0.0 THEN (amount - receivable_amount) WHEN net_user_expense > 0.0 THEN net_user_expense ELSE 0.0 END ELSE amount END), 0.0) FROM expense WHERE year = :year AND month = :month AND category_id = :categoryId AND is_deleted = 0")
    suspend fun getSpendForCategoryMonth(year: Int, month: Int, categoryId: String): Double

    // Sync helpers
    @Query("SELECT * FROM expense WHERE updated_at > :sinceEpoch")
    suspend fun getUpdatedSince(sinceEpoch: Long): List<Expense>

    @Query("SELECT * FROM expense WHERE month_key = :monthKey")
    suspend fun getByMonthKeyRaw(monthKey: String): List<Expense>

    @Query("SELECT * FROM expense")
    suspend fun getAllIncludingDeleted(): List<Expense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: Expense)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<Expense>)

    @Query("DELETE FROM expense")
    suspend fun clearAll()

    // Split Expense & In-Place Settlement (Avoids duplicate snapshot/audit table bloat)
    @Query("UPDATE expense SET is_reimbursed = :isReimbursed, updated_at = :now WHERE id = :id")
    suspend fun markSplitReimbursed(id: String, isReimbursed: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE expense SET is_split = :isSplit, receivable_amount = :receivable, net_user_expense = :netUser, is_reimbursed = :isReimbursed, updated_at = :now WHERE id = :id")
    suspend fun updateSplitDetails(id: String, isSplit: Boolean, receivable: Double, netUser: Double, isReimbursed: Boolean, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM expense WHERE is_split = 1 AND is_deleted = 0 ORDER BY date_epoch DESC")
    fun observeSplitExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expense WHERE is_split = 1 AND is_reimbursed = 0 AND is_deleted = 0 ORDER BY date_epoch DESC")
    fun observePendingReimbursements(): Flow<List<Expense>>

    @Query("SELECT COALESCE(SUM(receivable_amount), 0.0) FROM expense WHERE is_split = 1 AND is_reimbursed = 0 AND is_deleted = 0")
    fun observeTotalPendingReceivable(): Flow<Double>

    @Query("SELECT COALESCE(SUM(extra_credit_amount), 0.0) FROM expense WHERE year = :year AND month = :month AND is_deleted = 0")
    suspend fun getTotalExtraCreditForMonth(year: Int, month: Int): Double

    @Query("SELECT COALESCE(SUM(extra_credit_amount), 0.0) FROM expense WHERE year = :year AND month = :month AND is_deleted = 0")
    fun observeTotalExtraCreditForMonth(year: Int, month: Int): Flow<Double>
}

data class MonthlyTotal(val year: Int, val month: Int, val total: Double)
