package com.spendwise.app.data.cloud.vault

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.spendwise.app.data.local.SpendWiseDatabase
import com.spendwise.app.data.local.dao.BudgetDao
import com.spendwise.app.data.local.dao.CategoryDao
import com.spendwise.app.data.local.dao.CreditCardLedgerDao
import com.spendwise.app.data.local.dao.ExpenseDao
import com.spendwise.app.data.local.dao.IncomeMonthDao
import com.spendwise.app.data.local.entity.Budget
import com.spendwise.app.data.local.entity.Category
import com.spendwise.app.data.local.entity.CreditCardLedger
import com.spendwise.app.data.local.entity.Expense
import com.spendwise.app.data.local.entity.IncomeMonth
import com.spendwise.app.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed class VaultRestoreResult {
    data class Success(
        val expensesRestored: Int,
        val monthsRestored: Int,
        val categoriesRestored: Int,
        val budgetsRestored: Int,
        val source: String,
        val timestamp: Long
    ) : VaultRestoreResult()
    object NotFound : VaultRestoreResult()
    data class Error(val message: String) : VaultRestoreResult()
}

data class BackupVaultInfo(
    val lastBackupTimestamp: Long = 0L,
    val expensesCount: Int = 0,
    val monthsCount: Int = 0,
    val categoriesCount: Int = 0,
    val isAvailable: Boolean = false,
    val storageLocation: String = "Google Cloud & Device Safe Vault"
)

