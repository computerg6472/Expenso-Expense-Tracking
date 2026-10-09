package com.spendwise.app.domain.repository

import com.spendwise.app.data.local.entity.CreditCardLedger
import kotlinx.coroutines.flow.Flow

interface CreditCardRepository {
    suspend fun getOrCreate(year: Int, month: Int): CreditCardLedger
    suspend fun updateTotal(year: Int, month: Int, total: Double)
    suspend fun markCarried(year: Int, month: Int)
    fun observeByYearMonth(year: Int, month: Int): Flow<CreditCardLedger?>
}
