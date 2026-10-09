package com.spendwise.intelligence.domain.feature

import com.spendwise.intelligence.domain.model.BudgetInfo
import com.spendwise.intelligence.domain.model.ExpenseRecord
import com.spendwise.intelligence.domain.model.MonthSummary

data class HealthMetric(
    val name: String,
    val score: Int, // 0 - 100
    val maxScore: Int = 100,
    val weight: Float,
    val detail: String
)

data class FinancialHealthScore(
    val overallScore: Int, // 0 - 100
    val rating: String,    // "Excellent", "Good", "Fair", "Needs Attention"
    val metrics: List<HealthMetric>,
    val tips: List<String>
)

interface HealthScoreCalculator {
    suspend fun calculateScore(
        summary: MonthSummary,
        history: List<ExpenseRecord>,
        budgets: List<BudgetInfo>
    ): FinancialHealthScore
}

data class WhatIfScenario(
    val categoryCutPercent: Map<Long, Float> = emptyMap(), // categoryId -> percentage to cut (e.g., 0.20 for 20%)
    val salaryDelta: Double = 0.0,
    val recurringCutAmount: Double = 0.0
)

data class WhatIfResult(
    val projectedSavingsDelta: Double,
    val projectedNewBalance: Double,
    val impactSummary: String
)

interface WhatIfSimulator {
    suspend fun simulate(
        baseForecast: MonthForecast,
        scenario: WhatIfScenario
    ): WhatIfResult
}
