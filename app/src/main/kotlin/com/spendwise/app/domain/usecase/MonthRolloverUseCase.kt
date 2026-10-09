package com.spendwise.app.domain.usecase

import com.spendwise.app.domain.repository.CreditCardRepository
import com.spendwise.app.domain.repository.ExpenseRepository
import com.spendwise.app.domain.repository.IncomeRepository
import java.util.Calendar
import javax.inject.Inject

/**
 * Month rollover logic:
 * At the start of a new month, carry the previous month's CC spend as a due
 * into the new month's income record.
 */
class MonthRolloverUseCase @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val creditCardRepository: CreditCardRepository
) {
    suspend operator fun invoke() {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1

        // Get previous month
        val prevCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val prevYear = prevCal.get(Calendar.YEAR)
        val prevMonth = prevCal.get(Calendar.MONTH) + 1

        // Check if rollover already done for current month
        val currentMonthRecord = incomeRepository.getByYearMonth(currentYear, currentMonth)
        val prevLedger = creditCardRepository.getOrCreate(prevYear, prevMonth)

        if (prevLedger.carriedToNextMonth) return // already rolled over

        val prevCCTotal = expenseRepository.getTotalCreditCardSpend(prevYear, prevMonth)

        // Mark previous month as carried
        creditCardRepository.markCarried(prevYear, prevMonth)

        // Add the CC due to current month
        if (currentMonthRecord != null) {
            incomeRepository.updateCreditCardDue(currentYear, currentMonth, prevCCTotal)
        } else {
            // Create current month with the due pre-filled
            incomeRepository.setSalary(currentYear, currentMonth, 0.0)
            incomeRepository.updateCreditCardDue(currentYear, currentMonth, prevCCTotal)
        }
    }
}
