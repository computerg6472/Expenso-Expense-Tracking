package com.spendwise.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "credit_card_ledger",
    indices = [
        Index(value = ["month_key"], unique = true),
        Index("is_deleted"),
        Index("updated_at")
    ]
)
data class CreditCardLedger(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "month") val month: Int,
    @ColumnInfo(name = "month_key") val monthKey: String = String.format("%04d-%02d", year, month),
    @ColumnInfo(name = "total_spend") val totalSpend: Double = 0.0,
    @ColumnInfo(name = "carried_to_next_month") val carriedToNextMonth: Boolean = false,
    @ColumnInfo(name = "device_id") val deviceId: String = "",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
