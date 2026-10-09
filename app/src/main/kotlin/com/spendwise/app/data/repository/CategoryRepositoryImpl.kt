package com.spendwise.app.data.repository

import android.util.Log
import com.spendwise.app.data.backup.BackupTriggerManager
import com.spendwise.app.data.local.dao.CategoryDao
import com.spendwise.app.data.local.dao.ExpenseDao
import com.spendwise.app.data.local.entity.Category
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.model.DefaultCategories
import com.spendwise.app.domain.repository.CategoryRepository
import com.spendwise.app.domain.repository.CategoryUsageStats
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CategoryRepo"

/**
 * CategoryRepositoryImpl — Strictly Local-First Architecture.
 * Category mutations are saved immediately to Room/SQLCipher.
 * Changes mark data as changed in BackupTriggerManager to schedule debounced Google Drive snapshot backup.
 */
@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
    private val expenseDao: ExpenseDao,
    private val appPreferences: AppPreferences,
    private val backupTriggerManager: BackupTriggerManager
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> = dao.observeAll()

    override suspend fun getAll(): List<Category> = dao.getAll()

    override suspend fun getById(id: String): Category? = dao.getById(id)

    override suspend fun getOthersCategory(): Category = ensureOthersCategory()

    override suspend fun ensureOthersCategory(): Category {
        val existing = dao.getOthers() ?: dao.getById(DefaultCategories.ID_OTHERS)
        if (existing != null && !existing.isDeleted) return existing
        val deviceId = appPreferences.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val others = DefaultCategories.CATEGORY_OTHERS.copy(deviceId = deviceId, updatedAt = now)
        dao.upsert(others)
        scheduleConfigWrite()
        return others
    }

    override suspend fun addCategory(category: Category): String {
        val deviceId = appPreferences.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val prepared = category.copy(
            deviceId = if (category.deviceId.isBlank()) deviceId else category.deviceId,
            createdAt = now, updatedAt = now
        )
        dao.insert(prepared)
        scheduleConfigWrite()
        return prepared.id
    }

    override suspend fun updateCategory(category: Category) {
        val deviceId = appPreferences.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val prepared = category.copy(
            deviceId = if (category.deviceId.isBlank()) deviceId else category.deviceId,
            updatedAt = now
        )
        dao.update(prepared)
        scheduleConfigWrite()
    }

    override suspend fun deleteCategory(category: Category) {
        dao.softDelete(category.id, System.currentTimeMillis())
        scheduleConfigWrite()
    }

    override suspend fun deleteAndReassign(category: Category, targetCategoryId: String) {
        val now = System.currentTimeMillis()
        expenseDao.reassignCategory(
            oldCategoryId = category.id,
            newCategoryId = targetCategoryId,
            now = now
        )
        deleteCategory(category)
        scheduleConfigWrite()
    }

    override suspend fun restoreCategory(id: String) {
        dao.restore(id, System.currentTimeMillis())
        scheduleConfigWrite()
    }

    override fun observeRecentlyDeleted(): Flow<List<Category>> = dao.observeRecentlyDeleted()

    override suspend fun mergeCategories(sourceCategoryId: String, targetCategoryId: String) {
        val source = dao.getById(sourceCategoryId) ?: return
        val target = dao.getById(targetCategoryId) ?: return
        val now = System.currentTimeMillis()

        expenseDao.reassignCategory(oldCategoryId = source.id, newCategoryId = target.id, now = now)

        val sourceAliases = source.aliasList
        val targetAliases = target.aliasList.toMutableList()
        for (a in sourceAliases) {
            if (!targetAliases.any { it.equals(a, ignoreCase = true) }) targetAliases.add(a)
        }
        val mergedAliases = targetAliases.joinToString(", ")
        val updatedTarget = target.copy(aliases = mergedAliases, updatedAt = now)
        dao.update(updatedTarget)
        dao.softDelete(source.id, now)
        scheduleConfigWrite()
    }

    override suspend fun reorderCategories(orderedIds: List<String>) {
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id ->
            if (id != DefaultCategories.ID_OTHERS) dao.updateSortOrder(id, index, now)
        }
        dao.updateSortOrder(DefaultCategories.ID_OTHERS, 9999, now)
        scheduleConfigWrite()
    }

    override suspend fun getUsageStats(categoryId: String): CategoryUsageStats {
        val count = expenseDao.getExpenseCountForCategory(categoryId)
        val firstDate = expenseDao.getFirstExpenseDateForCategory(categoryId)
        val lastDate = expenseDao.getLastExpenseDateForCategory(categoryId)
        val cal = java.util.Calendar.getInstance()
        val yr = cal.get(java.util.Calendar.YEAR)
        val mo = cal.get(java.util.Calendar.MONTH) + 1
        val monthSpend = expenseDao.getSpendForCategoryMonth(yr, mo, categoryId)
        return CategoryUsageStats(
            categoryId = categoryId,
            expenseCount = count,
            firstDateEpoch = firstDate,
            lastDateEpoch = lastDate,
            currentMonthSpend = monthSpend
        )
    }

    override suspend fun isNameTaken(name: String, excludeId: String): Boolean =
        dao.countByName(name, excludeId) > 0

    override suspend fun insertDefaults() {
        val deviceId = appPreferences.getOrCreateDeviceId()
        val existingIds = dao.getAll().map { it.id }.toSet()
        val existingNames = dao.getAll().map { it.name.trim().lowercase() }.toSet()
        var inserted = false
        DefaultCategories.ALL.forEach { defaultCat ->
            if (defaultCat.id !in existingIds && defaultCat.name.trim().lowercase() !in existingNames) {
                dao.insert(defaultCat.copy(deviceId = deviceId))
                inserted = true
            }
        }
        if (inserted) scheduleConfigWrite()
    }

    private fun scheduleConfigWrite() {
        backupTriggerManager.markDataChanged()
    }
}
