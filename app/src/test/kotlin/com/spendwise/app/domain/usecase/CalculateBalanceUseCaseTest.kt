package com.spendwise.app.domain.usecase

import com.spendwise.app.data.local.entity.IncomeMonth
import com.spendwise.app.domain.repository.ExpenseRepository
import com.spendwise.app.domain.repository.IncomeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class CalculateBalanceUseCaseTest {

    private val incomeRepository = mock(IncomeRepository::class.java)
    private val expenseRepository = mock(ExpenseRepository::class.java)
    private val appPreferences = mock(com.spendwise.app.data.preferences.AppPreferences::class.java)
    private lateinit var useCase: CalculateBalanceUseCase

    @Before
    fun setUp() {
        useCase = CalculateBalanceUseCase(incomeRepository, expenseRepository, appPreferences)
    }

    @Test
    fun `available balance is correctly calculated as salary minus income spend minus cc dues`() = runTest {
        val year = 2026
        val month = 10
        val salary = 100000.0
        val ccDueCarried = 15000.0
        val incomeSpend = 30000.0
        val ccSpend = 12000.0
        val creditLimit = 50000.0

        val incomeMonth = IncomeMonth(
            year = year,
            month = month,
            salary = salary,
            creditCardDueCarried = ccDueCarried
        )

        `when`(incomeRepository.observeCurrentMonth()).thenReturn(flowOf(incomeMonth))
        `when`(expenseRepository.observeTotalIncomeSpend(year, month)).thenReturn(flowOf(incomeSpend))
        `when`(expenseRepository.observeTotalCreditCardSpend(year, month)).thenReturn(flowOf(ccSpend))
        `when`(appPreferences.creditLimit).thenReturn(flowOf(creditLimit))

        val result = useCase(year, month).first()

        assertEquals(salary, result.salary, 0.001)
        assertEquals(incomeSpend, result.spentViaIncome, 0.001)
        assertEquals(ccSpend, result.creditCardSpendThisMonth, 0.001)
        assertEquals(ccDueCarried, result.creditCardDueCarried, 0.001)
        
        // Expected available balance = 100,000 - 30,000 - 15,000 = 55,000
        val expectedAvailable = 100000.0 - 30000.0 - 15000.0
        assertEquals(expectedAvailable, result.availableBalance, 0.001)

        // Expected available credit limit = 50,000 - 12,000 = 38,000
        assertEquals(38000.0, result.availableCreditLimit, 0.001)
        // Utilization % = (12,000 / 50,000) * 100 = 24.0%
        assertEquals(24.0, result.creditUtilizationPercentage, 0.001)
    }

    @Test
    fun `available balance handles zero salary and zero expenses`() = runTest {
        val year = 2026
        val month = 10

        `when`(incomeRepository.observeCurrentMonth()).thenReturn(flowOf(null))
        `when`(expenseRepository.observeTotalIncomeSpend(year, month)).thenReturn(flowOf(0.0))
        `when`(expenseRepository.observeTotalCreditCardSpend(year, month)).thenReturn(flowOf(0.0))

        val result = useCase(year, month).first()

        assertEquals(0.0, result.salary, 0.001)
        assertEquals(0.0, result.availableBalance, 0.001)
        assertEquals(0.0, result.spentViaIncome, 0.001)
    }
}
