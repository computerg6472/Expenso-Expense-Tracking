package com.spendwise.app.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.spendwise.app.data.local.SpendWiseDatabase
import com.spendwise.app.data.local.dao.*
import com.spendwise.app.data.local.entity.*
import com.spendwise.app.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class FirebaseSyncResult {
    data class Success(
        val expensesCount: Int,
        val monthsCount: Int,
        val categoriesCount: Int,
        val timestamp: Long,
        val isRestore: Boolean = false
    ) : FirebaseSyncResult()

    data class Error(val message: String, val cause: Throwable? = null) : FirebaseSyncResult()
}

data class CloudBackupMeta(
    val lastBackupTimestamp: Long = 0L,
    val expensesCount: Int = 0,
    val monthsCount: Int = 0,
    val categoriesCount: Int = 0,
    val email: String = "",
    val exists: Boolean = false
)

@Singleton
class FirebaseCloudSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val database: SpendWiseDatabase,
    private val expenseDao: ExpenseDao,
    private val incomeMonthDao: IncomeMonthDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val creditCardLedgerDao: CreditCardLedgerDao,
    private val googleAuthManager: com.spendwise.app.data.auth.GoogleAuthManager
) {
    companion object {
        private const val TAG = "FirebaseSync"
        private const val CURRENT_SCHEMA_VERSION = 4
    }

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private suspend fun ensureFirebaseAuth(): String? {
        val currentUid = auth.currentUser?.uid
        if (!currentUid.isNullOrBlank()) return currentUid

        // 1. Try silent sign-in with Google to get fresh ID token with OAuth client ID
        try {
            val client = googleAuthManager.getGoogleSignInClient(context)
            val task = client.silentSignIn().await()
            val idToken = task.idToken
            if (!idToken.isNullOrBlank()) {
                val cred = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                val authRes = auth.signInWithCredential(cred).await()
                val uid = authRes.user?.uid
                if (!uid.isNullOrBlank()) {
                    Log.i(TAG, "FirebaseAuth session successfully established via silentSignIn: $uid")
                    return uid
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "silentSignIn failed: ${e.message}")
        }

        // 2. Try cached idToken from AppPreferences
        try {
            val cachedToken = appPreferences.googleIdToken.first()
            if (cachedToken.isNotBlank()) {
                val cred = com.google.firebase.auth.GoogleAuthProvider.getCredential(cachedToken, null)
                val authRes = auth.signInWithCredential(cred).await()
                val uid = authRes.user?.uid
                if (!uid.isNullOrBlank()) return uid
            }
        } catch (e: Exception) {
            Log.w(TAG, "cached token sign in failed: ${e.message}")
        }

        return auth.currentUser?.uid ?: appPreferences.userUid.first().ifBlank { null }
    }

    private suspend fun getCurrentUid(): String? {
        val fbUid = ensureFirebaseAuth()
        if (!fbUid.isNullOrBlank()) return fbUid
        val prefUid = appPreferences.userUid.first()
        if (prefUid.isNotBlank()) return prefUid
        val email = appPreferences.userEmail.first()
        if (email.isNotBlank()) return "user_" + email.replace(".", "_").replace("@", "_")
        return null
    }

    /**
     * Checks cloud for existing backup metadata without downloading the full dataset.
     */
    suspend fun getCloudBackupMeta(): CloudBackupMeta = withContext(Dispatchers.IO) {
        val uid = getCurrentUid() ?: return@withContext CloudBackupMeta()
        try {
            val doc = firestore.collection("users").document(uid)
                .collection("backup").document("meta")
                .get().await()

            if (doc.exists()) {
                val timestamp = doc.getLong("lastBackupTimestamp") ?: 0L
                val expenses = doc.getLong("expensesCount")?.toInt() ?: 0
                val months = doc.getLong("monthsCount")?.toInt() ?: 0
                val categories = doc.getLong("categoriesCount")?.toInt() ?: 0
                val email = doc.getString("email") ?: ""
                return@withContext CloudBackupMeta(
                    lastBackupTimestamp = timestamp,
                    expensesCount = expenses,
                    monthsCount = months,
                    categoriesCount = categories,
                    email = email,
                    exists = true
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching backup metadata: ${e.message}")
        }
        CloudBackupMeta()
    }

    /**
     * Backs up ALL local data (expenses, categories, income, budgets, credit cards, settings)
     * into Firestore under the user's private document tree (users/{uid}/).
     */
    suspend fun backupToCloud(): FirebaseSyncResult = withContext(Dispatchers.IO) {
        val uid = getCurrentUid()
        if (uid.isNullOrBlank()) {
            return@withContext FirebaseSyncResult.Error("Please sign in with Google to back up to cloud.")
        }

        try {
            // 1. Flush local WAL to ensure all latest transactions are clean
            database.checkpointWal(truncate = true)

            val now = System.currentTimeMillis()
            val deviceId = appPreferences.getOrCreateDeviceId()
            val userEmail = appPreferences.userEmail.first().ifBlank { auth.currentUser?.email.orEmpty() }

            // 2. Fetch all local tables including soft-deleted items for sync tombstones
            val categories = categoryDao.getAllIncludingDeleted()
            val expenses = expenseDao.getAllIncludingDeleted()
            val incomeMonths = incomeMonthDao.getAllIncludingDeleted()
            val budgets = budgetDao.getAllIncludingDeleted()
            val creditCardLedgers = creditCardLedgerDao.getAllIncludingDeleted()

            // 3. User settings & preferences
            val currencySymbol = appPreferences.currencySymbol.first()
            val creditLimit = appPreferences.creditLimit.first()
            val appLockEnabled = appPreferences.appLockEnabled.first()
            val biometricEnabled = appPreferences.biometricEnabled.first()
            val lockTimeout = appPreferences.lockTimeoutSeconds.first()
            val dailyReminder = appPreferences.dailyReminderEnabled.first()
            val budgetAlerts = appPreferences.budgetAlertsEnabled.first()
            val upiMonitor = appPreferences.upiMonitorEnabled.first()
            val overlay = appPreferences.overlayEnabled.first()
            val theme = appPreferences.theme.first()
            val aiFloating = appPreferences.aiFloatingButtonEnabled.first()
            val geminiAi = appPreferences.geminiAiEnabled.first()

            val userRef = firestore.collection("users").document(uid)
            val backupCol = userRef.collection("backup")

            // Document: Metadata
            val metaData = hashMapOf(
                "email" to userEmail,
                "uid" to uid,
                "deviceId" to deviceId,
                "schemaVersion" to CURRENT_SCHEMA_VERSION,
                "lastBackupTimestamp" to now,
                "expensesCount" to expenses.filter { !it.isDeleted }.size,
                "monthsCount" to incomeMonths.filter { !it.isDeleted }.size,
                "categoriesCount" to categories.filter { !it.isDeleted }.size,
                "budgetsCount" to budgets.size,
                "ledgersCount" to creditCardLedgers.size
            )
            backupCol.document("meta").set(metaData, SetOptions.merge()).await()

            // Document: Settings & Preferences
            val settingsData = hashMapOf(
                "currencySymbol" to currencySymbol,
                "creditLimit" to creditLimit,
                "appLockEnabled" to appLockEnabled,
                "biometricEnabled" to biometricEnabled,
                "lockTimeoutSeconds" to lockTimeout,
                "dailyReminderEnabled" to dailyReminder,
                "budgetAlertsEnabled" to budgetAlerts,
                "upiMonitorEnabled" to upiMonitor,
                "overlayEnabled" to overlay,
                "theme" to theme,
                "aiFloatingButtonEnabled" to aiFloating,
                "geminiAiEnabled" to geminiAi,
                "onboardingDone" to true,
                "updatedAt" to now
            )
            backupCol.document("settings").set(settingsData, SetOptions.merge()).await()

            // Document: Categories
            val categoriesPayload = categories.map { c ->
                hashMapOf(
                    "id" to c.id,
                    "name" to c.name,
                    "colorHex" to c.colorHex,
                    "iconName" to c.iconName,
                    "isDefault" to c.isDefault,
                    "sortOrder" to c.sortOrder,
                    "aliases" to c.aliases,
                    "budget" to c.budget,
                    "isDeleted" to c.isDeleted,
                    "createdAt" to c.createdAt,
                    "updatedAt" to c.updatedAt
                )
            }
            backupCol.document("categories").set(mapOf("list" to categoriesPayload), SetOptions.merge()).await()

            // Document: Income Months
            val incomePayload = incomeMonths.map { m ->
                hashMapOf(
                    "year" to m.year,
                    "month" to m.month,
                    "salary" to m.salary,
                    "extraCredit" to m.extraCredit,
                    "creditCardDueCarried" to m.creditCardDueCarried,
                    "isDeleted" to m.isDeleted,
                    "createdAt" to m.createdAt,
                    "updatedAt" to m.updatedAt
                )
            }
            backupCol.document("income_months").set(mapOf("list" to incomePayload), SetOptions.merge()).await()

            // Document: Budgets
            val budgetsPayload = budgets.map { b ->
                hashMapOf<String, Any>(
                    "id" to b.id,
                    "categoryId" to b.categoryId,
                    "year" to b.year,
                    "month" to b.month,
                    "monthKey" to b.monthKey,
                    "budgetAmount" to b.budgetAmount,
                    "deviceId" to b.deviceId,
                    "isDeleted" to b.isDeleted,
                    "createdAt" to b.createdAt,
                    "updatedAt" to b.updatedAt
                )
            }
            backupCol.document("budgets").set(mapOf("list" to budgetsPayload), SetOptions.merge()).await()

            // Document: Credit Card Ledgers
            val ccPayload = creditCardLedgers.map { cc ->
                hashMapOf<String, Any>(
                    "id" to cc.id,
                    "year" to cc.year,
                    "month" to cc.month,
                    "monthKey" to cc.monthKey,
                    "totalSpend" to cc.totalSpend,
                    "carriedToNextMonth" to cc.carriedToNextMonth,
                    "deviceId" to cc.deviceId,
                    "isDeleted" to cc.isDeleted,
                    "createdAt" to cc.createdAt,
                    "updatedAt" to cc.updatedAt
                )
            }
            backupCol.document("credit_card_ledgers").set(mapOf("list" to ccPayload), SetOptions.merge()).await()

            // Collection: Expenses by month (scalable chunking per month for unlimited capacity)
            val expensesByMonth = expenses.groupBy { it.monthKey }
            val monthsCol = userRef.collection("months")

            for ((monthKey, monthExpenses) in expensesByMonth) {
                val expList = monthExpenses.map { e ->
                    hashMapOf(
                        "id" to e.id,
                        "amount" to e.amount,
                        "paymentSource" to e.paymentSource,
                        "categoryId" to e.categoryId,
                        "dateEpoch" to e.dateEpoch,
                        "year" to e.year,
                        "month" to e.month,
                        "monthKey" to e.monthKey,
                        "note" to e.note,
                        "isDeleted" to e.isDeleted,
                        "createdAt" to e.createdAt,
                        "updatedAt" to e.updatedAt,
                        "isSplit" to e.isSplit,
                        "isReimbursed" to e.isReimbursed,
                        "receivableAmount" to e.receivableAmount,
                        "netUserExpense" to e.netUserExpense,
                        "extraCreditAmount" to e.extraCreditAmount
                    )
                }
                monthsCol.document(monthKey).set(mapOf("expenses" to expList), SetOptions.merge()).await()
            }

            // Record successful backup in preferences
            appPreferences.setLastSyncTime(now)
            Log.i(TAG, "Firebase Cloud Backup succeeded: ${expenses.size} expenses across ${expensesByMonth.size} months")

            FirebaseSyncResult.Success(
                expensesCount = expenses.filter { !it.isDeleted }.size,
                monthsCount = incomeMonths.filter { !it.isDeleted }.size,
                categoriesCount = categories.filter { !it.isDeleted }.size,
                timestamp = now,
                isRestore = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firebase backup failed: ${e.message}", e)
            FirebaseSyncResult.Error("Backup failed: ${e.message ?: "Unknown cloud error"}", e)
        }
    }

    /**
     * Restores ALL data (expenses, categories, income, budgets, credit cards, settings)
     * from Firestore down into the local Room database and SharedPreferences.
     */
    suspend fun restoreFromCloud(): FirebaseSyncResult = withContext(Dispatchers.IO) {
        val uid = getCurrentUid()
        if (uid.isNullOrBlank()) {
            return@withContext FirebaseSyncResult.Error("Please sign in with Google to restore from cloud.")
        }

        try {
            val userRef = firestore.collection("users").document(uid)
            val backupCol = userRef.collection("backup")

            // 1. Fetch metadata
            val metaDoc = backupCol.document("meta").get().await()
            if (!metaDoc.exists()) {
                return@withContext FirebaseSyncResult.Error("No existing cloud backup found for this Google account.")
            }

            val backupTimestamp = metaDoc.getLong("lastBackupTimestamp") ?: System.currentTimeMillis()

            // 2. Restore Categories first (foreign key dependency for expenses)
            val catDoc = backupCol.document("categories").get().await()
            if (catDoc.exists()) {
                val catList = catDoc.get("list") as? List<Map<String, Any>> ?: emptyList()
                val restoredCats = catList.mapNotNull { m ->
                    try {
                        Category(
                            id = m["id"] as? String ?: return@mapNotNull null,
                            name = m["name"] as? String ?: "Category",
                            colorHex = m["colorHex"] as? String ?: "#3B82F6",
                            iconName = m["iconName"] as? String ?: "category",
                            isDefault = (m["isDefault"] as? Boolean) ?: false,
                            sortOrder = (m["sortOrder"] as? Number)?.toInt() ?: 0,
                            aliases = m["aliases"] as? String ?: "",
                            budget = (m["budget"] as? Number)?.toDouble(),
                            isDeleted = (m["isDeleted"] as? Boolean) ?: false,
                            createdAt = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) { null }
                }
                if (restoredCats.isNotEmpty()) {
                    categoryDao.upsertAll(restoredCats)
                }
            }

            // 3. Restore Income Months
            val incDoc = backupCol.document("income_months").get().await()
            if (incDoc.exists()) {
                val incList = incDoc.get("list") as? List<Map<String, Any>> ?: emptyList()
                val restoredIncome = incList.mapNotNull { m ->
                    try {
                        IncomeMonth(
                            year = (m["year"] as? Number)?.toInt() ?: return@mapNotNull null,
                            month = (m["month"] as? Number)?.toInt() ?: return@mapNotNull null,
                            salary = (m["salary"] as? Number)?.toDouble() ?: 0.0,
                            extraCredit = (m["extraCredit"] as? Number)?.toDouble() ?: 0.0,
                            creditCardDueCarried = (m["creditCardDueCarried"] as? Number)?.toDouble() ?: 0.0,
                            isDeleted = (m["isDeleted"] as? Boolean) ?: false,
                            createdAt = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) { null }
                }
                if (restoredIncome.isNotEmpty()) {
                    incomeMonthDao.upsertAll(restoredIncome)
                }
            }

            // 4. Restore Budgets
            val budDoc = backupCol.document("budgets").get().await()
            if (budDoc.exists()) {
                val budList = budDoc.get("list") as? List<Map<String, Any>> ?: emptyList()
                for (m in budList) {
                    try {
                        val year = (m["year"] as? Number)?.toInt() ?: continue
                        val month = (m["month"] as? Number)?.toInt() ?: continue
                        val b = Budget(
                            id = m["id"] as? String ?: continue,
                            categoryId = m["categoryId"] as? String ?: continue,
                            year = year,
                            month = month,
                            monthKey = m["monthKey"] as? String ?: String.format("%04d-%02d", year, month),
                            budgetAmount = (m["budgetAmount"] as? Number)?.toDouble() ?: (m["amount"] as? Number)?.toDouble() ?: 0.0,
                            deviceId = m["deviceId"] as? String ?: "",
                            isDeleted = (m["isDeleted"] as? Boolean) ?: false,
                            createdAt = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                        budgetDao.upsert(b)
                    } catch (_: Exception) {}
                }
            }

            // 5. Restore Credit Card Ledgers
            val ccDoc = backupCol.document("credit_card_ledgers").get().await()
            if (ccDoc.exists()) {
                val ccList = ccDoc.get("list") as? List<Map<String, Any>> ?: emptyList()
                for (m in ccList) {
                    try {
                        val year = (m["year"] as? Number)?.toInt() ?: continue
                        val month = (m["month"] as? Number)?.toInt() ?: continue
                        val cc = CreditCardLedger(
                            id = m["id"] as? String ?: continue,
                            year = year,
                            month = month,
                            monthKey = m["monthKey"] as? String ?: String.format("%04d-%02d", year, month),
                            totalSpend = (m["totalSpend"] as? Number)?.toDouble() ?: (m["amount"] as? Number)?.toDouble() ?: 0.0,
                            carriedToNextMonth = (m["carriedToNextMonth"] as? Boolean) ?: false,
                            deviceId = m["deviceId"] as? String ?: "",
                            isDeleted = (m["isDeleted"] as? Boolean) ?: false,
                            createdAt = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                        creditCardLedgerDao.upsert(cc)
                    } catch (_: Exception) {}
                }
            }

            // 6. Restore Expenses from all months
            val monthsCol = userRef.collection("months").get().await()
            var totalRestoredExpenses = 0
            val allExpenses = mutableListOf<Expense>()

            for (monthDoc in monthsCol.documents) {
                val expList = monthDoc.get("expenses") as? List<Map<String, Any>> ?: emptyList()
                for (m in expList) {
                    try {
                        val e = Expense(
                            id = m["id"] as? String ?: continue,
                            amount = (m["amount"] as? Number)?.toDouble() ?: 0.0,
                            paymentSource = m["paymentSource"] as? String ?: "INCOME",
                            categoryId = m["categoryId"] as? String ?: "cat_others",
                            dateEpoch = (m["dateEpoch"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            year = (m["year"] as? Number)?.toInt() ?: 2026,
                            month = (m["month"] as? Number)?.toInt() ?: 10,
                            monthKey = m["monthKey"] as? String ?: "",
                            note = m["note"] as? String ?: "",
                            isDeleted = (m["isDeleted"] as? Boolean) ?: false,
                            createdAt = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            isSplit = (m["isSplit"] as? Boolean) ?: false,
                            isReimbursed = (m["isReimbursed"] as? Boolean) ?: false,
                            receivableAmount = (m["receivableAmount"] as? Number)?.toDouble() ?: 0.0,
                            netUserExpense = (m["netUserExpense"] as? Number)?.toDouble() ?: 0.0,
                            extraCreditAmount = (m["extraCreditAmount"] as? Number)?.toDouble() ?: 0.0
                        )
                        allExpenses.add(e)
                    } catch (_: Exception) {}
                }
            }

            if (allExpenses.isNotEmpty()) {
                expenseDao.upsertAll(allExpenses)
                totalRestoredExpenses = allExpenses.filter { !it.isDeleted }.size
            }

            // 7. Restore User Settings & Preferences
            val setDoc = backupCol.document("settings").get().await()
            if (setDoc.exists()) {
                setDoc.getString("currencySymbol")?.let { appPreferences.setCurrencySymbol(it) }
                setDoc.getDouble("creditLimit")?.let { appPreferences.setCreditLimit(it) }
                setDoc.getString("theme")?.let { appPreferences.setTheme(it) }
                setDoc.getBoolean("appLockEnabled")?.let { appPreferences.setAppLockEnabled(it) }
                setDoc.getBoolean("biometricEnabled")?.let { appPreferences.setBiometricEnabled(it) }
                setDoc.getLong("lockTimeoutSeconds")?.toInt()?.let { appPreferences.setLockTimeout(it) }
                setDoc.getBoolean("dailyReminderEnabled")?.let { appPreferences.setDailyReminderEnabled(it) }
                setDoc.getBoolean("budgetAlertsEnabled")?.let { appPreferences.setBudgetAlertsEnabled(it) }
                setDoc.getBoolean("upiMonitorEnabled")?.let { appPreferences.setUpiMonitorEnabled(it) }
                setDoc.getBoolean("overlayEnabled")?.let { appPreferences.setOverlayEnabled(it) }
                setDoc.getBoolean("aiFloatingButtonEnabled")?.let { appPreferences.setAiFloatingButtonEnabled(it) }
                setDoc.getBoolean("geminiAiEnabled")?.let { appPreferences.setGeminiAiEnabled(it) }
                appPreferences.setOnboardingDone(true)
            }

            // Flush database to disk
            database.checkpointWal(truncate = true)
            appPreferences.setLastSyncTime(backupTimestamp)

            Log.i(TAG, "Firebase Cloud Restore completed: $totalRestoredExpenses active expenses restored")

            FirebaseSyncResult.Success(
                expensesCount = totalRestoredExpenses,
                monthsCount = monthsCol.documents.size,
                categoriesCount = catDoc.get("list")?.let { (it as? List<*>)?.size } ?: 0,
                timestamp = backupTimestamp,
                isRestore = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firebase restore failed: ${e.message}", e)
            FirebaseSyncResult.Error("Restore failed: ${e.message ?: "Unknown cloud error"}", e)
        }
    }
}
