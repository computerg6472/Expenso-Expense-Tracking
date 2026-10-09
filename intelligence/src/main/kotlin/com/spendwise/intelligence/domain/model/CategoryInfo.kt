package com.spendwise.intelligence.domain.model

data class CategoryInfo(
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val colorHex: String? = null,
    val iconName: String? = null
)