@Singleton
class CloudBackupVaultManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val database: SpendWiseDatabase,
    private val expenseDao: ExpenseDao,
    private val incomeMonthDao: IncomeMonthDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val creditCardLedgerDao: CreditCardLedgerDao
) {
    companion object {
        private const val TAG = "CloudBackupVault"
        private const val VAULT_FOLDER_NAME = "ExpensoBackup"
        private const val CURRENT_SCHEMA_VERSION = 2
    }

    fun getEmailHash(email: String): String {
        val clean = email.trim().lowercase()
        if (clean.isBlank()) return "anonymous"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(clean.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun createSnapshotJson(email: String, uid: String): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val categories = categoryDao.getAllIncludingDeleted()
        val months = incomeMonthDao.getAllIncludingDeleted()
        val expenses = expenseDao.getAllIncludingDeleted()
        val budgets = budgetDao.getAllIncludingDeleted()
        val currencySymbol = appPreferences.currencySymbol.first()
        val aiFloatingButton = appPreferences.aiFloatingButtonEnabled.first()

        val root = JSONObject().apply {
            put("version", CURRENT_SCHEMA_VERSION)
            put("email", email)
            put("uid", uid)
            put("timestamp", now)
            put("onboardingDone", true)
            put("currencySymbol", currencySymbol)
            put("aiFloatingButtonEnabled", aiFloatingButton)

            val catArray = JSONArray()
            for (c in categories) {
                val obj = JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("colorHex", c.colorHex)
                    put("icon", c.iconName)
                    put("isDefault", c.isDefault)
                    put("sortOrder", c.sortOrder)
                    put("aliases", c.aliases)
                    if (c.budget != null) put("budget", c.budget)
                    put("isDeleted", c.isDeleted)
                    put("updatedAt", c.updatedAt)
                }
                catArray.put(obj)
            }
            put("categories", catArray)

            val monthArray = JSONArray()
            for (m in months) {
                val obj = JSONObject().apply {
                    put("id", m.id)
                    put("monthKey", m.monthKey)
                    put("year", m.year)
                    put("month", m.month)
                    put("salary", m.salary)
                    put("extraCredit", m.extraCredit)
                    put("isDeleted", m.isDeleted)
                    put("updatedAt", m.updatedAt)
                }
                monthArray.put(obj)
            }
            put("months", monthArray)

            val expArray = JSONArray()
            for (e in expenses) {
                val obj = JSONObject().apply {
                    put("id", e.id)
                    put("amount", e.amount)
                    put("categoryId", e.categoryId)
                    put("dateEpoch", e.dateEpoch)
                    put("year", e.year)
                    put("month", e.month)
                    put("monthKey", e.monthKey)
                    put("paymentSource", e.paymentSource)
                    put("note", e.note)
                    put("isSplit", e.isSplit)
                    put("isReimbursed", e.isReimbursed)
                    put("receivableAmount", e.receivableAmount)
                    put("netUserExpense", e.netUserExpense)
                    put("extraCreditAmount", e.extraCreditAmount)
                    put("isDeleted", e.isDeleted)
                    put("updatedAt", e.updatedAt)
                }
                expArray.put(obj)
            }
            put("expenses", expArray)

            val budgetArray = JSONArray()
            for (b in budgets) {
                val obj = JSONObject().apply {
                    put("id", b.id)
                    put("categoryId", b.categoryId)
                    put("year", b.year)
                    put("month", b.month)
                    put("monthKey", b.monthKey)
                    put("monthlyLimit", b.budgetAmount)
                    put("isDeleted", b.isDeleted)
                    put("updatedAt", b.updatedAt)
                }
                budgetArray.put(obj)
            }
            put("budgets", budgetArray)
        }

        root.toString(2)
    }

    suspend fun persistBackup(email: String, uid: String): Boolean = withContext(Dispatchers.IO) {
        if (email.isBlank() && uid.isBlank()) return@withContext false
        val hash = getEmailHash(if (email.isNotBlank()) email else uid)
        val fileName = "spendwise_vault_$hash.json"

        try {
            // Checkpoint WAL before creating snapshot and copying
            database.checkpointWal()
            val jsonStr = createSnapshotJson(email, uid)

            // 1. Save to App Internal Files (Included in Android Auto Backup to Google Drive)
            try {
                val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }
                File(vaultDir, fileName).writeText(jsonStr)
                File(context.filesDir, "cloud_vault_latest.json").writeText(jsonStr)
            } catch (e: Exception) {
                Log.w(TAG, "Failed saving internal vault: ${e.message}")
            }

            // 2. Save to Public Documents (Survives App Uninstall on Device)
            try {
                val docsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    VAULT_FOLDER_NAME
                ).apply { mkdirs() }
                File(docsDir, fileName).writeText(jsonStr)
            } catch (e: Exception) {
                Log.w(TAG, "Failed saving docs vault: ${e.message}")
            }

            // 3. Save to Public Downloads (Survives App Uninstall on Device!)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val resolver = context.contentResolver
                    val projection = arrayOf(MediaStore.MediaColumns._ID)
                    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                    val selectionArgs = arrayOf(fileName)
                    var existingUri: Uri? = null
                    resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                            existingUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        }
                    }

                    val targetUri = existingUri ?: run {
                        val values = android.content.ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$VAULT_FOLDER_NAME")
                        }
                        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    }

                    if (targetUri != null) {
                        resolver.openOutputStream(targetUri, "wt")?.use { stream ->
                            stream.write(jsonStr.toByteArray(Charsets.UTF_8))
                        }
                        Log.i(TAG, "Successfully mirrored vault via MediaStore to Downloads/$VAULT_FOLDER_NAME/$fileName")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed saving downloads vault via MediaStore: ${e.message}")
                }
            } else {
                try {
                    val dlDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        VAULT_FOLDER_NAME
                    ).apply { mkdirs() }
                    File(dlDir, fileName).writeText(jsonStr)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed saving downloads vault: ${e.message}")
                }
            }

            // 4. Save to External Files Directory
            try {
                val extDirs = context.getExternalFilesDirs(null)
                extDirs?.filterNotNull()?.forEach { dir ->
                    val targetDir = File(dir, VAULT_FOLDER_NAME).apply { mkdirs() }
                    File(targetDir, fileName).writeText(jsonStr)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed saving ext dirs: ${e.message}")
            }

            // 5. Update timestamp in preferences
            appPreferences.setLastSyncTime(System.currentTimeMillis())

            Log.i(TAG, "Backup snapshot saved successfully for $email (hash: $hash)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting backup: ${e.message}", e)
            false
        }
    }

    suspend fun restoreBackup(email: String, uid: String): VaultRestoreResult = withContext(Dispatchers.IO) {
        val hash = getEmailHash(if (email.isNotBlank()) email else uid)
        val fileName = "spendwise_vault_$hash.json"

        Log.i(TAG, "Searching for backup vaults for $email (hash: $hash)...")

        // 1. Check Candidate Vault Files across All Storage Tiers
        val candidateFiles = mutableListOf<Pair<File, String>>()

        // Tier A: Internal restored Google Auto-Backup files
        candidateFiles.add(File(context.filesDir, "cloud_vault_latest.json") to "Google Drive Auto-Backup")
        candidateFiles.add(File(File(context.filesDir, "vault"), fileName) to "App Internal Vault")

        // Tier B: Shared Documents directory (Preserved across uninstall/reinstall)
        val docsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            VAULT_FOLDER_NAME
        )
        candidateFiles.add(File(docsDir, fileName) to "Device Documents Safe Vault")

        // Tier C: Shared Downloads directory (Preserved across uninstall/reinstall)
        val dlDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            VAULT_FOLDER_NAME
        )
        candidateFiles.add(File(dlDir, fileName) to "Device Downloads Safe Vault")

        // Tier D: External app files
        context.getExternalFilesDirs(null)?.filterNotNull()?.forEach { dir ->
            candidateFiles.add(File(File(dir, VAULT_FOLDER_NAME), fileName) to "External Storage Safe Vault")
        }

        // Search through candidate files
        for ((file, sourceName) in candidateFiles) {
            if (file.exists() && file.length() > 0) {
                try {
                    val content = file.readText()
                    if (content.isNotBlank()) {
                        val result = parseAndRestoreJson(content, sourceName)
                        if (result is VaultRestoreResult.Success && (result.expensesRestored > 0 || result.monthsRestored > 0)) {
                            Log.i(TAG, "Successfully restored data from $sourceName (${file.absolutePath})")
                            // Re-persist across other tiers to heal any missing mirrors
                            persistBackup(email, uid)
                            return@withContext result
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading $sourceName from ${file.absolutePath}: ${e.message}")
                }
            }
        }

        // Tier E: Search MediaStore Downloads/Documents as fallback
        try {
            val mediaStoreResult = searchMediaStoreForVault(fileName, hash)
            if (mediaStoreResult != null) {
                val result = parseAndRestoreJson(mediaStoreResult, "Android MediaStore Vault")
                if (result is VaultRestoreResult.Success && (result.expensesRestored > 0 || result.monthsRestored > 0)) {
                    persistBackup(email, uid)
                    return@withContext result
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore search error: ${e.message}")
        }

        Log.i(TAG, "No historical backup found for $email across all tiers.")
        VaultRestoreResult.NotFound
    }
    private fun searchMediaStoreForVault(targetFileName: String, hash: String): String? {
        // Query 1: Exact hash match
        val content = queryMediaStoreUris("${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("%$hash%"))
        if (content != null) return content

        // Query 2: Fallback to any recent spendwise_vault file
        val vaultFallback = queryMediaStoreUris("${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("spendwise_vault_%"))
        if (vaultFallback != null) return vaultFallback

        // Query 3: Fallback to any recent Expenso exported JSON backup file
        return queryMediaStoreUris("${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("expenso_all_data_export_%"))
    }

    private fun queryMediaStoreUris(selection: String, selectionArgs: Array<String>): String? {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val urisToQuery = mutableListOf<Uri>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            urisToQuery.add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }
        urisToQuery.add(MediaStore.Files.getContentUri("external"))

        for (contentUri in urisToQuery) {
            try {
                context.contentResolver.query(
                    contentUri,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    var checked = 0
                    while (cursor.moveToNext() && checked < 5) {
                        checked++
                        val id = cursor.getLong(idCol)
                        val itemUri = ContentUris.withAppendedId(contentUri, id)
                        context.contentResolver.openInputStream(itemUri)?.use { stream ->
                            val text = stream.bufferedReader().readText()
                            if (text.isNotBlank() && (text.contains("expenses") || text.contains("months"))) {
                                return text
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore query failures
            }
        }
        return null
    }

    suspend fun parseAndRestoreJson(jsonStr: String, sourceName: String): VaultRestoreResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonStr)
            val timestamp = root.optLong("timestamp", System.currentTimeMillis())
            val currencySymbol = root.optString("currencySymbol", "₹")
            val aiFloating = root.optBoolean("aiFloatingButtonEnabled", true)

            // 1. Categories
            val catArray = root.optJSONArray("categories") ?: JSONArray()
            val roomCategories = mutableListOf<Category>()
            for (i in 0 until catArray.length()) {
                val obj = catArray.getJSONObject(i)
                val bVal = if (obj.has("budget") && !obj.isNull("budget")) obj.optDouble("budget") else null
                roomCategories.add(
                    Category(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        colorHex = obj.optString("colorHex", "#6B7280"),
                        iconName = obj.optString("icon", "category"),
                        isDefault = obj.optBoolean("isDefault", false),
                        sortOrder = obj.optInt("sortOrder", 0),
                        aliases = obj.optString("aliases", ""),
                        budget = bVal,
                        isDeleted = obj.optBoolean("isDeleted", false),
                        updatedAt = obj.optLong("updatedAt", timestamp)
                    )
                )
            }
            if (roomCategories.none { it.id == com.spendwise.app.domain.model.DefaultCategories.ID_OTHERS }) {
                roomCategories.add(com.spendwise.app.domain.model.DefaultCategories.CATEGORY_OTHERS)
            }

            // 2. Months (Salaries)
            val monthArray = root.optJSONArray("months") ?: JSONArray()
            val roomMonths = mutableListOf<IncomeMonth>()
            for (i in 0 until monthArray.length()) {
                val obj = monthArray.getJSONObject(i)
                roomMonths.add(
                    IncomeMonth(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        monthKey = obj.getString("monthKey"),
                        year = obj.getInt("year"),
                        month = obj.getInt("month"),
                        salary = obj.optDouble("salary", 0.0),
                        extraCredit = obj.optDouble("extraCredit", 0.0),
                        isDeleted = obj.optBoolean("isDeleted", false),
                        updatedAt = obj.optLong("updatedAt", timestamp)
                    )
                )
            }

            // 3. Expenses
            val expArray = root.optJSONArray("expenses") ?: JSONArray()
            val roomExpenses = mutableListOf<Expense>()
            for (i in 0 until expArray.length()) {
                val obj = expArray.getJSONObject(i)
                val yr = obj.getInt("year")
                val mth = obj.getInt("month")
                val mKey = obj.optString("monthKey", String.format("%04d-%02d", yr, mth))
                val amt = obj.getDouble("amount")
                val isSplitVal = obj.optBoolean("isSplit", false)
                val recAmt = obj.optDouble("receivableAmount", 0.0)
                val netAmt = obj.optDouble("netUserExpense", if (isSplitVal) (amt - recAmt).coerceAtLeast(0.0) else amt)
                roomExpenses.add(
                    Expense(
                        id = obj.getString("id"),
                        amount = amt,
                        categoryId = obj.getString("categoryId"),
                        dateEpoch = obj.getLong("dateEpoch"),
                        year = yr,
                        month = mth,
                        monthKey = mKey,
                        paymentSource = obj.optString("paymentSource", "INCOME"),
                        note = obj.optString("note", ""),
                        isSplit = isSplitVal,
                        isReimbursed = obj.optBoolean("isReimbursed", false),
                        receivableAmount = recAmt,
                        netUserExpense = netAmt,
                        extraCreditAmount = obj.optDouble("extraCreditAmount", 0.0),
                        isDeleted = obj.optBoolean("isDeleted", false),
                        updatedAt = obj.optLong("updatedAt", timestamp)
                    )
                )
            }

            // 4. Budgets
            val budgetArray = root.optJSONArray("budgets") ?: JSONArray()
            val roomBudgets = mutableListOf<Budget>()
            for (i in 0 until budgetArray.length()) {
                val obj = budgetArray.getJSONObject(i)
                val yr = obj.getInt("year")
                val mth = obj.getInt("month")
                val mKey = obj.optString("monthKey", String.format("%04d-%02d", yr, mth))
                roomBudgets.add(
                    Budget(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        categoryId = obj.getString("categoryId"),
                        year = yr,
                        month = mth,
                        monthKey = mKey,
                        budgetAmount = obj.getDouble("monthlyLimit"),
                        isDeleted = obj.optBoolean("isDeleted", false),
                        updatedAt = obj.optLong("updatedAt", timestamp)
                    )
                )
            }

            // Clear old cache before applying snapshot
            expenseDao.clearAll()
            incomeMonthDao.clearAll()
            categoryDao.clearAll()
            budgetDao.clearAll()
            creditCardLedgerDao.clearAll()

            // Populate Room
            if (roomCategories.isNotEmpty()) categoryDao.upsertAll(roomCategories)
            if (roomMonths.isNotEmpty()) incomeMonthDao.upsertAll(roomMonths)
            if (roomExpenses.isNotEmpty()) expenseDao.upsertAll(roomExpenses)
            if (roomBudgets.isNotEmpty()) budgetDao.upsertAll(roomBudgets)

            // Reconstruct Credit Card Ledgers
            val ccExpenses = roomExpenses.filter { !it.isDeleted && it.paymentSource == "CREDIT_CARD" }
            val byMonth = ccExpenses.groupBy { it.year to it.month }
            val ledgers = byMonth.map { (ym, list) ->
                CreditCardLedger(
                    year = ym.first,
                    month = ym.second,
                    monthKey = String.format("%04d-%02d", ym.first, ym.second),
                    totalSpend = list.sumOf { it.amount },
                    updatedAt = timestamp
                )
            }
            if (ledgers.isNotEmpty()) creditCardLedgerDao.upsertAll(ledgers)

            // Flush changes to SQLite main file
            database.checkpointWal()

            // Restore App Settings
            if (currencySymbol.isNotBlank()) {
                appPreferences.setCurrencySymbol(currencySymbol)
            }
            appPreferences.setAiFloatingButtonEnabled(aiFloating)
            appPreferences.setOnboardingDone(true)
            appPreferences.setLastSyncTime(timestamp)

            val nonDeletedExpenses = roomExpenses.count { !it.isDeleted }
            val nonDeletedMonths = roomMonths.count { !it.isDeleted }
            val nonDeletedCategories = roomCategories.count { !it.isDeleted }
            val nonDeletedBudgets = roomBudgets.count { !it.isDeleted }

            VaultRestoreResult.Success(
                expensesRestored = nonDeletedExpenses,
                monthsRestored = nonDeletedMonths,
                categoriesRestored = nonDeletedCategories,
                budgetsRestored = nonDeletedBudgets,
                source = sourceName,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from JSON ($sourceName): ${e.message}", e)
            VaultRestoreResult.Error("Corrupted backup data: ${e.message}")
        }
    }

    suspend fun getVaultInfo(email: String): BackupVaultInfo = withContext(Dispatchers.IO) {
        val hash = getEmailHash(email)
        val fileName = "spendwise_vault_$hash.json"
        val docsFile = File(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), VAULT_FOLDER_NAME),
            fileName
        )
        val internalFile = File(File(context.filesDir, "vault"), fileName)
        val latestFile = File(context.filesDir, "cloud_vault_latest.json")

        val target = when {
            latestFile.exists() -> latestFile
            internalFile.exists() -> internalFile
            docsFile.exists() -> docsFile
            else -> null
        }

        if (target != null && target.exists()) {
            try {
                val json = JSONObject(target.readText())
                val expArray = json.optJSONArray("expenses") ?: JSONArray()
                val monthArray = json.optJSONArray("months") ?: JSONArray()
                val catArray = json.optJSONArray("categories") ?: JSONArray()
                val ts = json.optLong("timestamp", target.lastModified())

                return@withContext BackupVaultInfo(
                    lastBackupTimestamp = ts,
                    expensesCount = expArray.length(),
                    monthsCount = monthArray.length(),
                    categoriesCount = catArray.length(),
                    isAvailable = true,
                    storageLocation = "Google Cloud & Device Safe Vault"
                )
            } catch (e: Exception) {
                // Ignore parse error for summary
            }
        }

        BackupVaultInfo(
            lastBackupTimestamp = appPreferences.lastSyncTime.first(),
            isAvailable = false
        )
    }
}
