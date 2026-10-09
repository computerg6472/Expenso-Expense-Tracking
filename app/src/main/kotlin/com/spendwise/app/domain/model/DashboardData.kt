package com.spendwise.app.domain.model

data class DashboardData(
    val salary: Double,
    val extraCredit: Double = 0.0,
    val availableBalance: Double,
    val spentViaIncome: Double,
    val creditCardSpendThisMonth: Double,
    val creditCardDueCarried: Double,
    val creditLimit: Double = 50000.0,
    val availableCreditLimit: Double = 50000.0,
    val creditUtilizationPercentage: Double = 0.0,
    val pendingReceivableTotal: Double = 0.0,
    val year: Int,
    val month: Int
)
