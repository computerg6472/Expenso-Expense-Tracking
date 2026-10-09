package com.spendwise.app.domain.repository

import com.spendwise.app.data.local.dao.MonthlyTotal
import com.spendwise.app.data.local.entity.Expense
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    suspend fun addExpense(expense: Expense): String
    suspend fun updateExpense(expense: Expense)
    suspend fun deleteExpense(expense: Expense)
    suspend fun getExpenseById(id: String): Expense?
    fun observeByYearMonth(year: Int, month: Int): Flow<List<Expense>>
    fun observeAll(): Flow<List<Expense>>
    fun searchExpenses(query: String): Flow<List<Expense>>
    fun observeByDateRange(startEpoch: Long, endEpoch: Long): Flow<List<Expense>>
    suspend fun getTotalIncomeSpend(year: Int, month: Int): Double
    suspend fun getTotalCreditCardSpend(year: Int, month: Int): Double
    fun observeTotalIncomeSpend(year: Int, month: Int): Flow<Double>
    fun observeTotalCreditCardSpend(year: Int, month: Int): Flow<Double>
    fun getMonthlyIncomeTotals(): Flow<List<MonthlyTotal>>
    fun getMonthlyCreditTotals(): Flow<List<MonthlyTotal>>
    suspend fun reassignCategory(oldCategoryId: String, newCategoryId: String)
    suspend fun getByCategory(categoryId: String): List<Expense>
    fun observeByYearMonthAndCategory(year: Int, month: Int, categoryId: String): Flow<List<Expense>>
    suspend fun markSplitReimbursed(expenseId: String, isReimbursed: Boolean)
    suspend fun updateSplitDetails(expenseId: String, isSplit: Boolean, receivableAmount: Double, netUserExpense: Double, isReimbursed: Boolean)
    fun observeSplitExpenses(): Flow<List<Expense>>
    fun observePendingReimbursements(): Flow<List<Expense>>
    fun observeTotalPendingReceivable(): Flow<Double>
}
