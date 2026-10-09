package com.spendwise.intelligence.domain.feature

import com.spendwise.intelligence.domain.model.ExpenseRecord
import com.spendwise.intelligence.domain.model.PaymentSource

enum class RecurringFrequency {
    WEEKLY,
    BI_WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY
}

data class RecurringExpense(
    val merchant: String,
    val categoryId: String,
    val averageAmount: Double,
    val frequency: RecurringFrequency,
    val nextExpectedDateEpoch: Long,
    val confidence: Float,
    val source: PaymentSource
)

interface RecurringDetector {
    /**
     * Detects regular or subscription patterns from historical expenses.
     */
    suspend fun detectRecurring(expenses: List<ExpenseRecord>): List<RecurringExpense>
}
