package com.spendwise.app.domain.usecase

import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.model.DashboardData
import com.spendwise.app.domain.repository.ExpenseRepository
import com.spendwise.app.domain.repository.IncomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import javax.inject.Inject

class CalculateBalanceUseCase @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val appPreferences: AppPreferences
) {
    operator fun invoke(year: Int, month: Int): Flow<DashboardData> {
        val incomeFlow = incomeRepository.observeCurrentMonth()
        val incomeSpendFlow = expenseRepository.observeTotalIncomeSpend(year, month)
        val creditSpendFlow = expenseRepository.observeTotalCreditCardSpend(year, month)
        val limitFlow = appPreferences.creditLimit
        val pendingReceivableFlow = expenseRepository.observeTotalPendingReceivable()

        return combine(incomeFlow, incomeSpendFlow, creditSpendFlow, limitFlow, pendingReceivableFlow) { incomeMonth, incomeSpend, creditSpend, limit, pendingReceivable ->
            val salary = incomeMonth?.salary ?: 0.0
            val extraCredit = incomeMonth?.extraCredit ?: 0.0
            val creditDue = incomeMonth?.creditCardDueCarried ?: 0.0
            // Available Balance = (Restored/Live Salary + Extra Credit) - Total Income Spent - Credit Due
            val available = (salary + extraCredit) - incomeSpend - creditDue
            val availCreditLimit = (limit - creditSpend).coerceAtLeast(0.0)
            val utilization = if (limit > 0) ((creditSpend / limit) * 100.0).coerceIn(0.0, 100.0) else 0.0

            DashboardData(
                salary = salary,
                extraCredit = extraCredit,
                availableBalance = available,
                spentViaIncome = incomeSpend,
                creditCardSpendThisMonth = creditSpend,
                creditCardDueCarried = creditDue,
                creditLimit = limit,
                availableCreditLimit = availCreditLimit,
                creditUtilizationPercentage = utilization,
                pendingReceivableTotal = pendingReceivable,
                year = year,
                month = month
            )
        }
    }
}
