package com.spendwise.intelligence.domain.model

data class ExpenseRecord(
    val id: String = "",
    val amountMinor: Long,
    val categoryId: String,
    val source: PaymentSource,
    val dateEpochMillis: Long,
    val merchant: String? = null,
    val note: String = "",
    val createdVia: String = "MANUAL"
) {
    val amount: Double
        get() = amountMinor / 100.0

    companion object {
        fun fromDouble(
            id: String = "",
            amount: Double,
            categoryId: String,
            source: PaymentSource,
            dateEpochMillis: Long,
            merchant: String? = null,
            note: String = "",
            createdVia: String = "MANUAL"
        ): ExpenseRecord = ExpenseRecord(
            id = id,
            amountMinor = (amount * 100).toLong(),
            categoryId = categoryId,
            source = source,
            dateEpochMillis = dateEpochMillis,
            merchant = merchant,
            note = note,
            createdVia = createdVia
        )
    }
}
