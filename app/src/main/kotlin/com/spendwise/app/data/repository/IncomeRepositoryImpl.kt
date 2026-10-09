package com.spendwise.app.data.repository

import com.spendwise.app.data.backup.BackupTriggerManager
import com.spendwise.app.data.local.dao.IncomeMonthDao
import com.spendwise.app.data.local.entity.IncomeMonth
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.repository.IncomeRepository
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IncomeRepositoryImpl — Strictly Local-First Architecture.
 *
 * Salary and credit card due records are stored directly in Room/SQLCipher.
 * Changes mark data as changed in BackupTriggerManager to schedule debounced Google Drive snapshot backup.
 */
@Singleton
class IncomeRepositoryImpl @Inject constructor(
    private val dao: IncomeMonthDao,
    private val appPreferences: AppPreferences,
    private val backupTriggerManager: BackupTriggerManager
) : IncomeRepository {

    private fun currentYearMonth(): Pair<Int, Int> {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
    }

    override suspend fun getOrCreateCurrentMonth(): IncomeMonth {
        val (year, month) = currentYearMonth()
        val existing = dao.getByYearMonth(year, month)
        if (existing != null) return existing

        val deviceId = appPreferences.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val newRecord = IncomeMonth(year = year, month = month, salary = 0.0, deviceId = deviceId, updatedAt = now)
        dao.insert(newRecord)
        backupTriggerManager.markDataChanged()
        return newRecord
    }

    override suspend fun setSalary(year: Int, month: Int, salary: Double): IncomeMonth {
        val deviceId = appPreferences.getOrCreateDeviceId()
        val existing = dao.getByYearMonth(year, month)
        val now = System.currentTimeMillis()
        val record = if (existing != null) {
            val updated = existing.copy(salary = salary, deviceId = deviceId, updatedAt = now)
            dao.update(updated)
            updated
        } else {
            val newRecord = IncomeMonth(year = year, month = month, salary = salary, deviceId = deviceId, updatedAt = now)
            dao.insert(newRecord)
            newRecord
        }
        backupTriggerManager.markDataChanged()
        return record
    }

    override suspend fun addExtraCredit(year: Int, month: Int, amount: Double): IncomeMonth {
        val deviceId = appPreferences.getOrCreateDeviceId()
        val existing = dao.getByYearMonth(year, month)
        val now = System.currentTimeMillis()
        val currentCredit = existing?.extraCredit ?: 0.0
        val newCredit = currentCredit + amount
        val record = if (existing != null) {
            val updated = existing.copy(extraCredit = newCredit, deviceId = deviceId, updatedAt = now)
            dao.update(updated)
            updated
        } else {
            val newRecord = IncomeMonth(year = year, month = month, salary = 0.0, extraCredit = newCredit, deviceId = deviceId, updatedAt = now)
            dao.insert(newRecord)
            newRecord
        }
        backupTriggerManager.markDataChanged()
        return record
    }

    override suspend fun setExtraCredit(year: Int, month: Int, amount: Double): IncomeMonth {
        val deviceId = appPreferences.getOrCreateDeviceId()
        val existing = dao.getByYearMonth(year, month)
        val now = System.currentTimeMillis()
        val record = if (existing != null) {
            val updated = existing.copy(extraCredit = amount, deviceId = deviceId, updatedAt = now)
            dao.update(updated)
            updated
        } else {
            val newRecord = IncomeMonth(year = year, month = month, salary = 0.0, extraCredit = amount, deviceId = deviceId, updatedAt = now)
            dao.insert(newRecord)
            newRecord
        }
        backupTriggerManager.markDataChanged()
        return record
    }

    override suspend fun updateCreditCardDue(year: Int, month: Int, due: Double) {
        val existing = dao.getByYearMonth(year, month) ?: return
        val deviceId = appPreferences.getOrCreateDeviceId()
        val updated = existing.copy(
            creditCardDueCarried = due,
            deviceId = deviceId,
            updatedAt = System.currentTimeMillis()
        )
        dao.update(updated)
        backupTriggerManager.markDataChanged()
    }

    override fun observeCurrentMonth(): Flow<IncomeMonth?> {
        val (year, month) = currentYearMonth()
        return dao.observeByYearMonth(year, month)
    }

    override fun observeByYearMonth(year: Int, month: Int): Flow<IncomeMonth?> =
        dao.observeByYearMonth(year, month)

    override fun observeAll(): Flow<List<IncomeMonth>> = dao.observeAll()

    override suspend fun getByYearMonth(year: Int, month: Int): IncomeMonth? =
        dao.getByYearMonth(year, month)
}
