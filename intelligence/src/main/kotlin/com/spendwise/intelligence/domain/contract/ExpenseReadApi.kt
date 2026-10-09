package com.spendwise.intelligence.domain.contract

import com.spendwise.intelligence.domain.model.DateRange
import com.spendwise.intelligence.domain.model.ExpenseRecord
import com.spendwise.intelligence.domain.model.MonthSummary
import kotlinx.coroutines.flow.Flow

interface ExpenseReadApi {
    fun observeExpenses(dateRange: DateRange): Flow<List<ExpenseRecord>>
    fun observeAllExpenses(): Flow<List<ExpenseRecord>>
    suspend fun getExpenses(dateRange: DateRange): List<ExpenseRecord>
    suspend fun getAllExpenses(): List<ExpenseRecord>
    suspend fun getMonthSummary(year: Int, month: Int): MonthSummary?
}
