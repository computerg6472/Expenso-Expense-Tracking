package com.spendwise.app.data.repository

import com.spendwise.app.data.local.dao.CreditCardLedgerDao
import com.spendwise.app.data.local.dao.ExpenseDao
import com.spendwise.app.data.local.dao.SyncOutboxDao
import com.spendwise.app.data.local.entity.CreditCardLedger
import com.spendwise.app.data.local.entity.SyncOutbox
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.repository.CreditCardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreditCardRepositoryImpl @Inject constructor(
    private val dao: CreditCardLedgerDao,
    private val expenseDao: ExpenseDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val appPreferences: AppPreferences
) : CreditCardRepository {

    override suspend fun getOrCreate(year: Int, month: Int): CreditCardLedger {
        val existing = dao.getByYearMonth(year, month)
        val derivedTotal = expenseDao.getTotalCreditCardSpendForMonth(year, month)
        val deviceId = appPreferences.getOrCreateDeviceId()

        return if (existing != null) {
            if (existing.totalSpend != derivedTotal) {
                val updated = existing.copy(totalSpend = derivedTotal, updatedAt = System.currentTimeMillis())
                dao.update(updated)
                updated
            } else {
                existing
            }
        } else {
            val newRecord = CreditCardLedger(
                year = year,
                month = month,
                totalSpend = derivedTotal,
                deviceId = deviceId
            )
            dao.insert(newRecord)
            syncOutboxDao.insert(
                SyncOutbox(
                    entityType = "CREDIT_CARD_LEDGER",
                    entityId = newRecord.id,
                    operation = "UPSERT"
                )
            )
            newRecord
        }
    }

    override suspend fun updateTotal(year: Int, month: Int, total: Double) {
        val existing = getOrCreate(year, month)
        val updated = existing.copy(totalSpend = total, updatedAt = System.currentTimeMillis())
        dao.update(updated)
    }

    override suspend fun markCarried(year: Int, month: Int) {
        dao.markCarried(year, month)
    }

    override fun observeByYearMonth(year: Int, month: Int): Flow<CreditCardLedger?> =
        dao.observeByYearMonth(year, month)
}
