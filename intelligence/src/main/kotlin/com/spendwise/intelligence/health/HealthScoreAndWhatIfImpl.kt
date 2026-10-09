package com.spendwise.intelligence.health

import com.spendwise.intelligence.capability.AiFeature
import com.spendwise.intelligence.capability.CapabilityProvider
import com.spendwise.intelligence.domain.feature.FinancialHealthScore
import com.spendwise.intelligence.domain.feature.HealthMetric
import com.spendwise.intelligence.domain.feature.HealthScoreCalculator
import com.spendwise.intelligence.domain.feature.MonthForecast
import com.spendwise.intelligence.domain.feature.WhatIfResult
import com.spendwise.intelligence.domain.feature.WhatIfScenario
import com.spendwise.intelligence.domain.feature.WhatIfSimulator
import com.spendwise.intelligence.domain.model.BudgetInfo
import com.spendwise.intelligence.domain.model.ExpenseRecord
import com.spendwise.intelligence.domain.model.MonthSummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthScoreCalculatorImpl @Inject constructor(
    private val capabilityProvider: CapabilityProvider
) : HealthScoreCalculator {

    override suspend fun calculateScore(
        summary: MonthSummary,
        history: List<ExpenseRecord>,
        budgets: List<BudgetInfo>
    ): FinancialHealthScore {
        val salary = summary.salary.coerceAtLeast(1.0)
        val totalSpent = summary.totalIncomeSpend + summary.totalCreditCardSpend
        val balance = summary.availableBalance

        // 1. Savings / Surplus Metric (30% weight)
        val savingsRatio = (balance / salary).coerceIn(-1.0, 1.0)
        val savingsScore = when {
            savingsRatio >= 0.30 -> 100
            savingsRatio >= 0.20 -> 85
            savingsRatio >= 0.10 -> 70
            savingsRatio >= 0.0 -> 55
            else -> 25
        }

        // 2. Credit Card Exposure Metric (25% weight)
        val creditRatio = (summary.totalCreditCardSpend / salary).coerceIn(0.0, 1.0)
        val creditScore = when {
            creditRatio <= 0.15 -> 100
            creditRatio <= 0.30 -> 85
            creditRatio <= 0.50 -> 65
            creditRatio <= 0.70 -> 45
            else -> 20
        }

        // 3. Budget Adherence Metric (25% weight)
        var budgetScore = 80
        if (budgets.isNotEmpty()) {
            val categorySpends = history.groupBy { it.categoryId }
                .mapValues { (_, list) -> list.sumOf { it.amount } }

            var overBudgetCount = 0
            for (b in budgets) {
                val spent = categorySpends[b.categoryId] ?: 0.0
                if (spent > b.budgetAmount) overBudgetCount++
            }

            val adherenceRatio = 1.0 - (overBudgetCount.toDouble() / budgets.size)
            budgetScore = (adherenceRatio * 100).toInt().coerceIn(10, 100)
        }

        // 4. Spending Velocity / Buffer (20% weight)
        val burnRateScore = if (totalSpent <= salary * 0.75) 90 else if (totalSpent <= salary) 65 else 30

        val weightedScore = (
            savingsScore * 0.30 +
            creditScore * 0.25 +
            budgetScore * 0.25 +
            burnRateScore * 0.20
        ).toInt().coerceIn(0, 100)

        val rating = when {
            weightedScore >= 85 -> "Excellent"
            weightedScore >= 70 -> "Good"
            weightedScore >= 50 -> "Fair"
            else -> "Needs Attention"
        }

        val metrics = listOf(
            HealthMetric(
                name = "Savings Surplus",
                score = savingsScore,
                weight = 0.30f,
                detail = "${(savingsRatio * 100).toInt()}% of income retained"
            ),
            HealthMetric(
                name = "Credit Card Load",
                score = creditScore,
                weight = 0.25f,
                detail = "${(creditRatio * 100).toInt()}% card exposure vs salary"
            ),
            HealthMetric(
                name = "Budget Discipline",
                score = budgetScore,
                weight = 0.25f,
                detail = if (budgets.isNotEmpty()) "Budgets tracked and monitored" else "No category budgets set"
            ),
            HealthMetric(
                name = "Spending Buffer",
                score = burnRateScore,
                weight = 0.20f,
                detail = if (totalSpent <= salary) "Within monthly earnings" else "Exceeding monthly salary"
            )
        )

        val tips = mutableListOf<String>()
        if (savingsRatio < 0.20) tips.add("Aim to retain at least 20% of your salary into savings or investments.")
        if (creditRatio > 0.30) tips.add("Your credit card spend is above 30% of your salary. Watch for high dues next month.")
        if (budgets.isEmpty()) tips.add("Set category budgets in Settings to track your monthly spending discipline.")
        if (tips.isEmpty()) tips.add("Keep it up! Your spending and savings discipline are in great shape.")

        return FinancialHealthScore(
            overallScore = weightedScore,
            rating = rating,
            metrics = metrics,
            tips = tips
        )
    }
}

@Singleton
class WhatIfSimulatorImpl @Inject constructor() : WhatIfSimulator {

    override suspend fun simulate(
        baseForecast: MonthForecast,
        scenario: WhatIfScenario
    ): WhatIfResult {
        var savingsDelta = 0.0

        // Extra recurring cuts
        savingsDelta += scenario.recurringCutAmount

        // Income change
        val incomeDelta = scenario.salaryDelta


        // Category cuts
        for ((_, cutPercent) in scenario.categoryCutPercent) {
            // Assume 15% discretionary category average
            val estimatedCatSpend = baseForecast.projectedTotalSpend * 0.15
            savingsDelta += estimatedCatSpend * cutPercent.coerceIn(0f, 1f)
        }

        val newProjectedBalance = baseForecast.projectedAvailableBalance + savingsDelta + incomeDelta
        val annualImpact = savingsDelta * 12

        val impactSummary = when {
            savingsDelta > 0 -> "You could save ₹${savingsDelta.toInt()} extra this month (₹${annualImpact.toInt()}/year)!"
            savingsDelta < 0 -> "This adjustment increases spending by ₹${(-savingsDelta).toInt()} this month."
            else -> "No change to projected monthly spend."
        }

        return WhatIfResult(
            projectedSavingsDelta = savingsDelta,
            projectedNewBalance = newProjectedBalance,
            impactSummary = impactSummary
        )
    }
}
