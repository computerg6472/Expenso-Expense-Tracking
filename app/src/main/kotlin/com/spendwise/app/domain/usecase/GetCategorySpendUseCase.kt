package com.spendwise.app.domain.usecase

import com.spendwise.app.domain.model.CategorySpend
import com.spendwise.app.domain.repository.BudgetRepository
import com.spendwise.app.domain.repository.CategoryRepository
import com.spendwise.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetCategorySpendUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository
) {
    operator fun invoke(year: Int, month: Int): Flow<List<CategorySpend>> {
        val categoriesFlow = categoryRepository.observeAll()
        val expensesFlow = expenseRepository.observeByYearMonth(year, month)
        val budgetsFlow = budgetRepository.observeByYearMonth(year, month)

        return combine(categoriesFlow, expensesFlow, budgetsFlow) { categories, expenses, budgets ->
            val budgetMap = budgets.associateBy { it.categoryId }
            val totalSpend = expenses.sumOf { it.amount }.takeIf { it > 0 } ?: 1.0

            categories.map { category ->
                val spend = expenses.filter { it.categoryId == category.id }.sumOf { it.amount }
                val percentage = (spend / totalSpend) * 100.0
                val budget = budgetMap[category.id]?.budgetAmount
                val budgetPct = if (budget != null && budget > 0) (spend / budget) * 100.0 else null
                CategorySpend(
                    categoryId = category.id,
                    categoryName = category.name,
                    colorHex = category.colorHex,
                    iconName = category.iconName,
                    totalSpend = spend,
                    percentage = percentage,
                    budget = budget,
                    budgetPercentage = budgetPct
                )
            }.filter { it.totalSpend > 0 }.sortedByDescending { it.totalSpend }
        }
    }
}
