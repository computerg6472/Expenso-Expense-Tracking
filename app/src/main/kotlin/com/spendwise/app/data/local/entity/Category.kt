package com.spendwise.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "category",
    indices = [
        Index("is_deleted"),
        Index("updated_at")
    ]
)
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "color_hex") val colorHex: String = "#6200EE",
    @ColumnInfo(name = "icon_name") val iconName: String = "category",
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "device_id") val deviceId: String = "",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(name = "aliases", defaultValue = "''") val aliases: String = "",
    @ColumnInfo(name = "budget") val budget: Double? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    val aliasList: List<String>
        get() = if (aliases.isBlank()) emptyList() else aliases.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
