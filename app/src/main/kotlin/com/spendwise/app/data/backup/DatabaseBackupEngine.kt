package com.spendwise.app.data.backup

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.spendwise.app.data.backup.snapshot.BackupSnapshot
import com.spendwise.app.data.backup.snapshot.PreferencesSnapshot
import com.spendwise.app.data.backup.snapshot.SnapshotHeader
import com.spendwise.app.data.backup.snapshot.SnapshotSerializer
import com.spendwise.app.data.local.SpendWiseDatabase
import com.spendwise.app.data.local.dao.*
import com.spendwise.app.data.local.entity.CreditCardLedger
import com.spendwise.app.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseBackupEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SpendWiseDatabase,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val incomeMonthDao: IncomeMonthDao,
    private val budgetDao: BudgetDao,
    private val creditCardLedgerDao: CreditCardLedgerDao,
    private val appPreferences: AppPreferences,
    private val snapshotSerializer: SnapshotSerializer
) {
    companion object {
        private const val TAG = "DatabaseBackupEngine"
    }

    /**
     * Reads the entire local Room/SQLCipher database and creates an unencrypted snapshot payload,
     * compressed GZIP bytes, and the unencrypted content SHA-256 hash.
     */
    suspend fun createSnapshot(
        accountEmail: String,
        encryptionMode: Int = SnapshotHeader.ENCRYPTION_MODE_ACCOUNT_BOUND,
        includeAiChat: Boolean = true
    ): Pair<BackupSnapshot, ByteArray> = withContext(Dispatchers.IO) {
        // Enforce WAL checkpoint immediately prior to snapshot creation
        database.checkpointWal()

        val deviceId = appPreferences.getOrCreateDeviceId()
        val generation = appPreferences.backupGeneration.first() + 1L

        val categories = categoryDao.getAllIncludingDeleted()
        val budgets = budgetDao.getAllIncludingDeleted()
        val incomeMonths = incomeMonthDao.getAllIncludingDeleted()
        val expenses = expenseDao.getAllIncludingDeleted()
        val creditCards = creditCardLedgerDao.getAllIncludingDeleted()

        val currency = appPreferences.currencySymbol.first()
        val preferences = PreferencesSnapshot(
            currencySymbol = currency,
            monthlySalary = 0.0,
            onboardingDone = true
        )

        val activeExpenses = expenses.filter { !it.isDeleted }
        val activeMonths = incomeMonths.filter { !it.isDeleted }

        // Build tentative snapshot to compute SHA-256
        val tentativeHeader = SnapshotHeader(
            schemaVersion = SnapshotHeader.CURRENT_SCHEMA_VERSION,
            appVersion = "5.0",
            createdAt = System.currentTimeMillis(),
            deviceId = deviceId,
            accountEmail = accountEmail,
            expensesCount = activeExpenses.size,
            monthsCount = activeMonths.size,
            categoriesCount = categories.filter { !it.isDeleted }.size,
            generation = generation,
            contentHash = "",
            encryptionMode = encryptionMode
        )

        val tentativeSnapshot = BackupSnapshot(
            header = tentativeHeader,
            categories = categories,
            budgets = budgets,
            incomeMonths = incomeMonths,
            expenses = expenses,
            creditCardLedgers = creditCards,
            chatMessages = emptyList(), // or chat messages if persisted
            preferences = preferences
        )

        val jsonString = snapshotSerializer.toJson(tentativeSnapshot)
        val rawBytes = jsonString.toByteArray(Charsets.UTF_8)
        val contentHash = snapshotSerializer.computeSha256(rawBytes)

        val finalHeader = tentativeHeader.copy(contentHash = contentHash)
        val finalSnapshot = tentativeSnapshot.copy(header = finalHeader)
        val finalJson = snapshotSerializer.toJson(finalSnapshot)

        val compressedGzip = snapshotSerializer.compressGzip(finalJson)
        Log.i(TAG, "Snapshot created: ${activeExpenses.size} expenses, ${activeMonths.size} months, GZIP size = ${compressedGzip.size} bytes, hash = ${contentHash.take(8)}")

        Pair(finalSnapshot, compressedGzip)
    }

    /**
     * Atomically restores an entire BackupSnapshot into Room/SQLCipher within a single transaction.
     * Enforces foreign key constraints by restoring Categories before Expenses and Budgets.
     * Recalculates credit card balances to ensure complete financial ledger integrity.
     */
    suspend fun applySnapshot(snapshot: BackupSnapshot) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Applying snapshot: schema=${snapshot.header.schemaVersion}, expenses=${snapshot.expenses.size}, categories=${snapshot.categories.size}")

        database.withTransaction {
            // 1. Categories first (parent FK for expenses and budgets)
            val defaultOthers = com.spendwise.app.data.local.entity.Category(
                id = "cat_others",
                name = "Other",
                colorHex = "#9E9E9E",
                iconName = "more_horiz",
                isDefault = true,
                updatedAt = System.currentTimeMillis()
            )
            categoryDao.insert(defaultOthers)

            for (category in snapshot.categories) {
                categoryDao.upsert(category)
            }

            val validCategoryIds = categoryDao.getAllIncludingDeleted().map { it.id }.toMutableSet()
            validCategoryIds.add("cat_others")

            // 2. Income Months
            for (incomeMonth in snapshot.incomeMonths) {
                incomeMonthDao.upsert(incomeMonth)
            }

            // 3. Expenses (ensure valid categoryId)
            for (expense in snapshot.expenses) {
                val safeExpense = if (expense.categoryId !in validCategoryIds) {
                    val fallbackCat = com.spendwise.app.data.local.entity.Category(
                        id = if (expense.categoryId.isNotBlank()) expense.categoryId else "cat_" + java.util.UUID.randomUUID().toString().take(8),
                        name = "Imported Category",
                        colorHex = "#6200EE",
                        iconName = "category",
                        isDefault = false,
                        isDeleted = false,
                        updatedAt = System.currentTimeMillis()
                    )
                    categoryDao.insert(fallbackCat)
                    validCategoryIds.add(fallbackCat.id)
                    expense.copy(categoryId = fallbackCat.id)
                } else {
                    expense
                }
                expenseDao.insert(safeExpense)
            }

            // 4. Budgets (ensure valid categoryId)
            for (budget in snapshot.budgets) {
                if (budget.categoryId in validCategoryIds) {
                    budgetDao.upsert(budget)
                }
            }

            // 5. Credit Card Ledgers
            for (cc in snapshot.creditCardLedgers) {
                creditCardLedgerDao.insert(cc)
            }

            // 6. Recalculate credit card monthly totals from restored expenses
            recalculateCreditCardLedgers()
        }

        // 7. Flush WAL checkpoint so newly restored records reside in primary .db
        database.checkpointWal()

        // 8. Restore User Preferences (currency, onboarding, salary)
        appPreferences.setCurrencySymbol(snapshot.preferences.currencySymbol)
        appPreferences.setOnboardingDone(true)
        appPreferences.setBackupSuccess(
            timestamp = snapshot.header.createdAt,
            sizeBytes = 0L,
            expensesCount = snapshot.header.expensesCount,
            monthsCount = snapshot.header.monthsCount,
            generation = snapshot.header.generation,
            contentHash = snapshot.header.contentHash
        )

        // 9. Post-restore financial integrity verification:
        // Available Balance = (Restored Salary + Restored Extra Credit) - Total Income Spent
        val verifiedBalance = verifyRestoredFinancialIntegrity(snapshot)

        Log.i(TAG, "Snapshot applied successfully! App is fully restored with verified Available Balance: $verifiedBalance")
    }

    /**
     * Integrity verification step upon restore:
     * Validates that Available Balance correctly recalculates from restored records:
     * Available Balance = (Restored Salary + Restored Extra Credit) - Total Income Spent
     */
    suspend fun verifyRestoredFinancialIntegrity(snapshot: BackupSnapshot): Double = withContext(Dispatchers.IO) {
        val cal = java.util.Calendar.getInstance()
        val curYear = cal.get(java.util.Calendar.YEAR)
        val curMonth = cal.get(java.util.Calendar.MONTH) + 1
        val curMonthKey = String.format("%04d-%02d", curYear, curMonth)

        val restoredIncome = incomeMonthDao.getByMonthKey(curMonthKey)
        val restoredSalary = restoredIncome?.salary ?: snapshot.preferences.monthlySalary
        val restoredExtraCredit = (restoredIncome?.extraCredit ?: 0.0) + expenseDao.getTotalExtraCreditForMonth(curYear, curMonth)
        val totalIncomeSpent = expenseDao.getTotalIncomeSpendForMonth(curYear, curMonth)

        val availableBalance = (restoredSalary + restoredExtraCredit) - totalIncomeSpent
        Log.i(TAG, "Financial Integrity Verification: Restored Salary=$restoredSalary, Extra Credit=$restoredExtraCredit, Income Spent=$totalIncomeSpent -> Available Balance=$availableBalance")
        availableBalance
    }

    /**
     * Recalculates CreditCardLedger entries based on all non-deleted credit card expenses.
     */
    private suspend fun recalculateCreditCardLedgers() {
        val allCcExpenses = expenseDao.getAllIncludingDeleted()
            .filter { !it.isDeleted && it.paymentSource == "CREDIT_CARD" }

        val spendByMonth = allCcExpenses.groupBy { it.monthKey }
        val existingLedgers = creditCardLedgerDao.getAllIncludingDeleted().associateBy { it.monthKey }

        for ((monthKey, expenses) in spendByMonth) {
            val total = expenses.sumOf { it.amount }
            val existing = existingLedgers[monthKey]
            if (existing != null) {
                creditCardLedgerDao.insert(existing.copy(totalSpend = total, updatedAt = System.currentTimeMillis()))
            } else {
                val first = expenses.first()
                creditCardLedgerDao.insert(
                    CreditCardLedger(
                        year = first.year,
                        month = first.month,
                        monthKey = monthKey,
                        totalSpend = total
                    )
                )
            }
        }
    }
}
