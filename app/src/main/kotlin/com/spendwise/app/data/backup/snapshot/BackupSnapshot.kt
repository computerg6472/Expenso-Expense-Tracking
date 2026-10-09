package com.spendwise.app.data.backup.snapshot

import com.spendwise.app.data.local.entity.Budget
import com.spendwise.app.data.local.entity.Category
import com.spendwise.app.data.local.entity.CreditCardLedger
import com.spendwise.app.data.local.entity.Expense
import com.spendwise.app.data.local.entity.IncomeMonth

/**
 * Snapshot representation of an AI chat message for backup.
 */
data class ChatMessageSnapshot(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestamp: Long
)

/**
 * Metadata header stored with the snapshot.
 */
data class SnapshotHeader(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appVersion: String = "5.0",
    val createdAt: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val accountEmail: String = "",
    val expensesCount: Int = 0,
    val monthsCount: Int = 0,
    val categoriesCount: Int = 0,
    val generation: Long = 1L,
    val contentHash: String = "",
    val encryptionMode: Int = ENCRYPTION_MODE_ACCOUNT_BOUND
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 5
        const val ENCRYPTION_MODE_ACCOUNT_BOUND = 1
        const val ENCRYPTION_MODE_PASSWORD = 2
        const val ENCRYPTION_MODE_RECOVERY_KEY = 3
    }
}

/**
 * User preferences included in the snapshot so new phone needs ZERO onboarding setup.
 */
data class PreferencesSnapshot(
    val currencySymbol: String = "₹",
    val monthlySalary: Double = 0.0,
    val onboardingDone: Boolean = true,
    val themeMode: String = "SYSTEM",
    val biometricEnabled: Boolean = false,
    val creditCardCarriedForwardEnabled: Boolean = false
)

/**
 * Full unencrypted logical snapshot of the user's financial database.
 */
data class BackupSnapshot(
    val header: SnapshotHeader,
    val categories: List<Category> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val incomeMonths: List<IncomeMonth> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val creditCardLedgers: List<CreditCardLedger> = emptyList(),
    val chatMessages: List<ChatMessageSnapshot> = emptyList(),
    val preferences: PreferencesSnapshot = PreferencesSnapshot()
)
