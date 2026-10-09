package com.spendwise.intelligence.domain.feature

import com.spendwise.intelligence.domain.model.PaymentSource

data class ParsedExpense(
    val rawInput: String,
    val amount: Double?,
    val categoryHint: String?,
    val source: PaymentSource?,
    val merchant: String?,
    val note: String,
    val dateEpochMillis: Long?,
    val confidence: Float,
    val isFallback: Boolean
)

interface NlpExpenseParser {
    /**
     * Parses a natural language expense string (e.g., "Spent 450 at Starbucks on credit card yesterday")
     * into structured expense candidate data.
     */
    suspend fun parse(input: String): ParsedExpense
}
