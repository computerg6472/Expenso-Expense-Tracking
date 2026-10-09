package com.spendwise.app.data.repository

import com.spendwise.app.data.backup.BackupTriggerManager
import com.spendwise.app.data.local.dao.ExpenseDao
import com.spendwise.app.data.local.dao.MonthlyTotal
import com.spendwise.app.data.local.entity.Expense
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ExpenseRepositoryImpl – Strictly Local-First Architecture.
 *
 * Every add / update / delete:
 *  1. Writes to local Room/SQLCipher database immediately (optimistic, 0ms latency).
 *  2. Marks data changed in BackupTriggerManager to schedule debounced Google Drive snapshot backup.
 */
@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao,
    private val appPreferences: AppPreferences,
    private val backupTriggerManager: BackupTriggerManager
) : ExpenseRepository {

    override suspend fun addExpense(expense: Expense): String {
        val deviceId = appPreferences.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val prepared = expense.copy(
            deviceId = if (expense.deviceId.isBlank()) deviceId else expense.deviceId,
            createdAt = now,
            updatedAt = now
        )
        dao.insert(prepared)
        backupTriggerManager.markDataChanged()
        return prepared.id
    }

    override suspend fun updateExpense(expense: Expense) {
        val deviceId = appPreferences.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val prepared = expense.copy(
            deviceId = if (expense.deviceId.isBlank()) deviceId else expense.deviceId,
            updatedAt = now
        )
        dao.update(prepared)
        backupTriggerManager.markDataChanged()
    }

    override suspend fun deleteExpense(expense: Expense) {
        val now = System.currentTimeMillis()
        dao.softDelete(expense.id, now)
        backupTriggerManager.markDataChanged()
    }

    override suspend fun getExpenseById(id: String) = dao.getById(id)

    override fun observeByYearMonth(year: Int, month: Int) = dao.observeByYearMonth(year, month)

    override fun observeAll() = dao.observeAll()

    override fun searchExpenses(query: String) = dao.searchExpenses(query)

    override fun observeByDateRange(startEpoch: Long, endEpoch: Long) =
        dao.observeByDateRange(startEpoch, endEpoch)

    override suspend fun getTotalIncomeSpend(year: Int, month: Int) =
        dao.getTotalIncomeSpendForMonth(year, month)

    override suspend fun getTotalCreditCardSpend(year: Int, month: Int) =
        dao.getTotalCreditCardSpendForMonth(year, month)

    override fun observeTotalIncomeSpend(year: Int, month: Int) =
        dao.observeTotalIncomeSpendForMonth(year, month)

    override fun observeTotalCreditCardSpend(year: Int, month: Int) =
        dao.observeTotalCreditCardSpendForMonth(year, month)

    override fun getMonthlyIncomeTotals() = dao.getMonthlyIncomeTotals()

    override fun getMonthlyCreditTotals() = dao.getMonthlyCreditTotals()

    override suspend fun reassignCategory(oldCategoryId: String, newCategoryId: String) {
        dao.reassignCategory(oldCategoryId, newCategoryId)
    }

    override suspend fun getByCategory(categoryId: String) = dao.getByCategoryId(categoryId)

    override fun observeByYearMonthAndCategory(year: Int, month: Int, categoryId: String) =
        dao.observeByYearMonthAndCategory(year, month, categoryId)

    override suspend fun markSplitReimbursed(expenseId: String, isReimbursed: Boolean) {
        val now = System.currentTimeMillis()
        dao.markSplitReimbursed(expenseId, isReimbursed, now)
        backupTriggerManager.markDataChanged()
    }

    override suspend fun updateSplitDetails(
        expenseId: String,
        isSplit: Boolean,
        receivableAmount: Double,
        netUserExpense: Double,
        isReimbursed: Boolean
    ) {
        val now = System.currentTimeMillis()
        dao.updateSplitDetails(expenseId, isSplit, receivableAmount, netUserExpense, isReimbursed, now)
        backupTriggerManager.markDataChanged()
    }

    override fun observeSplitExpenses() = dao.observeSplitExpenses()

    override fun observePendingReimbursements() = dao.observePendingReimbursements()

    override fun observeTotalPendingReceivable() = dao.observeTotalPendingReceivable()
}
