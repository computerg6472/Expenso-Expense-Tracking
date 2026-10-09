package com.spendwise.app.data.cloud.model

/**
 * Cloud Firestore Data Models under users/{uid}/...
 * Firestore is the single source of truth for all user financial data.
 */

data class CloudUserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val onboardingDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastBackupAt: Long = System.currentTimeMillis(),
    val schemaVersion: Int = 2,
    val migrationV2Done: Boolean = false
)

data class CloudMonth(
    val monthKey: String = "", // e.g. "2026-10"
    val year: Int = 0,
    val month: Int = 0,
    val salary: Double = 0.0,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

data class CloudExpense(
    val id: String = "", // UUID string
    val amount: Double = 0.0,
    val categoryId: String = "",
    val dateEpoch: Long = 0L,
    val year: Int = 0,
    val month: Int = 0,
    val paymentSource: String = "INCOME", // "INCOME" or "CREDIT_CARD"
    val note: String = "",
    val isEncrypted: Boolean = false, // True if encrypted in Private Mode
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false // Tombstone soft-delete
)

data class CloudCategory(
    val id: String = "", // UUID string or "cat_others"
    val name: String = "",
    val colorHex: String = "#2563EB",
    val icon: String = "category",
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val aliases: String = "",
    val budget: Double? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

data class CloudBudget(
    val id: String = "", // UUID string
    val categoryId: String = "",
    val monthlyLimit: Double = 0.0,
    val year: Int = 0,
    val month: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

data class CloudGoal(
    val id: String = "", // UUID string
    val title: String = "",
    val targetAmount: Double = 0.0,
    val savedAmount: Double = 0.0,
    val targetDateEpoch: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

data class CloudRecurring(
    val id: String = "", // UUID string
    val title: String = "",
    val amount: Double = 0.0,
    val categoryId: String = "",
    val frequency: String = "MONTHLY",
    val nextDueEpoch: Long = 0L,
    val paymentSource: String = "INCOME",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

data class CloudSettings(
    val currencySymbol: String = "₹",
    val theme: String = "SYSTEM",
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = true,
    val lockTimeoutSeconds: Int = 30,
    val dailyReminderEnabled: Boolean = true,
    val budgetAlertsEnabled: Boolean = true,
    val upiMonitorEnabled: Boolean = false,
    val aiFloatingButtonEnabled: Boolean = true,
    val geminiAiEnabled: Boolean = false,
    val privateModeEnabled: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CloudChatMessage(
    val id: String = "",
    val text: String = "",
    val isUser: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
