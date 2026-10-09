package com.spendwise.intelligence.insight

import com.spendwise.intelligence.capability.AiFeature
import com.spendwise.intelligence.capability.CapabilityProvider
import com.spendwise.intelligence.domain.contract.BudgetReadApi
import com.spendwise.intelligence.domain.contract.CategoryReadApi
import com.spendwise.intelligence.domain.contract.ExpenseReadApi
import com.spendwise.intelligence.domain.contract.SalaryReadApi
import com.spendwise.intelligence.domain.feature.ActionProposal
import com.spendwise.intelligence.domain.feature.FinancialInsight
import com.spendwise.intelligence.domain.feature.InsightEngine
import com.spendwise.intelligence.domain.feature.InsightPriority
import com.spendwise.intelligence.domain.feature.InsightType
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightEngineImpl @Inject constructor(
    private val expenseReadApi: ExpenseReadApi,
    private val categoryReadApi: CategoryReadApi,
    private val budgetReadApi: BudgetReadApi,
    private val salaryReadApi: SalaryReadApi,
    private val capabilityProvider: CapabilityProvider
) : InsightEngine {

    override suspend fun generateInsights(): List<FinancialInsight> {
        val insights = mutableListOf<FinancialInsight>()
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val summary = expenseReadApi.getMonthSummary(year, month) ?: return emptyList()
        val budgets = budgetReadApi.getBudgets(year, month)
        val categories = categoryReadApi.getCategories().associateBy { it.id }
        val expenses = expenseReadApi.getAllExpenses().filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateEpochMillis }
            c.get(Calendar.YEAR) == year && (c.get(Calendar.MONTH) + 1) == month
        }

        // 1. Credit Card Due Alert
        if (summary.creditCardDue > 0) {
            insights.add(
                FinancialInsight(
                    id = UUID.randomUUID().toString(),
                    title = "Credit Card Due Carried",
                    message = "₹${summary.creditCardDue.toInt()} was carried from last month's card spend and deducted from your available balance.",
                    type = InsightType.SPENDING_ALERT,
                    priority = InsightPriority.HIGH,
                    actionProposal = ActionProposal(label = "Review Ledgers", actionType = "NAVIGATE_HISTORY")
                )
            )
        }

        // 2. Budget Pacing Checks
        val spendByCategory = expenses.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amount } }
        for (b in budgets) {
            val spent = spendByCategory[b.categoryId] ?: 0.0
            val catName = categories[b.categoryId]?.name ?: "Category"
            val percentUsed = if (b.budgetAmount > 0) (spent / b.budgetAmount) * 100.0 else 0.0

            if (percentUsed >= 100.0) {
                insights.add(
                    FinancialInsight(
                        id = UUID.randomUUID().toString(),
                        title = "$catName Budget Exceeded",
                        message = "You've spent ₹${spent.toInt()} of your ₹${b.budgetAmount.toInt()} budget ($dayOfMonth/$maxDays days into the month).",
                        type = InsightType.BUDGET_PACING,
                        priority = InsightPriority.URGENT,
                        actionProposal = ActionProposal(label = "Adjust Budget", actionType = "NAVIGATE_BUDGETS")
                    )
                )
            } else if (percentUsed >= 80.0 && dayOfMonth <= (maxDays / 2)) {
                insights.add(
                    FinancialInsight(
                        id = UUID.randomUUID().toString(),
                        title = "$catName Pacing Fast",
                        message = "You've used ${percentUsed.toInt()}% of your $catName budget with ${maxDays - dayOfMonth} days remaining.",
                        type = InsightType.BUDGET_PACING,
                        priority = InsightPriority.MEDIUM
                    )
                )
            }
        }

        // 3. Overall Velocity / Surplus Milestone
        val totalSpent = summary.totalIncomeSpend + summary.totalCreditCardSpend
        if (summary.salary > 0) {
            val spendRatio = totalSpent / summary.salary
            if (dayOfMonth >= 20 && spendRatio <= 0.60) {
                insights.add(
                    FinancialInsight(
                        id = UUID.randomUUID().toString(),
                        title = "Healthy Savings Trajectory",
                        message = "You're at day $dayOfMonth and have spent only ${(spendRatio * 100).toInt()}% of your income. Great job saving!",
                        type = InsightType.MILESTONE,
                        priority = InsightPriority.LOW
                    )
                )
            } else if (spendRatio >= 0.90 && dayOfMonth < 25) {
                insights.add(
                    FinancialInsight(
                        id = UUID.randomUUID().toString(),
                        title = "High Spending Velocity",
                        message = "You have used ${(spendRatio * 100).toInt()}% of your monthly income. Consider slowing discretionary spending.",
                        type = InsightType.SPENDING_ALERT,
                        priority = InsightPriority.HIGH
                    )
                )
            }
        }

        return insights.sortedByDescending { it.priority.ordinal }
    }
}
