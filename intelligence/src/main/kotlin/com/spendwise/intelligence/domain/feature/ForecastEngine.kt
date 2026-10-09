package com.spendwise.intelligence.domain.feature

import com.spendwise.intelligence.domain.model.ExpenseRecord

data class ForecastConfidence(
    val p10: Double,
    val p50: Double,
    val p90: Double
)

data class DailyForecast(
    val dayOfMonth: Int,
    val projectedCumulativeSpend: Double,
    val lowerBound: Double,
    val upperBound: Double
)

data class MonthForecast(
    val year: Int,
    val month: Int,
    val projectedTotalSpend: Double,
    val projectedAvailableBalance: Double,
    val confidence: ForecastConfidence,
    val dailyProjections: List<DailyForecast>,
    val isDeficitProjected: Boolean
)

interface ForecastEngine {
    /**
     * Projects month-end spending, balance, and deficit risk based on history and pacing.
     */
    suspend fun forecastMonthEnd(
        year: Int,
        month: Int,
        currentSpend: Double,
        salary: Double,
        history: List<ExpenseRecord>
    ): MonthForecast
}
