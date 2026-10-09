package com.spendwise.app.domain.repository

import com.spendwise.app.data.local.entity.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun observeByYearMonth(year: Int, month: Int): Flow<List<Budget>>
    suspend fun setBudget(budget: Budget)
    suspend fun deleteBudget(budget: Budget)
    fun observeBudgetForCategory(categoryId: String, year: Int, month: Int): Flow<Budget?>
}
