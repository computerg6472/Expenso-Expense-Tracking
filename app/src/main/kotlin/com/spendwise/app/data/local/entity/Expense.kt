package com.spendwise.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "expense",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("category_id"),
        Index("date_epoch"),
        Index("payment_source"),
        Index("month_key"),
        Index("is_deleted"),
        Index("updated_at"),
        Index("is_split"),
        Index("is_reimbursed")
    ]
)
data class Expense(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "payment_source") val paymentSource: String, // "INCOME" | "CREDIT_CARD"
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "date_epoch") val dateEpoch: Long, // epoch millis
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "month") val month: Int, // 1-12
    @ColumnInfo(name = "month_key") val monthKey: String = String.format("%04d-%02d", year, month), // "YYYY-MM"
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "device_id") val deviceId: String = "",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),

    // Split Expenses & Wallet Money primitive columns (optimized footprint: SQLite INTEGER 0/1 & compact numeric)
    @ColumnInfo(name = "is_split", defaultValue = "0") val isSplit: Boolean = false,
    @ColumnInfo(name = "is_reimbursed", defaultValue = "0") val isReimbursed: Boolean = false,
    @ColumnInfo(name = "receivable_amount", defaultValue = "0.0") val receivableAmount: Double = 0.0,
    @ColumnInfo(name = "net_user_expense", defaultValue = "0.0") val netUserExpense: Double = 0.0,
    @ColumnInfo(name = "extra_credit_amount", defaultValue = "0.0") val extraCreditAmount: Double = 0.0
)
