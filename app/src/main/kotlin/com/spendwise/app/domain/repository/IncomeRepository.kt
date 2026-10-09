package com.spendwise.app.domain.repository

import com.spendwise.app.data.local.entity.IncomeMonth
import kotlinx.coroutines.flow.Flow

interface IncomeRepository {
    suspend fun getOrCreateCurrentMonth(): IncomeMonth
    suspend fun setSalary(year: Int, month: Int, salary: Double): IncomeMonth
    suspend fun addExtraCredit(year: Int, month: Int, amount: Double): IncomeMonth
    suspend fun setExtraCredit(year: Int, month: Int, amount: Double): IncomeMonth
    suspend fun updateCreditCardDue(year: Int, month: Int, due: Double)
    fun observeCurrentMonth(): Flow<IncomeMonth?>
    fun observeByYearMonth(year: Int, month: Int): Flow<IncomeMonth?>
    fun observeAll(): Flow<List<IncomeMonth>>
    suspend fun getByYearMonth(year: Int, month: Int): IncomeMonth?
}
