package com.spendwise.app.data.repository

import com.spendwise.app.data.backup.BackupTriggerManager
import com.spendwise.app.data.local.dao.BudgetDao
import com.spendwise.app.data.local.entity.Budget
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BudgetRepositoryImpl — Strictly Local-First Architecture.
 *
 * Budgets are tracked directly in Room/SQLCipher database.
 * Changes mark data as changed in BackupTriggerManager to schedule debounced Google Drive snapshot backup.
 */
@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val dao: BudgetDao,
    private val appPreferences: AppPreferences,
    private val backupTriggerManager: BackupTriggerManager
) : BudgetRepository {

    override fun observeByYearMonth(year: Int, month: Int): Flow<List<Budget>> =
        dao.observeByYearMonth(year, month)

    override suspend fun setBudget(budget: Budget) {
        val existing = dao.getBudgetForCategoryMonth(budget.categoryId, budget.year, budget.month)
        val deviceId = appPreferences.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val prepared = if (existing != null) {
            existing.copy(budgetAmount = budget.budgetAmount, deviceId = deviceId, updatedAt = now)
        } else {
            budget.copy(deviceId = deviceId, updatedAt = now)
        }
        dao.insert(prepared)
        backupTriggerManager.markDataChanged()
    }

    override suspend fun deleteBudget(budget: Budget) {
        dao.softDelete(budget.id, System.currentTimeMillis())
        backupTriggerManager.markDataChanged()
    }

    override fun observeBudgetForCategory(categoryId: String, year: Int, month: Int): Flow<Budget?> =
        dao.observeBudgetForCategoryMonth(categoryId, year, month)
}
