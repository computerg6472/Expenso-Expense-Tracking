package com.spendwise.intelligence.domain.feature

import com.spendwise.intelligence.domain.model.CategoryInfo

data class CategoryConfidence(
    val category: CategoryInfo,
    val confidence: Float,
    val matchedKeyword: String? = null
)

interface SmartCategorizer {
    /**
     * Recommends ranked categories given merchant name, expense note, and amount.
     */
    suspend fun suggestCategory(
        merchant: String?,
        note: String?,
        amount: Double,
        categories: List<CategoryInfo>
    ): List<CategoryConfidence>
}
