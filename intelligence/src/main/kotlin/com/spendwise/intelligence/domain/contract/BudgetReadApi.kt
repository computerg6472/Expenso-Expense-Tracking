package com.spendwise.intelligence.domain.contract

import com.spendwise.intelligence.domain.model.BudgetInfo
import kotlinx.coroutines.flow.Flow

interface BudgetReadApi {
    fun observeBudgets(year: Int, month: Int): Flow<List<BudgetInfo>>
    suspend fun getBudgets(year: Int, month: Int): List<BudgetInfo>
}
