package com.spendwise.app.domain.model

import com.spendwise.app.data.local.entity.Category
import com.spendwise.app.data.local.entity.Expense

data class ExpenseWithCategory(
    val expense: Expense,
    val category: Category
)
