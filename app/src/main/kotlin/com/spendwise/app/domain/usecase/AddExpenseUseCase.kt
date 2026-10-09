package com.spendwise.app.domain.usecase

import com.spendwise.app.data.local.entity.Expense
import com.spendwise.app.domain.model.PaymentSource
import com.spendwise.app.domain.repository.CreditCardRepository
import com.spendwise.app.domain.repository.ExpenseRepository
import javax.inject.Inject

class AddExpenseUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val creditCardRepository: CreditCardRepository
) {
    suspend operator fun invoke(expense: Expense): String {
        val id = expenseRepository.addExpense(expense)
        if (expense.paymentSource == PaymentSource.CREDIT_CARD.name) {
            val total = expenseRepository.getTotalCreditCardSpend(expense.year, expense.month)
            creditCardRepository.updateTotal(expense.year, expense.month, total)
        }
        return id
    }
}
