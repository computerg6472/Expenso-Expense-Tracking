package com.spendwise.app.di

import com.spendwise.app.data.intelligence.AppBudgetReadAdapter
import com.spendwise.app.data.intelligence.AppCategoryReadAdapter
import com.spendwise.app.data.intelligence.AppExpenseReadAdapter
import com.spendwise.app.data.intelligence.AppSalaryReadAdapter
import com.spendwise.intelligence.domain.contract.BudgetReadApi
import com.spendwise.intelligence.domain.contract.CategoryReadApi
import com.spendwise.intelligence.domain.contract.ExpenseReadApi
import com.spendwise.intelligence.domain.contract.SalaryReadApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IntelligenceBridgeModule {

    @Binds
    @Singleton
    abstract fun bindExpenseReadApi(impl: AppExpenseReadAdapter): ExpenseReadApi

    @Binds
    @Singleton
    abstract fun bindCategoryReadApi(impl: AppCategoryReadAdapter): CategoryReadApi

    @Binds
    @Singleton
    abstract fun bindBudgetReadApi(impl: AppBudgetReadAdapter): BudgetReadApi

    @Binds
    @Singleton
    abstract fun bindSalaryReadApi(impl: AppSalaryReadAdapter): SalaryReadApi
}
