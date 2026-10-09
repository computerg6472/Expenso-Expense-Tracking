package com.spendwise.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "budget",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["category_id", "month_key"], unique = true),
        Index("is_deleted"),
        Index("updated_at")
    ]
)
data class Budget(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "month") val month: Int,
    @ColumnInfo(name = "month_key") val monthKey: String = String.format("%04d-%02d", year, month),
    @ColumnInfo(name = "budget_amount") val budgetAmount: Double,
    @ColumnInfo(name = "device_id") val deviceId: String = "",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
