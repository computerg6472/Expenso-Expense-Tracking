package com.spendwise.app.data.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import com.spendwise.app.data.backup.BackupTriggerManager
import com.spendwise.app.data.cloud.vault.CloudBackupVaultManager
import com.spendwise.app.data.local.SpendWiseDatabase
import com.spendwise.app.data.local.dao.*
import com.spendwise.app.data.local.entity.*
import com.spendwise.app.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

sealed class ImportResult {
    data class Success(val expensesCount: Int, val monthsCount: Int, val categoriesCount: Int) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

sealed class ExportResult {
    data class Success(val file: File, val uri: Uri, val totalExpenses: Int, val totalMonths: Int) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

@Singleton
class DataExportImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SpendWiseDatabase,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val incomeMonthDao: IncomeMonthDao,
    private val budgetDao: BudgetDao,
    private val creditCardLedgerDao: CreditCardLedgerDao,
    private val backupTriggerManager: BackupTriggerManager,
    private val cloudBackupVaultManager: CloudBackupVaultManager,
    private val appPreferences: AppPreferences
) {
    private val TAG = "DataExportImport"
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Export all data across all months to a structured JSON file that supports re-importing.
     * Includes date, time, category, payment source, note, salaries, budgets, etc.
     */
    suspend fun exportAllDataJson(): ExportResult = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val nowFormatted = dateTimeFormat.format(Date(now))
            val fileStamp = fileTimestampFormat.format(Date(now))

            val allExpenses = expenseDao.getAllIncludingDeleted().filter { !it.isDeleted }
            val allCategories = categoryDao.getAllIncludingDeleted().filter { !it.isDeleted }
            val allMonths = incomeMonthDao.getAllIncludingDeleted().filter { !it.isDeleted }
            val allBudgets = budgetDao.getAllIncludingDeleted().filter { !it.isDeleted }

            val categoriesById = allCategories.associateBy { it.id }
            val currencySymbol = appPreferences.currencySymbol.first()

            val root = JSONObject().apply {
                put("app", "Expenso")
                put("exportVersion", 4)
                put("exportedAtEpoch", now)
                put("exportedAtDateTime", nowFormatted)
                put("currencySymbol", currencySymbol)
                put("totalExpenses", allExpenses.size)
                put("totalMonths", allMonths.size)
                put("totalCategories", allCategories.size)

                // 1. Categories
                val catArray = JSONArray()
                for (cat in allCategories) {
                    val catObj = JSONObject().apply {
                        put("id", cat.id)
                        put("name", cat.name)
                        put("colorHex", cat.colorHex)
                        put("iconName", cat.iconName)
                        put("isDefault", cat.isDefault)
                        put("sortOrder", cat.sortOrder)
                        put("aliases", cat.aliases)
                        if (cat.budget != null) put("budget", cat.budget)
                        put("updatedAt", cat.updatedAt)
                    }
                    catArray.put(catObj)
                }
                put("categories", catArray)

                // 2. Months (Salaries)
                val monthArray = JSONArray()
                for (m in allMonths) {
                    val mObj = JSONObject().apply {
                        put("id", m.id)
                        put("monthKey", m.monthKey)
                        put("year", m.year)
                        put("month", m.month)
                        put("salary", m.salary)
                        put("creditCardDueCarried", m.creditCardDueCarried)
                        put("extraCredit", m.extraCredit)
                        put("updatedAt", m.updatedAt)
                    }
                    monthArray.put(mObj)
                }
                put("months", monthArray)

                // 3. Expenses (All fields, date, time, category name, split & credit fields)
                val expArray = JSONArray()
                for (e in allExpenses) {
                    val eDate = Date(e.dateEpoch)
                    val catName = categoriesById[e.categoryId]?.name ?: "Uncategorized"
                    val catIcon = categoriesById[e.categoryId]?.iconName ?: "category"
                    val catColor = categoriesById[e.categoryId]?.colorHex ?: "#6200EE"

                    val expObj = JSONObject().apply {
                        put("id", e.id)
                        put("amount", e.amount)
                        put("categoryId", e.categoryId)
                        put("categoryName", catName)
                        put("categoryIcon", catIcon)
                        put("categoryColor", catColor)
                        put("paymentSource", e.paymentSource)
                        put("dateEpoch", e.dateEpoch)
                        put("date", dateFormat.format(eDate))
                        put("time", timeFormat.format(eDate))
                        put("dateTime", dateTimeFormat.format(eDate))
                        put("year", e.year)
                        put("month", e.month)
                        put("monthKey", e.monthKey)
                        put("note", e.note)
                        put("deviceId", e.deviceId)
                        put("updatedAt", e.updatedAt)
                        put("isSplit", e.isSplit)
                        put("isReimbursed", e.isReimbursed)
                        put("receivableAmount", e.receivableAmount)
                        put("netUserExpense", e.netUserExpense)
                        put("extraCreditAmount", e.extraCreditAmount)
                    }
                    expArray.put(expObj)
                }
                put("expenses", expArray)

                // 4. Budgets
                val budgetArray = JSONArray()
                for (b in allBudgets) {
                    val catName = categoriesById[b.categoryId]?.name ?: "Uncategorized"
                    val bObj = JSONObject().apply {
                        put("id", b.id)
                        put("categoryId", b.categoryId)
                        put("categoryName", catName)
                        put("monthKey", b.monthKey)
                        put("year", b.year)
                        put("month", b.month)
                        put("budgetAmount", b.budgetAmount)
                        put("updatedAt", b.updatedAt)
                    }
                    budgetArray.put(bObj)
                }
                put("budgets", budgetArray)
            }

            val jsonContent = root.toString(2)
            val fileName = "expenso_all_data_export_$fileStamp.json"

            // 1. Cache directory file for FileProvider sharing
            val cacheFile = File(context.cacheDir, fileName)
            cacheFile.writeText(jsonContent)

            // 2. Also save to public Downloads for direct user access
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ExpensoExport")
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { it.write(jsonContent.toByteArray(Charsets.UTF_8)) }
                    }
                } else {
                    val downloadsDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "ExpensoExport"
                    ).apply { mkdirs() }
                    File(downloadsDir, fileName).writeText(jsonContent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not mirror export to public Downloads: ${e.message}")
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
            ExportResult.Success(
                file = cacheFile,
                uri = uri,
                totalExpenses = allExpenses.size,
                totalMonths = allMonths.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "exportAllDataJson failed: ${e.message}", e)
            ExportResult.Error(e.message ?: "Export failed")
        }
    }

    /**
     * Export all data across all months to a comprehensive CSV spreadsheet.
     */
    suspend fun exportAllDataCsv(): ExportResult = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val fileStamp = fileTimestampFormat.format(Date(now))

            val allExpenses = expenseDao.getAllIncludingDeleted().filter { !it.isDeleted }
                .sortedByDescending { it.dateEpoch }
            val allCategories = categoryDao.getAllIncludingDeleted().associateBy { it.id }
            val allMonths = incomeMonthDao.getAllIncludingDeleted().associateBy { it.monthKey }

            val fileName = "expenso_all_months_expenses_$fileStamp.csv"
            val cacheFile = File(context.cacheDir, fileName)

            CSVWriter(FileWriter(cacheFile)).use { writer ->
                writer.writeNext(
                    arrayOf(
                        "Date",
                        "Time",
                        "Month",
                        "Amount",
                        "Category",
                        "Payment Source",
                        "Note",
                        "Month Salary"
                    )
                )
                for (exp in allExpenses) {
                    val expDate = Date(exp.dateEpoch)
                    val catName = allCategories[exp.categoryId]?.name ?: "Uncategorized"
                    val monthSalary = allMonths[exp.monthKey]?.salary?.toString() ?: "0.0"

                    writer.writeNext(
                        arrayOf(
                            dateFormat.format(expDate),
                            timeFormat.format(expDate),
                            exp.monthKey,
                            exp.amount.toString(),
                            catName,
                            exp.paymentSource,
                            exp.note,
                            monthSalary
                        )
                    )
                }
            }

            // Copy to public Downloads
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ExpensoExport")
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            cacheFile.inputStream().use { input -> input.copyTo(out) }
                        }
                    }
                } else {
                    val downloadsDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "ExpensoExport"
                    ).apply { mkdirs() }
                    cacheFile.copyTo(File(downloadsDir, fileName), overwrite = true)
                }
            } catch (_: Exception) {}

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
            ExportResult.Success(
                file = cacheFile,
                uri = uri,
                totalExpenses = allExpenses.size,
                totalMonths = allMonths.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "exportAllDataCsv failed: ${e.message}", e)
            ExportResult.Error(e.message ?: "CSV export failed")
        }
    }

    /**
     * Import full data from an exported file (supports both JSON and CSV).
     * Enforces foreign key constraint integrity inside a database transaction.
     */
    suspend fun importDataFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Error("Could not open selected file.")

            val rawContent = inputStream.bufferedReader().use { it.readText() }.trim()
            if (rawContent.isBlank()) {
                return@withContext ImportResult.Error("Selected file is empty.")
            }

            val isJson = rawContent.startsWith("{") || rawContent.startsWith("[")
            if (isJson) {
                importFromJson(rawContent)
            } else {
                importFromCsv(rawContent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "importDataFromUri failed: ${e.message}", e)
            ImportResult.Error("Import failed: ${e.localizedMessage ?: e.message}")
        }
    }

    private suspend fun importFromCsv(csvString: String): ImportResult {
        val now = System.currentTimeMillis()
        val reader = CSVReader(StringReader(csvString))
        val rows = try {
            reader.readAll()
        } catch (e: Exception) {
            return ImportResult.Error("Failed to parse CSV file: ${e.message}")
        }

        if (rows.isEmpty()) {
            return ImportResult.Error("CSV file contains no data.")
        }

        val header = rows[0].map { it.trim().lowercase() }
        val idxDate = header.indexOfFirst { it.contains("date") }
        val idxTime = header.indexOfFirst { it.contains("time") }
        val idxMonth = header.indexOfFirst { it.contains("month") && !it.contains("salary") }
        val idxAmount = header.indexOfFirst { it.contains("amount") }
        val idxCategory = header.indexOfFirst { it.contains("category") }
        val idxSource = header.indexOfFirst { it.contains("source") || it.contains("payment") }
        val idxNote = header.indexOfFirst { it.contains("note") }
        val idxSalary = header.indexOfFirst { it.contains("salary") }

        if (idxAmount == -1) {
            return ImportResult.Error("Invalid CSV format: Missing 'Amount' column.")
        }

        return database.withTransaction {
            // 1. Ensure default fallback category exists
            val defaultOthers = Category(
                id = "cat_others",
                name = "Other",
                colorHex = "#9E9E9E",
                iconName = "more_horiz",
                isDefault = true,
                updatedAt = now
            )
            categoryDao.insert(defaultOthers)

            val existingCats = categoryDao.getAllIncludingDeleted().toMutableList()
            val catNameToId = existingCats.associate { it.name.trim().lowercase() to it.id }.toMutableMap()
            val catIdSet = existingCats.map { it.id }.toMutableSet()
            catIdSet.add("cat_others")

            var newCatsCount = 0
            val importedExpenses = mutableListOf<Expense>()
            val monthSalaries = mutableMapOf<String, Double>()

            for (i in 1 until rows.size) {
                val row = rows[i]
                if (row.isEmpty() || row.all { it.isBlank() }) continue

                val amountStr = if (idxAmount in row.indices) row[idxAmount].trim() else "0"
                val amount = amountStr.toDoubleOrNull() ?: continue

                // Category
                val rawCatName = if (idxCategory != -1 && idxCategory in row.indices) row[idxCategory].trim() else "Uncategorized"
                val catName = if (rawCatName.isNotBlank()) rawCatName else "Uncategorized"
                val catKey = catName.lowercase()

                val catId = if (catNameToId.containsKey(catKey)) {
                    catNameToId[catKey]!!
                } else {
                    val newId = "cat_" + UUID.randomUUID().toString().take(8)
                    val newCat = Category(
                        id = newId,
                        name = catName,
                        colorHex = "#6200EE",
                        iconName = "category",
                        isDefault = false,
                        isDeleted = false,
                        updatedAt = now
                    )
                    categoryDao.insert(newCat)
                    catNameToId[catKey] = newId
                    catIdSet.add(newId)
                    newCatsCount++
                    newId
                }

                // Date & Time
                val dateStr = if (idxDate != -1 && idxDate in row.indices) row[idxDate].trim() else ""
                val timeStr = if (idxTime != -1 && idxTime in row.indices) row[idxTime].trim() else ""

                var dateEpoch = now
                if (dateStr.isNotBlank()) {
                    try {
                        if (timeStr.isNotBlank()) {
                            dateEpoch = dateTimeFormat.parse("$dateStr $timeStr")?.time ?: now
                        } else {
                            dateEpoch = dateFormat.parse(dateStr)?.time ?: now
                        }
                    } catch (_: Exception) {
                        try {
                            dateEpoch = dateFormat.parse(dateStr)?.time ?: now
                        } catch (_: Exception) {}
                    }
                }

                val cal = Calendar.getInstance().apply { timeInMillis = dateEpoch }
                val yr = cal.get(Calendar.YEAR)
                val mo = cal.get(Calendar.MONTH) + 1

                val mKeyFromRow = if (idxMonth != -1 && idxMonth in row.indices) row[idxMonth].trim() else ""
                val resolvedMKey = if (mKeyFromRow.isNotBlank() && mKeyFromRow.contains("-")) mKeyFromRow else String.format("%04d-%02d", yr, mo)

                // Salary
                if (idxSalary != -1 && idxSalary in row.indices) {
                    val sal = row[idxSalary].trim().toDoubleOrNull()
                    if (sal != null && sal > 0.0) {
                        monthSalaries[resolvedMKey] = sal
                    }
                }

                val source = if (idxSource != -1 && idxSource in row.indices) {
                    val s = row[idxSource].trim().uppercase()
                    if (s.contains("CREDIT")) "CREDIT_CARD" else "INCOME"
                } else "INCOME"

                val note = if (idxNote != -1 && idxNote in row.indices) row[idxNote].trim() else ""

                importedExpenses.add(
                    Expense(
                        id = UUID.randomUUID().toString(),
                        amount = amount,
                        paymentSource = source,
                        categoryId = catId,
                        dateEpoch = dateEpoch,
                        year = yr,
                        month = mo,
                        monthKey = resolvedMKey,
                        note = note,
                        isDeleted = false,
                        updatedAt = now
                    )
                )
            }

            // Insert / Upsert Months
            val existingMonths = incomeMonthDao.getAllIncludingDeleted().associateBy { it.monthKey }
            val monthsToUpsert = mutableListOf<IncomeMonth>()
            val allMonthKeys = importedExpenses.map { it.monthKey }.toSet() + monthSalaries.keys

            for (mKey in allMonthKeys) {
                val yr = mKey.substringBefore("-").toIntOrNull() ?: 2026
                val mo = mKey.substringAfter("-").toIntOrNull() ?: 1
                val salary = monthSalaries[mKey] ?: (existingMonths[mKey]?.salary ?: 0.0)
                monthsToUpsert.add(
                    IncomeMonth(
                        id = existingMonths[mKey]?.id ?: UUID.randomUUID().toString(),
                        year = yr,
                        month = mo,
                        monthKey = mKey,
                        salary = salary,
                        creditCardDueCarried = existingMonths[mKey]?.creditCardDueCarried ?: 0.0,
                        isDeleted = false,
                        updatedAt = now
                    )
                )
            }

            if (monthsToUpsert.isNotEmpty()) {
                incomeMonthDao.upsertAll(monthsToUpsert)
            }

            if (importedExpenses.isNotEmpty()) {
                expenseDao.upsertAll(importedExpenses)
            }

            // Reconstruct Credit Card Ledgers
            val ccExpenses = expenseDao.getAllIncludingDeleted().filter { !it.isDeleted && it.paymentSource == "CREDIT_CARD" }
            val byMonth = ccExpenses.groupBy { it.year to it.month }
            val ledgers = byMonth.map { (ym, list) ->
                CreditCardLedger(
                    year = ym.first,
                    month = ym.second,
                    monthKey = String.format("%04d-%02d", ym.first, ym.second),
                    totalSpend = list.sumOf { it.amount },
                    updatedAt = now
                )
            }
            if (ledgers.isNotEmpty()) {
                creditCardLedgerDao.upsertAll(ledgers)
            }

            appPreferences.setOnboardingDone(true)
            val uid = appPreferences.userUid.first()
            val email = appPreferences.userEmail.first()
            val effectiveUid = if (uid.isNotBlank()) uid else if (email.isNotBlank()) "user_" + email.replace(".", "_").replace("@", "_") else ""
            if (effectiveUid.isNotBlank()) {
                try { cloudBackupVaultManager.persistBackup(email, effectiveUid) } catch (_: Exception) {}
            }
            database.checkpointWal()
            backupTriggerManager.markDataChanged()

            Log.i(TAG, "CSV Import completed: ${importedExpenses.size} expenses across ${monthsToUpsert.size} months")
            ImportResult.Success(
                expensesCount = importedExpenses.size,
                monthsCount = monthsToUpsert.size,
                categoriesCount = newCatsCount
            )
        }
    }

    private suspend fun importFromJson(jsonString: String): ImportResult {
        val root = JSONObject(jsonString)
        val now = System.currentTimeMillis()

        return database.withTransaction {
            // 1. Ensure default fallback category exists
            val defaultOthers = Category(
                id = "cat_others",
                name = "Other",
                colorHex = "#9E9E9E",
                iconName = "more_horiz",
                isDefault = true,
                updatedAt = now
            )
            categoryDao.insert(defaultOthers)

            // 2. Parse and Upsert Categories
            val catArray = root.optJSONArray("categories") ?: JSONArray()
            val importedCategories = mutableListOf<Category>()

            for (i in 0 until catArray.length()) {
                val obj = catArray.getJSONObject(i)
                val catId = obj.optString("id", UUID.randomUUID().toString())
                val catName = obj.optString("name", "Category $i")
                val colorHex = obj.optString("colorHex", "#6200EE")
                val iconName = obj.optString("iconName", obj.optString("icon", "category"))
                val isDefault = obj.optBoolean("isDefault", false)
                val sortOrder = obj.optInt("sortOrder", i)
                val aliases = obj.optString("aliases", "")
                val budget = if (obj.has("budget") && !obj.isNull("budget")) obj.getDouble("budget") else null
                val updatedAt = obj.optLong("updatedAt", now)

                val cat = Category(
                    id = catId,
                    name = catName,
                    colorHex = colorHex,
                    iconName = iconName,
                    isDefault = isDefault,
                    sortOrder = sortOrder,
                    aliases = aliases,
                    budget = budget,
                    isDeleted = false,
                    updatedAt = updatedAt
                )
                importedCategories.add(cat)
                categoryDao.upsert(cat)
            }

            // Map all existing category IDs and names
            val allCatsAfterImport = categoryDao.getAllIncludingDeleted()
            val catIdSet = allCatsAfterImport.map { it.id }.toMutableSet()
            val catNameToId = allCatsAfterImport.associate { it.name.trim().lowercase() to it.id }.toMutableMap()
            catIdSet.add("cat_others")

            // 3. Parse and Upsert Months (Salaries)
            val monthArray = root.optJSONArray("months") ?: JSONArray()
            val importedMonths = mutableListOf<IncomeMonth>()

            for (i in 0 until monthArray.length()) {
                val obj = monthArray.getJSONObject(i)
                val mKey = obj.optString("monthKey", "")
                val yr = obj.optInt("year", mKey.substringBefore("-").toIntOrNull() ?: 2026)
                val mo = obj.optInt("month", mKey.substringAfter("-").toIntOrNull() ?: 1)
                val resolvedKey = if (mKey.isNotBlank()) mKey else String.format("%04d-%02d", yr, mo)
                val salary = obj.optDouble("salary", 0.0)
                val ccDue = obj.optDouble("creditCardDueCarried", 0.0)
                val extraCredit = obj.optDouble("extraCredit", 0.0)
                val updatedAt = obj.optLong("updatedAt", now)

                val m = IncomeMonth(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    year = yr,
                    month = mo,
                    monthKey = resolvedKey,
                    salary = salary,
                    creditCardDueCarried = ccDue,
                    extraCredit = extraCredit,
                    isDeleted = false,
                    updatedAt = updatedAt
                )
                importedMonths.add(m)
                incomeMonthDao.upsert(m)
            }

            // 4. Parse and Upsert Expenses
            val expArray = root.optJSONArray("expenses") ?: JSONArray()
            val importedExpenses = mutableListOf<Expense>()

            for (i in 0 until expArray.length()) {
                val obj = expArray.getJSONObject(i)
                val expId = obj.optString("id", UUID.randomUUID().toString())
                val amount = obj.optDouble("amount", 0.0)
                val paymentSource = obj.optString("paymentSource", "INCOME")
                val dateEpoch = obj.optLong("dateEpoch", now)
                val yr = obj.optInt("year", 0)
                val mo = obj.optInt("month", 0)
                val mKey = obj.optString("monthKey", "")

                val cal = Calendar.getInstance().apply { timeInMillis = dateEpoch }
                val resolvedYr = if (yr != 0) yr else cal.get(Calendar.YEAR)
                val resolvedMo = if (mo != 0) mo else (cal.get(Calendar.MONTH) + 1)
                val resolvedMKey = if (mKey.isNotBlank()) mKey else String.format("%04d-%02d", resolvedYr, resolvedMo)

                // Match category ID safely:
                var catId = obj.optString("categoryId", "")
                val catName = obj.optString("categoryName", "")

                if (catId.isBlank() || catId !in catIdSet) {
                    if (catName.isNotBlank() && catNameToId.containsKey(catName.trim().lowercase())) {
                        catId = catNameToId[catName.trim().lowercase()] ?: ""
                    }
                }

                // If still not in catIdSet, create category immediately so foreign key constraint NEVER fails!
                if (catId.isBlank() || catId !in catIdSet) {
                    val validId = if (catId.isNotBlank()) catId else "cat_" + UUID.randomUUID().toString().take(8)
                    val validName = if (catName.isNotBlank()) catName else "Imported Category"
                    val newCat = Category(
                        id = validId,
                        name = validName,
                        colorHex = "#6200EE",
                        iconName = "category",
                        isDefault = false,
                        isDeleted = false,
                        updatedAt = now
                    )
                    categoryDao.insert(newCat)
                    catIdSet.add(validId)
                    catNameToId[validName.trim().lowercase()] = validId
                    catId = validId
                }

                val note = obj.optString("note", "")
                val isDeleted = obj.optBoolean("isDeleted", false)
                val updatedAt = obj.optLong("updatedAt", now)

                importedExpenses.add(
                    Expense(
                        id = expId,
                        amount = amount,
                        paymentSource = paymentSource,
                        categoryId = catId,
                        dateEpoch = dateEpoch,
                        year = resolvedYr,
                        month = resolvedMo,
                        monthKey = resolvedMKey,
                        note = note,
                        isDeleted = isDeleted,
                        updatedAt = updatedAt,
                        isSplit = obj.optBoolean("isSplit", false),
                        isReimbursed = obj.optBoolean("isReimbursed", false),
                        receivableAmount = obj.optDouble("receivableAmount", 0.0),
                        netUserExpense = obj.optDouble("netUserExpense", 0.0),
                        extraCreditAmount = obj.optDouble("extraCreditAmount", 0.0)
                    )
                )
            }

            if (importedExpenses.isNotEmpty()) {
                expenseDao.upsertAll(importedExpenses)
            }

            // 5. Parse and Upsert Budgets
            val budgetArray = root.optJSONArray("budgets") ?: JSONArray()
            val importedBudgets = mutableListOf<Budget>()

            for (i in 0 until budgetArray.length()) {
                val obj = budgetArray.getJSONObject(i)
                val bId = obj.optString("id", UUID.randomUUID().toString())
                var catId = obj.optString("categoryId", "")
                val catName = obj.optString("categoryName", "")
                if (catId.isBlank() || catId !in catIdSet) {
                    if (catName.isNotBlank() && catNameToId.containsKey(catName.trim().lowercase())) {
                        catId = catNameToId[catName.trim().lowercase()] ?: ""
                    }
                }
                if (catId in catIdSet) {
                    val yr = obj.optInt("year", 2026)
                    val mo = obj.optInt("month", 1)
                    val mKey = obj.optString("monthKey", String.format("%04d-%02d", yr, mo))
                    val limit = obj.optDouble("budgetAmount", obj.optDouble("monthlyLimit", 0.0))

                    val b = Budget(
                        id = bId,
                        categoryId = catId,
                        year = yr,
                        month = mo,
                        monthKey = mKey,
                        budgetAmount = limit,
                        isDeleted = false,
                        updatedAt = obj.optLong("updatedAt", now)
                    )
                    importedBudgets.add(b)
                    budgetDao.upsert(b)
                }
            }

            // 6. Reconstruct Credit Card Ledgers
            val ccExpenses = expenseDao.getAllIncludingDeleted().filter { !it.isDeleted && it.paymentSource == "CREDIT_CARD" }
            val byMonth = ccExpenses.groupBy { it.year to it.month }
            val ledgers = byMonth.map { (ym, list) ->
                CreditCardLedger(
                    year = ym.first,
                    month = ym.second,
                    monthKey = String.format("%04d-%02d", ym.first, ym.second),
                    totalSpend = list.sumOf { it.amount },
                    updatedAt = now
                )
            }
            if (ledgers.isNotEmpty()) {
                creditCardLedgerDao.upsertAll(ledgers)
            }

            // 7. Update Preferences
            appPreferences.setOnboardingDone(true)
            val currencySymbol = root.optString("currencySymbol", "")
            if (currencySymbol.isNotBlank()) {
                appPreferences.setCurrencySymbol(currencySymbol)
            }

            val uid = appPreferences.userUid.first()
            val email = appPreferences.userEmail.first()
            val effectiveUid = if (uid.isNotBlank()) uid else if (email.isNotBlank()) "user_" + email.replace(".", "_").replace("@", "_") else ""
            if (effectiveUid.isNotBlank()) {
                try { cloudBackupVaultManager.persistBackup(email, effectiveUid) } catch (_: Exception) {}
            }
            database.checkpointWal()
            backupTriggerManager.markDataChanged()

            Log.i(TAG, "JSON Import completed: ${importedExpenses.size} expenses, ${importedMonths.size} months, ${importedCategories.size} categories")
            ImportResult.Success(
                expensesCount = importedExpenses.count { !it.isDeleted },
                monthsCount = importedMonths.count { !it.isDeleted },
                categoriesCount = importedCategories.count { !it.isDeleted }
            )
        }
    }
}
