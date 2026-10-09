package com.spendwise.app.di

import com.spendwise.app.data.repository.*
import com.spendwise.app.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindIncomeRepository(impl: IncomeRepositoryImpl): IncomeRepository

    @Binds
    @Singleton
    abstract fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindCreditCardRepository(impl: CreditCardRepositoryImpl): CreditCardRepository

    @Binds
    @Singleton
    abstract fun bindBackupTriggerManager(impl: com.spendwise.app.data.backup.BackupTriggerManagerImpl): com.spendwise.app.data.backup.BackupTriggerManager
}
