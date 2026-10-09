package com.spendwise.app.data.local.dao

import androidx.room.*
import com.spendwise.app.data.local.entity.CreditCardLedger
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditCardLedgerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ledger: CreditCardLedger)

    @Update
    suspend fun update(ledger: CreditCardLedger)

    @Delete
    suspend fun delete(ledger: CreditCardLedger)

    @Query("UPDATE credit_card_ledger SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM credit_card_ledger WHERE year = :year AND month = :month AND is_deleted = 0 LIMIT 1")
    suspend fun getByYearMonth(year: Int, month: Int): CreditCardLedger?

    @Query("SELECT * FROM credit_card_ledger WHERE month_key = :monthKey AND is_deleted = 0 LIMIT 1")
    suspend fun getByMonthKey(monthKey: String): CreditCardLedger?

    @Query("SELECT * FROM credit_card_ledger WHERE year = :year AND month = :month AND is_deleted = 0 LIMIT 1")
    fun observeByYearMonth(year: Int, month: Int): Flow<CreditCardLedger?>

    @Query("UPDATE credit_card_ledger SET carried_to_next_month = 1, updated_at = :now WHERE year = :year AND month = :month")
    suspend fun markCarried(year: Int, month: Int, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM credit_card_ledger WHERE is_deleted = 0 ORDER BY year DESC, month DESC")
    fun observeAll(): Flow<List<CreditCardLedger>>

    // Sync helpers
    @Query("SELECT * FROM credit_card_ledger WHERE updated_at > :sinceEpoch")
    suspend fun getUpdatedSince(sinceEpoch: Long): List<CreditCardLedger>

    @Query("SELECT * FROM credit_card_ledger")
    suspend fun getAllIncludingDeleted(): List<CreditCardLedger>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ledger: CreditCardLedger)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(ledgers: List<CreditCardLedger>)

    @Query("DELETE FROM credit_card_ledger")
    suspend fun clearAll()
}
