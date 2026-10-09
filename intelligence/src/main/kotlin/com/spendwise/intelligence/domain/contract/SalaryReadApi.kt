package com.spendwise.intelligence.domain.contract

interface SalaryReadApi {
    suspend fun getSalary(year: Int, month: Int): Double?
    suspend fun getCreditCardDueCarried(year: Int, month: Int): Double?
}
