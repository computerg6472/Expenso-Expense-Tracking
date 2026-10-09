package com.spendwise.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index("entity_id"),
        Index("entity_type"),
        Index("timestamp")
    ]
)
data class SyncOutbox(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "entity_type") val entityType: String, // "EXPENSE", "CATEGORY", "BUDGET", "INCOME_MONTH"
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "operation") val operation: String, // "UPSERT", "DELETE"
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0
)
