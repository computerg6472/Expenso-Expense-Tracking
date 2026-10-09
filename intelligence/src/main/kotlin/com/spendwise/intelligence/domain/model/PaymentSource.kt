package com.spendwise.intelligence.domain.model

enum class PaymentSource(val label: String) {
    INCOME("Income"),
    CREDIT_CARD("Credit Card");

    companion object {
        fun fromString(value: String): PaymentSource {
            return if (value.equals("CREDIT_CARD", ignoreCase = true) || value.contains("credit", ignoreCase = true)) {
                CREDIT_CARD
            } else {
                INCOME
            }
        }
    }
}
