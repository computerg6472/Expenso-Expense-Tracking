package com.spendwise.app.data.cloud.model

/**
 * MONTH BUNDLE DATA MODEL – Expenso v4.0 Cloud Architecture
 *
 * Firestore structure under users/{uid}:
 *   meta/profile       – CloudBundleProfile
 *   config/main        – CloudConfig  (categories, settings, etc.)
 *   months/{yyyy-MM}   – CloudMonthBundle (salary + expenses map)
 *
 * Local Room DB is a rebuildable cache. Firestore is source of truth.
 */

// ---------------------------------------------------------------------------
// meta/profile document
// ---------------------------------------------------------------------------

data class CloudBundleProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val onboardingDone: Boolean = false,
    val schemaVersion: Int = 4,            // schema v4 = month-bundle format
    val lastBackupAt: Long = 0L,
    val lastWriterDeviceId: String = "",
    // Per-month checksums: key = "yyyy-MM", value = CRC32 of sorted expense ids
    val monthChecksums: Map<String, Long> = emptyMap(),
    // Per-month expense counts: key = "yyyy-MM", value = count
    val monthCounts: Map<String, Int> = emptyMap(),
    // Migration flags
    val migratedFromV2: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// ---------------------------------------------------------------------------
// config/main document
// ---------------------------------------------------------------------------

data class CloudConfig(
    val categories: List<CloudCategoryV4> = emptyList(),
    val settings: CloudSettingsV4 = CloudSettingsV4(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class CloudCategoryV4(
    val id: String = "",
    val name: String = "",
    val colorHex: String = "#2563EB",
    val icon: String = "category",
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val aliases: String = "",
    val budget: Double? = null,
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CloudSettingsV4(
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

// ---------------------------------------------------------------------------
// months/{yyyy-MM} document
// ---------------------------------------------------------------------------

/**
 * One Firestore document per calendar month.
 * The "expenses" field is a map: expenseId → CloudExpenseEntry
 * Each entry is kept small to stay well under the 1 MB document limit.
 *
 * Firestore field-level update: update("expenses.{uuid}", value)
 * So two phones editing different expenses in the same month never collide.
 *
 * Note: amounts are stored as-is (Double). No minor-units conversion needed
 * since the app already stores Double (we keep precision in 2 decimal places
 * enforced at UI level).
 */
data class CloudMonthBundle(
    val monthKey: String = "",           // "yyyy-MM"
    val year: Int = 0,
    val month: Int = 0,
    val salary: Double = 0.0,
    val notes: String = "",
    val expenses: Map<String, CloudExpenseEntry> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val checksum: Long = 0L,             // CRC32 of active expense ids
    val partIndex: Int = 0,             // 0 = main, 1+ = overflow parts
    val totalParts: Int = 1
)

/**
 * Compact expense entry stored in the month bundle's expenses map.
 * Keys mirror Expense entity; designed to be tiny (avg ~150 bytes per entry).
 * At 250 bytes per entry, a 600 KB limit = ~2400 expenses per month, which
 * is vastly more than any real user. Auto-split at 1800 expenses per part.
 */
data class CloudExpenseEntry(
    val amt: Double = 0.0,          // amount
    val cat: String = "",           // categoryId
    val src: String = "INCOME",     // paymentSource: INCOME | CREDIT_CARD
    val dt: Long = 0L,              // dateEpoch
    val yr: Int = 0,                // year (redundant but fast for queries)
    val mo: Int = 0,                // month (1-12)
    val note: String = "",
    val via: String = "",           // createdVia: e.g. "ai_chat", "manual", "upi"
    val dev: String = "",           // deviceId of last writer
    val upd: Long = System.currentTimeMillis(), // updatedAt
    val del: Boolean = false        // soft-delete tombstone
)

// ---------------------------------------------------------------------------
// Legacy v2 models (kept for migration read-path only)
// ---------------------------------------------------------------------------

data class LegacyCloudProfile(
    val uid: String = "",
    val onboardingDone: Boolean = false,
    val schemaVersion: Int = 2,
    val migrationV2Done: Boolean = false
)
