package com.spendwise.intelligence.domain.contract

import com.spendwise.intelligence.domain.model.CategoryInfo
import kotlinx.coroutines.flow.Flow

interface CategoryReadApi {
    fun observeCategories(): Flow<List<CategoryInfo>>
    suspend fun getCategories(): List<CategoryInfo>
    suspend fun getCategoryById(id: String): CategoryInfo?
}
