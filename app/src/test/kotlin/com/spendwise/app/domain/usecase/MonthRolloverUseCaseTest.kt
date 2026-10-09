package com.spendwise.app.domain.usecase

import com.spendwise.app.data.local.entity.CreditCardLedger
import com.spendwise.app.data.local.entity.IncomeMonth
import com.spendwise.app.domain.repository.CreditCardRepository
import com.spendwise.app.domain.repository.ExpenseRepository
import com.spendwise.app.domain.repository.IncomeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class MonthRolloverUseCaseTest {

    private val incomeRepository = mock(IncomeRepository::class.java)
    private val expenseRepository = mock(ExpenseRepository::class.java)
    private val creditCardRepository = mock(CreditCardRepository::class.java)
    private lateinit var rolloverUseCase: MonthRolloverUseCase

    @Before
    fun setUp() {
        rolloverUseCase = MonthRolloverUseCase(
            incomeRepository,
            expenseRepository,
            creditCardRepository
        )
    }

    @Test
    fun `rollover does nothing if previous month already carried`() = runTest {
        val alreadyCarriedLedger = CreditCardLedger(
            year = 2026,
            month = 9,
            totalSpend = 25000.0,
            carriedToNextMonth = true
        )

        `when`(creditCardRepository.getOrCreate(anyInt(), anyInt())).thenReturn(alreadyCarriedLedger)

        rolloverUseCase()

        // Should not update credit card due
        verify(incomeRepository, never()).updateCreditCardDue(anyInt(), anyInt(), anyDouble())
    }
}
