package com.spendwise.app.domain.repository

import com.spendwise.app.data.local.entity.Category
import kotlinx.coroutines.flow.Flow

data class CategoryUsageStats(
    val categoryId: String,
    val expenseCount: Int,
    val firstDateEpoch: Long?,
    val lastDateEpoch: Long?,
    val currentMonthSpend: Double
)

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    suspend fun getById(id: String): Category?
    suspend fun getOthersCategory(): Category
    suspend fun addCategory(category: Category): String
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(category: Category)
    suspend fun deleteAndReassign(category: Category, targetCategoryId: String)
    suspend fun restoreCategory(id: String)
    fun observeRecentlyDeleted(): Flow<List<Category>>
    suspend fun mergeCategories(sourceCategoryId: String, targetCategoryId: String)
    suspend fun reorderCategories(orderedIds: List<String>)
    suspend fun insertDefaults()
    suspend fun ensureOthersCategory(): Category
    suspend fun isNameTaken(name: String, excludeId: String = ""): Boolean
    suspend fun getUsageStats(categoryId: String): CategoryUsageStats
}
