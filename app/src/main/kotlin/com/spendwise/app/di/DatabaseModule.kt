package com.spendwise.app.di

import android.content.Context
import com.spendwise.app.data.local.SpendWiseDatabase
import com.spendwise.app.data.local.dao.*
import com.spendwise.app.data.security.DatabaseKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SpendWiseDatabase {
        val passphrase = runBlocking { DatabaseKeyProvider.getOrCreateDatabasePassphrase(context) }
        return SpendWiseDatabase.create(context, passphrase)
    }

    @Provides
    fun provideIncomeMonthDao(db: SpendWiseDatabase): IncomeMonthDao = db.incomeMonthDao()

    @Provides
    fun provideExpenseDao(db: SpendWiseDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideCategoryDao(db: SpendWiseDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideCreditCardLedgerDao(db: SpendWiseDatabase): CreditCardLedgerDao = db.creditCardLedgerDao()

    @Provides
    fun provideBudgetDao(db: SpendWiseDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideSyncOutboxDao(db: SpendWiseDatabase): SyncOutboxDao = db.syncOutboxDao()
}

