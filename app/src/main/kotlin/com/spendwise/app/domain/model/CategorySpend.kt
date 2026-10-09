package com.spendwise.app.domain.model

data class CategorySpend(
    val categoryId: String,
    val categoryName: String,
    val colorHex: String,
    val iconName: String,
    val totalSpend: Double,
    val percentage: Double,
    val budget: Double? = null,
    val budgetPercentage: Double? = null
)
