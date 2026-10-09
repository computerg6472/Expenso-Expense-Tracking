package com.spendwise.app.domain.model

data class MonthSummary(
    val year: Int,
    val month: Int,
    val salary: Double,
    val totalIncomeSpend: Double,
    val totalCreditCardSpend: Double,
    val creditCardDue: Double,
    val availableBalance: Double
)
