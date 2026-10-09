package com.spendwise.app.data.intelligence

import com.spendwise.app.domain.repository.BudgetRepository
import com.spendwise.app.domain.repository.CategoryRepository
import com.spendwise.app.domain.repository.ExpenseRepository
import com.spendwise.app.domain.repository.IncomeRepository
import com.spendwise.intelligence.domain.contract.BudgetReadApi
import com.spendwise.intelligence.domain.contract.CategoryReadApi
import com.spendwise.intelligence.domain.contract.ExpenseReadApi
import com.spendwise.intelligence.domain.contract.SalaryReadApi
import com.spendwise.intelligence.domain.model.BudgetInfo
import com.spendwise.intelligence.domain.model.CategoryInfo
import com.spendwise.intelligence.domain.model.DateRange
import com.spendwise.intelligence.domain.model.ExpenseRecord
import com.spendwise.intelligence.domain.model.MonthSummary
import com.spendwise.intelligence.domain.model.PaymentSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppExpenseReadAdapter @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository
) : ExpenseReadApi {

    override fun observeExpenses(dateRange: DateRange): Flow<List<ExpenseRecord>> {
        return expenseRepository.observeByDateRange(dateRange.startEpochMillis, dateRange.endEpochMillis)
            .map { list -> list.map { it.toIntelligenceRecord() } }
    }

    override fun observeAllExpenses(): Flow<List<ExpenseRecord>> {
        return expenseRepository.observeAll()
            .map { list -> list.map { it.toIntelligenceRecord() } }
    }

    override suspend fun getExpenses(dateRange: DateRange): List<ExpenseRecord> {
        return expenseRepository.observeByDateRange(dateRange.startEpochMillis, dateRange.endEpochMillis)
            .first()
            .map { it.toIntelligenceRecord() }
    }

    override suspend fun getAllExpenses(): List<ExpenseRecord> {
        return expenseRepository.observeAll().first().map { it.toIntelligenceRecord() }
    }

    override suspend fun getMonthSummary(year: Int, month: Int): MonthSummary? {
        val income = incomeRepository.getByYearMonth(year, month) ?: return null
        val incomeSpend = expenseRepository.getTotalIncomeSpend(year, month)
        val ccSpend = expenseRepository.getTotalCreditCardSpend(year, month)
        val ccDue = income.creditCardDueCarried
        val available = income.salary - incomeSpend - ccDue

        return MonthSummary(
            year = year,
            month = month,
            salary = income.salary,
            totalIncomeSpend = incomeSpend,
            totalCreditCardSpend = ccSpend,
            creditCardDue = ccDue,
            availableBalance = available
        )
    }

    private fun com.spendwise.app.data.local.entity.Expense.toIntelligenceRecord(): ExpenseRecord {
        return ExpenseRecord.fromDouble(
            id = id,
            amount = amount,
            categoryId = categoryId,
            source = PaymentSource.fromString(paymentSource),
            dateEpochMillis = dateEpoch,
            merchant = null,
            note = note,
            createdVia = "MANUAL"
        )
    }
}

@Singleton
class AppCategoryReadAdapter @Inject constructor(
    private val categoryRepository: CategoryRepository
) : CategoryReadApi {

    override fun observeCategories(): Flow<List<CategoryInfo>> {
        return categoryRepository.observeAll().map { list ->
            list.map { CategoryInfo(it.id, it.name, it.isDefault, it.colorHex, it.iconName) }
        }
    }

    override suspend fun getCategories(): List<CategoryInfo> {
        return categoryRepository.getAll().map {
            CategoryInfo(it.id, it.name, it.isDefault, it.colorHex, it.iconName)
        }
    }

    override suspend fun getCategoryById(id: String): CategoryInfo? {
        return categoryRepository.getById(id)?.let {
            CategoryInfo(it.id, it.name, it.isDefault, it.colorHex, it.iconName)
        }
    }
}

@Singleton
class AppBudgetReadAdapter @Inject constructor(
    private val budgetRepository: BudgetRepository
) : BudgetReadApi {

    override fun observeBudgets(year: Int, month: Int): Flow<List<BudgetInfo>> {
        return budgetRepository.observeByYearMonth(year, month).map { list ->
            list.map { BudgetInfo(it.id, it.categoryId, it.year, it.month, it.budgetAmount) }
        }
    }

    override suspend fun getBudgets(year: Int, month: Int): List<BudgetInfo> {
        return budgetRepository.observeByYearMonth(year, month).first().map {
            BudgetInfo(it.id, it.categoryId, it.year, it.month, it.budgetAmount)
        }
    }
}

@Singleton
class AppSalaryReadAdapter @Inject constructor(
    private val incomeRepository: IncomeRepository
) : SalaryReadApi {

    override suspend fun getSalary(year: Int, month: Int): Double? {
        return incomeRepository.getByYearMonth(year, month)?.salary
    }

    override suspend fun getCreditCardDueCarried(year: Int, month: Int): Double? {
        return incomeRepository.getByYearMonth(year, month)?.creditCardDueCarried
    }
}
