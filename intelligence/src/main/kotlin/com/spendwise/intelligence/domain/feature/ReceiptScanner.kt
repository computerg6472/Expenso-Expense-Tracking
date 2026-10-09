package com.spendwise.intelligence.domain.feature

data class ReceiptItem(
    val name: String,
    val amount: Double
)

data class ReceiptScanResult(
    val merchant: String?,
    val totalAmount: Double?,
    val dateEpochMillis: Long?,
    val items: List<ReceiptItem> = emptyList(),
    val rawText: String = "",
    val confidence: Float = 0f,
    val isFallback: Boolean = false
)

interface ReceiptScanner {
    /**
     * Extracts expense data (merchant, total, date, line items) from an on-device image or receipt.
     */
    suspend fun scanReceipt(imageBytes: ByteArray): ReceiptScanResult
}
