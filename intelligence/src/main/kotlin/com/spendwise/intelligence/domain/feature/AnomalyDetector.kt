package com.spendwise.intelligence.domain.feature

import com.spendwise.intelligence.domain.model.ExpenseRecord

enum class AnomalySeverity {
    LOW,
    MEDIUM,
    HIGH
}

data class ExpenseAnomaly(
    val expense: ExpenseRecord,
    val reason: String,
    val severity: AnomalySeverity,
    val expectedRange: ClosedFloatingPointRange<Double>,
    val deviationScore: Float
)

interface AnomalyDetector {
    /**
     * Checks if a new or existing expense is an anomaly compared to past category spending.
     */
    suspend fun evaluateExpense(
        expense: ExpenseRecord,
        history: List<ExpenseRecord>
    ): ExpenseAnomaly?
}
