package com.spendwise.intelligence.domain.model

data class BudgetInfo(
    val id: String = "",
    val categoryId: String,
    val year: Int,
    val month: Int,
    val budgetAmount: Double
)
