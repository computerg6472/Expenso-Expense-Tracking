package com.spendwise.app.data.backup.snapshot

import com.spendwise.app.data.local.entity.Budget
import com.spendwise.app.data.local.entity.Category
import com.spendwise.app.data.local.entity.CreditCardLedger
import com.spendwise.app.data.local.entity.Expense
import com.spendwise.app.data.local.entity.IncomeMonth
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnapshotSerializer @Inject constructor() {

    /**
     * Serializes BackupSnapshot to JSON string.
     */
    fun toJson(snapshot: BackupSnapshot): String {
        val root = JSONObject()

        // 1. Header
        val h = snapshot.header
        val headerJson = JSONObject().apply {
            put("schemaVersion", h.schemaVersion)
            put("appVersion", h.appVersion)
            put("createdAt", h.createdAt)
            put("deviceId", h.deviceId)
            put("accountEmail", h.accountEmail)
            put("expensesCount", h.expensesCount)
            put("monthsCount", h.monthsCount)
            put("categoriesCount", h.categoriesCount)
            put("generation", h.generation)
            put("contentHash", h.contentHash)
            put("encryptionMode", h.encryptionMode)
        }
        root.put("header", headerJson)

        // 2. Preferences
        val p = snapshot.preferences
        val prefJson = JSONObject().apply {
            put("currencySymbol", p.currencySymbol)
            put("monthlySalary", p.monthlySalary)
            put("onboardingDone", p.onboardingDone)
            put("themeMode", p.themeMode)
            put("biometricEnabled", p.biometricEnabled)
            put("creditCardCarriedForwardEnabled", p.creditCardCarriedForwardEnabled)
        }
        root.put("preferences", prefJson)

        // 3. Categories
        val categoriesArray = JSONArray()
        for (cat in snapshot.categories) {
            val cj = JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("colorHex", cat.colorHex)
                put("iconName", cat.iconName)
                put("isDefault", cat.isDefault)
                put("deviceId", cat.deviceId)
                put("isDeleted", cat.isDeleted)
                put("sortOrder", cat.sortOrder)
                put("aliases", cat.aliases)
                if (cat.budget != null) put("budget", cat.budget)
                put("createdAt", cat.createdAt)
                put("updatedAt", cat.updatedAt)
            }
            categoriesArray.put(cj)
        }
        root.put("categories", categoriesArray)

        // 4. Budgets
        val budgetsArray = JSONArray()
        for (b in snapshot.budgets) {
            val bj = JSONObject().apply {
                put("id", b.id)
                put("categoryId", b.categoryId)
                put("year", b.year)
                put("month", b.month)
                put("monthKey", b.monthKey)
                put("budgetAmount", b.budgetAmount)
                put("deviceId", b.deviceId)
                put("isDeleted", b.isDeleted)
                put("createdAt", b.createdAt)
                put("updatedAt", b.updatedAt)
            }
            budgetsArray.put(bj)
        }
        root.put("budgets", budgetsArray)

        // 5. Income Months (Salaries)
        val incomeArray = JSONArray()
        for (im in snapshot.incomeMonths) {
            val ij = JSONObject().apply {
                put("id", im.id)
                put("year", im.year)
                put("month", im.month)
                put("monthKey", im.monthKey)
                put("salary", im.salary)
                put("creditCardDueCarried", im.creditCardDueCarried)
                put("extraCredit", im.extraCredit)
                put("deviceId", im.deviceId)
                put("isDeleted", im.isDeleted)
                put("createdAt", im.createdAt)
                put("updatedAt", im.updatedAt)
            }
            incomeArray.put(ij)
        }
        root.put("incomeMonths", incomeArray)

        // 6. Expenses
        val expensesArray = JSONArray()
        for (e in snapshot.expenses) {
            val ej = JSONObject().apply {
                put("id", e.id)
                put("amount", e.amount)
                put("paymentSource", e.paymentSource)
                put("categoryId", e.categoryId)
                put("dateEpoch", e.dateEpoch)
                put("year", e.year)
                put("month", e.month)
                put("monthKey", e.monthKey)
                put("note", e.note)
                put("deviceId", e.deviceId)
                put("isDeleted", e.isDeleted)
                put("createdAt", e.createdAt)
                put("updatedAt", e.updatedAt)
                put("isSplit", e.isSplit)
                put("isReimbursed", e.isReimbursed)
                put("receivableAmount", e.receivableAmount)
                put("netUserExpense", e.netUserExpense)
                put("extraCreditAmount", e.extraCreditAmount)
            }
            expensesArray.put(ej)
        }
        root.put("expenses", expensesArray)

        // 7. Credit Card Ledgers
        val ccArray = JSONArray()
        for (cc in snapshot.creditCardLedgers) {
            val ccj = JSONObject().apply {
                put("id", cc.id)
                put("year", cc.year)
                put("month", cc.month)
                put("monthKey", cc.monthKey)
                put("totalSpend", cc.totalSpend)
                put("carriedToNextMonth", cc.carriedToNextMonth)
                put("deviceId", cc.deviceId)
                put("isDeleted", cc.isDeleted)
                put("createdAt", cc.createdAt)
                put("updatedAt", cc.updatedAt)
            }
            ccArray.put(ccj)
        }
        root.put("creditCardLedgers", ccArray)

        // 8. AI Chat History
        val chatArray = JSONArray()
        for (cm in snapshot.chatMessages) {
            val cmj = JSONObject().apply {
                put("id", cm.id)
                put("isUser", cm.isUser)
                put("text", cm.text)
                put("timestamp", cm.timestamp)
            }
            chatArray.put(cmj)
        }
        root.put("chatMessages", chatArray)

        return root.toString()
    }

    /**
     * Parses BackupSnapshot from JSON string.
     */
    fun fromJson(jsonString: String): BackupSnapshot {
        val root = JSONObject(jsonString)

        // 1. Header
        val hj = root.optJSONObject("header") ?: JSONObject()
        val header = SnapshotHeader(
            schemaVersion = hj.optInt("schemaVersion", SnapshotHeader.CURRENT_SCHEMA_VERSION),
            appVersion = hj.optString("appVersion", "5.0"),
            createdAt = hj.optLong("createdAt", System.currentTimeMillis()),
            deviceId = hj.optString("deviceId", ""),
            accountEmail = hj.optString("accountEmail", ""),
            expensesCount = hj.optInt("expensesCount", 0),
            monthsCount = hj.optInt("monthsCount", 0),
            categoriesCount = hj.optInt("categoriesCount", 0),
            generation = hj.optLong("generation", 1L),
            contentHash = hj.optString("contentHash", ""),
            encryptionMode = hj.optInt("encryptionMode", SnapshotHeader.ENCRYPTION_MODE_ACCOUNT_BOUND)
        )

        // 2. Preferences
        val pj = root.optJSONObject("preferences") ?: JSONObject()
        val preferences = PreferencesSnapshot(
            currencySymbol = pj.optString("currencySymbol", "₹"),
            monthlySalary = pj.optDouble("monthlySalary", 0.0),
            onboardingDone = pj.optBoolean("onboardingDone", true),
            themeMode = pj.optString("themeMode", "SYSTEM"),
            biometricEnabled = pj.optBoolean("biometricEnabled", false),
            creditCardCarriedForwardEnabled = pj.optBoolean("creditCardCarriedForwardEnabled", false)
        )

        // 3. Categories
        val categoriesList = mutableListOf<Category>()
        val catArray = root.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until catArray.length()) {
            val c = catArray.getJSONObject(i)
            categoriesList.add(
                Category(
                    id = c.getString("id"),
                    name = c.getString("name"),
                    colorHex = c.optString("colorHex", "#6200EE"),
                    iconName = c.optString("iconName", "category"),
                    isDefault = c.optBoolean("isDefault", false),
                    deviceId = c.optString("deviceId", ""),
                    isDeleted = c.optBoolean("isDeleted", false),
                    sortOrder = c.optInt("sortOrder", 0),
                    aliases = c.optString("aliases", ""),
                    budget = if (c.has("budget") && !c.isNull("budget")) c.optDouble("budget") else null,
                    createdAt = c.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = c.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        // 4. Budgets
        val budgetsList = mutableListOf<Budget>()
        val bArray = root.optJSONArray("budgets") ?: JSONArray()
        for (i in 0 until bArray.length()) {
            val b = bArray.getJSONObject(i)
            budgetsList.add(
                Budget(
                    id = b.getString("id"),
                    categoryId = b.getString("categoryId"),
                    year = b.getInt("year"),
                    month = b.getInt("month"),
                    monthKey = b.optString("monthKey", String.format("%04d-%02d", b.getInt("year"), b.getInt("month"))),
                    budgetAmount = b.getDouble("budgetAmount"),
                    deviceId = b.optString("deviceId", ""),
                    isDeleted = b.optBoolean("isDeleted", false),
                    createdAt = b.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = b.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        // 5. Income Months
        val incomeList = mutableListOf<IncomeMonth>()
        val inArray = root.optJSONArray("incomeMonths") ?: JSONArray()
        for (i in 0 until inArray.length()) {
            val im = inArray.getJSONObject(i)
            incomeList.add(
                IncomeMonth(
                    id = im.getString("id"),
                    year = im.getInt("year"),
                    month = im.getInt("month"),
                    monthKey = im.optString("monthKey", String.format("%04d-%02d", im.getInt("year"), im.getInt("month"))),
                    salary = im.getDouble("salary"),
                    creditCardDueCarried = im.optDouble("creditCardDueCarried", 0.0),
                    extraCredit = im.optDouble("extraCredit", 0.0),
                    deviceId = im.optString("deviceId", ""),
                    isDeleted = im.optBoolean("isDeleted", false),
                    createdAt = im.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = im.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        // 6. Expenses
        val expensesList = mutableListOf<Expense>()
        val exArray = root.optJSONArray("expenses") ?: JSONArray()
        for (i in 0 until exArray.length()) {
            val e = exArray.getJSONObject(i)
            expensesList.add(
                Expense(
                    id = e.getString("id"),
                    amount = e.getDouble("amount"),
                    paymentSource = e.optString("paymentSource", "INCOME"),
                    categoryId = e.getString("categoryId"),
                    dateEpoch = e.getLong("dateEpoch"),
                    year = e.getInt("year"),
                    month = e.getInt("month"),
                    monthKey = e.optString("monthKey", String.format("%04d-%02d", e.getInt("year"), e.getInt("month"))),
                    note = e.optString("note", ""),
                    deviceId = e.optString("deviceId", ""),
                    isDeleted = e.optBoolean("isDeleted", false),
                    createdAt = e.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = e.optLong("updatedAt", System.currentTimeMillis()),
                    isSplit = e.optBoolean("isSplit", false),
                    isReimbursed = e.optBoolean("isReimbursed", false),
                    receivableAmount = e.optDouble("receivableAmount", 0.0),
                    netUserExpense = e.optDouble("netUserExpense", 0.0),
                    extraCreditAmount = e.optDouble("extraCreditAmount", 0.0)
                )
            )
        }

        // 7. Credit Card Ledgers
        val ccList = mutableListOf<CreditCardLedger>()
        val ccArray = root.optJSONArray("creditCardLedgers") ?: JSONArray()
        for (i in 0 until ccArray.length()) {
            val cc = ccArray.getJSONObject(i)
            ccList.add(
                CreditCardLedger(
                    id = cc.getString("id"),
                    year = cc.getInt("year"),
                    month = cc.getInt("month"),
                    monthKey = cc.optString("monthKey", String.format("%04d-%02d", cc.getInt("year"), cc.getInt("month"))),
                    totalSpend = cc.optDouble("totalSpend", 0.0),
                    carriedToNextMonth = cc.optBoolean("carriedToNextMonth", false),
                    deviceId = cc.optString("deviceId", ""),
                    isDeleted = cc.optBoolean("isDeleted", false),
                    createdAt = cc.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = cc.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        // 8. AI Chat Messages
        val chatList = mutableListOf<ChatMessageSnapshot>()
        val chatArray = root.optJSONArray("chatMessages") ?: JSONArray()
        for (i in 0 until chatArray.length()) {
            val cm = chatArray.getJSONObject(i)
            chatList.add(
                ChatMessageSnapshot(
                    id = cm.getString("id"),
                    isUser = cm.getBoolean("isUser"),
                    text = cm.getString("text"),
                    timestamp = cm.getLong("timestamp")
                )
            )
        }

        return BackupSnapshot(
            header = header,
            categories = categoriesList,
            budgets = budgetsList,
            incomeMonths = incomeList,
            expenses = expensesList,
            creditCardLedgers = ccList,
            chatMessages = chatList,
            preferences = preferences
        )
    }

    /**
     * Compresses raw string into GZIP bytes.
     */
    fun compressGzip(data: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(data.toByteArray(StandardCharsets.UTF_8))
        }
        return bos.toByteArray()
    }

    /**
     * Decompresses GZIP bytes back into UTF-8 string.
     */
    fun decompressGzip(compressed: ByteArray): String {
        ByteArrayInputStream(compressed).use { bis ->
            GZIPInputStream(bis).use { gzip ->
                val buffer = ByteArray(4096)
                val out = ByteArrayOutputStream()
                var read: Int
                while (gzip.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                return out.toString(StandardCharsets.UTF_8.name())
            }
        }
    }

    /**
     * Computes SHA-256 hex string of input bytes.
     */
    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        val hexString = StringBuilder()
        for (b in hash) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }
}
