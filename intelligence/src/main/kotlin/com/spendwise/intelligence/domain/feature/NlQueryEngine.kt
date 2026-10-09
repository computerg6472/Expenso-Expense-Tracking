package com.spendwise.intelligence.domain.feature

import com.spendwise.intelligence.domain.model.ExpenseRecord

data class NlQueryAnswer(
    val query: String,
    val answerText: String,
    val matchedExpenses: List<ExpenseRecord> = emptyList(),
    val totalAmount: Double? = null,
    val chartHint: String? = null
)

interface NlQueryEngine {
    /**
     * Answers conversational questions about spending (e.g. "How much did I spend on food last month?").
     */
    suspend fun answerQuery(query: String): NlQueryAnswer
}
