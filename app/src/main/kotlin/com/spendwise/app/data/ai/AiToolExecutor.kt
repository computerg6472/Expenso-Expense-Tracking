package com.spendwise.app.data.ai

import com.spendwise.app.data.local.entity.Category
import com.spendwise.app.data.local.entity.Expense
import com.spendwise.app.domain.repository.CategoryRepository
import com.spendwise.app.domain.repository.CreditCardRepository
import com.spendwise.app.domain.repository.ExpenseRepository
import com.spendwise.app.domain.repository.IncomeRepository
import com.spendwise.intelligence.domain.feature.NlpExpenseParser
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ToolExecutionResult(
    val toolName: String,
    val resultJson: JSONObject,
    val pendingDrafts: List<PendingExpenseDraft>? = null
)

data class LocalEvaluationResult(
    val answerText: String,
    val pendingDrafts: List<PendingExpenseDraft>? = null
)

@Singleton
class AiToolExecutor @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val incomeRepository: IncomeRepository,
    private val creditCardRepository: CreditCardRepository,
    private val nlpExpenseParser: NlpExpenseParser
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    suspend fun executeTool(name: String, args: JSONObject): ToolExecutionResult {
        return when (name) {
            "get_expenses" -> ToolExecutionResult(name, executeGetExpenses(args))
            "get_category_totals" -> ToolExecutionResult(name, executeGetCategoryTotals(args))
            "get_balance_summary" -> ToolExecutionResult(name, executeGetBalanceSummary())
            "get_trends" -> ToolExecutionResult(name, executeGetTrends(args))
            "add_expense" -> {
                val drafts = executeAddExpenseDraft(args)
                val json = JSONObject().apply {
                    put("status", "draft_created")
                    put("item_count", drafts.size)
                    val itemsArr = JSONArray()
                    drafts.forEach { d ->
                        itemsArr.put(JSONObject().apply {
                            put("amount", d.amount)
                            put("category", d.categoryName)
                            put("note", d.note)
                            put("date", d.dateFormatted)
                        })
                    }
                    put("items", itemsArr)
                }
                ToolExecutionResult(name, json, pendingDrafts = drafts)
            }
            "update_expense" -> {
                val drafts = executeUpdateExpenseDraft(args)
                val json = JSONObject().apply {
                    put("status", if (drafts.isNotEmpty()) "update_draft_created" else "not_found")
                    put("item_count", drafts.size)
                }
                ToolExecutionResult(name, json, pendingDrafts = drafts)
            }
            "delete_expense" -> {
                val drafts = executeDeleteExpenseDraft(args)
                val json = JSONObject().apply {
                    put("status", if (drafts.isNotEmpty()) "delete_draft_created" else "not_found")
                    put("item_count", drafts.size)
                }
                ToolExecutionResult(name, json, pendingDrafts = drafts)
            }
            else -> ToolExecutionResult(name, JSONObject().put("error", "Unknown tool $name"))
        }
    }

    private suspend fun executeGetExpenses(args: JSONObject): JSONObject {
        val categoryName = args.optString("category", "").trim()
        val dateFromStr = args.optString("date_from", "")
        val dateToStr = args.optString("date_to", "")
        val minAmt = if (args.has("min_amount")) args.optDouble("min_amount") else null
        val maxAmt = if (args.has("max_amount")) args.optDouble("max_amount") else null
        val noteContains = args.optString("note_contains", "").trim().lowercase()

        val allExpenses = expenseRepository.observeAll().first()
        val allCategories = categoryRepository.getAll().associateBy { it.id }

        val fromEpoch = if (dateFromStr.isNotBlank()) try { dateFormat.parse(dateFromStr)?.time ?: 0L } catch (e: Exception) { 0L } else 0L
        val toEpoch = if (dateToStr.isNotBlank()) try { (dateFormat.parse(dateToStr)?.time ?: Long.MAX_VALUE) + 86400000L } catch (e: Exception) { Long.MAX_VALUE } else Long.MAX_VALUE

        val filtered = allExpenses.filter { exp ->
            val catName = allCategories[exp.categoryId]?.name.orEmpty()
            val matchesCategory = categoryName.isBlank() || catName.contains(categoryName, ignoreCase = true)
            val matchesDate = exp.dateEpoch in fromEpoch..toEpoch
            val matchesMin = minAmt == null || exp.amount >= minAmt
            val matchesMax = maxAmt == null || exp.amount <= maxAmt
            val matchesNote = noteContains.isBlank() || exp.note.lowercase().contains(noteContains)
            matchesCategory && matchesDate && matchesMin && matchesMax && matchesNote
        }

        val resArray = JSONArray()
        filtered.take(30).forEach { exp ->
            resArray.put(JSONObject().apply {
                put("id", exp.id)
                put("amount", exp.amount)
                put("category", allCategories[exp.categoryId]?.name ?: "Other")
                put("note", exp.note)
                put("date", dateFormat.format(Date(exp.dateEpoch)))
                put("payment_method", exp.paymentSource)
            })
        }

        return JSONObject().apply {
            put("count", filtered.size)
            put("total_amount", filtered.sumOf { it.amount })
            put("expenses", resArray)
        }
    }

    private suspend fun executeGetCategoryTotals(args: JSONObject): JSONObject {
        val dateRange = args.optString("date_range", "this_month")
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1

        val (targetYear, targetMonth) = when (dateRange) {
            "last_month" -> {
                cal.add(Calendar.MONTH, -1)
                Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            }
            else -> Pair(currentYear, currentMonth)
        }

        val allExpenses = expenseRepository.observeAll().first()
        val allCategories = categoryRepository.getAll().associateBy { it.id }

        val filtered = when (dateRange) {
            "all_time" -> allExpenses
            else -> allExpenses.filter { it.year == targetYear && it.month == targetMonth }
        }

        val grouped = filtered.groupBy { it.categoryId }
        val grandTotal = filtered.sumOf { it.amount }

        val catArray = JSONArray()
        grouped.entries.sortedByDescending { it.value.sumOf { exp -> exp.amount } }.forEach { (catId, exps) ->
            val catTotal = exps.sumOf { it.amount }
            val catName = allCategories[catId]?.name ?: "Other"
            val pct = if (grandTotal > 0) (catTotal / grandTotal) * 100.0 else 0.0
            catArray.put(JSONObject().apply {
                put("category", catName)
                put("total", catTotal)
                put("count", exps.size)
                put("percentage", Math.round(pct * 10.0) / 10.0)
            })
        }

        return JSONObject().apply {
            put("period", dateRange)
            put("grand_total", grandTotal)
            put("categories", catArray)
        }
    }

    private suspend fun executeGetBalanceSummary(): JSONObject {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1

        val incomeMonth = incomeRepository.getOrCreateCurrentMonth()
        val salary = incomeMonth.salary
        val allExpenses = expenseRepository.observeAll().first()
        val thisMonthExpenses = allExpenses.filter { it.year == year && it.month == month }

        val spentFromIncome = thisMonthExpenses.filter { it.paymentSource == "INCOME" }.sumOf { it.amount }
        val spentFromCredit = thisMonthExpenses.filter { it.paymentSource == "CREDIT_CARD" }.sumOf { it.amount }
        val availableBalance = (salary - spentFromIncome).coerceAtLeast(0.0)

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val remainingDays = (daysInMonth - currentDay + 1).coerceAtLeast(1)
        val dailyCeiling = availableBalance / remainingDays

        return JSONObject().apply {
            put("salary", salary)
            put("spent_from_income", spentFromIncome)
            put("spent_from_credit", spentFromCredit)
            put("total_spent_this_month", spentFromIncome + spentFromCredit)
            put("available_balance", availableBalance)
            put("daily_safe_ceiling", Math.round(dailyCeiling * 100.0) / 100.0)
            put("days_remaining", remainingDays)
        }
    }

    private suspend fun executeGetTrends(args: JSONObject): JSONObject {
        val period = args.optString("period", "compare_last_month")
        val cal = Calendar.getInstance()
        val thisYear = cal.get(Calendar.YEAR)
        val thisMonth = cal.get(Calendar.MONTH) + 1

        cal.add(Calendar.MONTH, -1)
        val lastYear = cal.get(Calendar.YEAR)
        val lastMonth = cal.get(Calendar.MONTH) + 1

        val allExpenses = expenseRepository.observeAll().first()
        val thisMonthExpenses = allExpenses.filter { it.year == thisYear && it.month == thisMonth }
        val lastMonthExpenses = allExpenses.filter { it.year == lastYear && it.month == lastMonth }

        val thisTotal = thisMonthExpenses.sumOf { it.amount }
        val lastTotal = lastMonthExpenses.sumOf { it.amount }
        val diff = thisTotal - lastTotal
        val pctChange = if (lastTotal > 0) ((diff / lastTotal) * 100.0) else 0.0

        val dayGroup = thisMonthExpenses.groupBy {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateEpoch }
            c.get(Calendar.DAY_OF_MONTH)
        }
        val highestDayEntry = dayGroup.maxByOrNull { it.value.sumOf { exp -> exp.amount } }
        val highestDay = highestDayEntry?.key ?: cal.get(Calendar.DAY_OF_MONTH)
        val highestDayTotal = highestDayEntry?.value?.sumOf { it.amount } ?: 0.0

        val allCategories = categoryRepository.getAll().associateBy { it.id }
        val topCats = thisMonthExpenses.groupBy { it.categoryId }
            .map { (id, exps) ->
                val name = allCategories[id]?.name ?: "Other"
                val sum = exps.sumOf { it.amount }
                val pct = if (thisTotal > 0) (sum / thisTotal) * 100.0 else 0.0
                name to Pair(sum, pct)
            }
            .sortedByDescending { it.second.first }
            .take(3)

        val topCatsArray = JSONArray()
        topCats.forEach { (name, pair) ->
            topCatsArray.put(JSONObject().apply {
                put("category", name)
                put("amount", pair.first)
                put("percentage", Math.round(pair.second * 10.0) / 10.0)
                put("suggested_cut", Math.round(pair.first * 0.15))
            })
        }

        return JSONObject().apply {
            put("period", period)
            put("this_month_total", thisTotal)
            put("last_month_total", lastTotal)
            put("difference", diff)
            put("percent_change", Math.round(pctChange * 10.0) / 10.0)
            put("highest_day_of_month", highestDay)
            put("highest_day_total", highestDayTotal)
            put("top_categories", topCatsArray)
        }
    }

    private suspend fun executeAddExpenseDraft(args: JSONObject): List<PendingExpenseDraft> {
        val itemsArray = args.optJSONArray("items") ?: JSONArray()
        val allCategories = categoryRepository.getAll()
        val pastExpenses = expenseRepository.observeAll().first().take(200)
        val drafts = mutableListOf<PendingExpenseDraft>()

        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.optJSONObject(i) ?: continue
            val amount = itemObj.optDouble("amount", 0.0)
            if (amount <= 0.0) continue

            val requestedCategory = itemObj.optString("category", "").trim()
            val rawNote = itemObj.optString("note", "").trim()
            val dateStr = itemObj.optString("date", "")
            val paymentMethod = itemObj.optString("payment_method", "INCOME")

            val matchResult = AiExpenseParser.matchCategory(
                query = if (requestedCategory.isNotBlank()) "$requestedCategory $rawNote" else rawNote,
                allCategories = allCategories,
                pastUserExpenses = pastExpenses
            )
            val matchedCategory = matchResult.category
            val cleanNote = AiExpenseParser.cleanExpenseNote(rawNote.ifBlank { matchedCategory.name }, matchedCategory.name)

            val (dateEpoch, dateLabel) = resolveDateEpoch(dateStr)

            drafts.add(
                PendingExpenseDraft(
                    draftType = ExpenseDraftType.CREATE,
                    amount = amount,
                    categoryName = matchedCategory.name,
                    categoryId = matchedCategory.id,
                    note = cleanNote,
                    dateEpoch = dateEpoch,
                    dateFormatted = dateLabel,
                    paymentSource = if (paymentMethod.contains("CREDIT", ignoreCase = true)) "CREDIT_CARD" else "INCOME",
                    matchReason = matchResult.matchReason,
                    isOthersFallback = matchResult.isOthersFallback,
                    suggestedCategoryName = matchResult.suggestedCategoryName,
                    candidateCategories = matchResult.candidateCategories,
                    clarificationNeeded = matchResult.clarificationNeeded
                )
            )
        }
        return drafts
    }

    private suspend fun executeUpdateExpenseDraft(args: JSONObject): List<PendingExpenseDraft> {
        val ref = args.optString("expense_reference", "").trim()
        val allExpenses = expenseRepository.observeAll().first()
        val allCategories = categoryRepository.getAll()
        val catMap = allCategories.associateBy { it.id }

        val target = AiExpenseParser.resolveExpenseReference(ref, allExpenses, allCategories)
            ?: return emptyList()

        val originalCatName = catMap[target.categoryId]?.name ?: "Others"
        val newAmount = if (args.has("new_amount") && args.optDouble("new_amount") > 0.0) args.optDouble("new_amount") else target.amount
        val requestedCat = args.optString("new_category", "").trim()
        val newNoteRaw = args.optString("new_note", "").trim()
        val newDateStr = args.optString("new_date", "").trim()
        val newPaymentMethod = args.optString("new_payment_method", "").trim()

        val matchedCategory = if (requestedCat.isNotBlank()) {
            val res = AiExpenseParser.matchCategory(requestedCat, allCategories)
            res.category
        } else {
            catMap[target.categoryId] ?: allCategories.first()
        }

        val cleanNote = if (newNoteRaw.isNotBlank()) {
            AiExpenseParser.cleanExpenseNote(newNoteRaw, matchedCategory.name)
        } else {
            target.note
        }

        val (newEpoch, newDateFormatted) = if (newDateStr.isNotBlank()) {
            resolveDateEpoch(newDateStr)
        } else {
            Pair(target.dateEpoch, dateFormat.format(Date(target.dateEpoch)))
        }

        val newPaymentSource = if (newPaymentMethod.isNotBlank()) {
            if (newPaymentMethod.contains("CREDIT", ignoreCase = true)) "CREDIT_CARD" else "INCOME"
        } else {
            target.paymentSource
        }

        val draft = PendingExpenseDraft(
            draftType = ExpenseDraftType.UPDATE,
            targetExpenseId = target.id,
            originalAmount = target.amount,
            originalCategoryName = originalCatName,
            originalNote = target.note,
            amount = newAmount,
            categoryId = matchedCategory.id,
            categoryName = matchedCategory.name,
            note = cleanNote,
            dateEpoch = newEpoch,
            dateFormatted = newDateFormatted,
            paymentSource = newPaymentSource,
            matchReason = "Updating existing expense"
        )
        return listOf(draft)
    }

    private suspend fun executeDeleteExpenseDraft(args: JSONObject): List<PendingExpenseDraft> {
        val ref = args.optString("expense_reference", "").trim()
        val allExpenses = expenseRepository.observeAll().first()
        val allCategories = categoryRepository.getAll()
        val catMap = allCategories.associateBy { it.id }

        val target = AiExpenseParser.resolveExpenseReference(ref, allExpenses, allCategories)
            ?: return emptyList()

        val catName = catMap[target.categoryId]?.name ?: "Others"

        val draft = PendingExpenseDraft(
            draftType = ExpenseDraftType.DELETE,
            targetExpenseId = target.id,
            originalAmount = target.amount,
            originalCategoryName = catName,
            originalNote = target.note,
            amount = target.amount,
            categoryId = target.categoryId,
            categoryName = catName,
            note = target.note,
            dateEpoch = target.dateEpoch,
            dateFormatted = dateFormat.format(Date(target.dateEpoch)),
            paymentSource = target.paymentSource,
            matchReason = "Confirm delete"
        )
        return listOf(draft)
    }

    private fun resolveDateEpoch(dateStr: String): Pair<Long, String> {
        val cal = Calendar.getInstance()
        val todayStr = dateFormat.format(cal.time)

        if (dateStr.isBlank() || dateStr.equals("today", ignoreCase = true)) {
            return Pair(cal.timeInMillis, "Today")
        }

        if (dateStr.equals("yesterday", ignoreCase = true)) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            return Pair(cal.timeInMillis, "Yesterday")
        }

        try {
            val parsed = dateFormat.parse(dateStr)
            if (parsed != null) {
                val formatted = if (dateStr == todayStr) "Today" else SimpleDateFormat("dd MMM", Locale.getDefault()).format(parsed)
                return Pair(parsed.time, formatted)
            }
        } catch (e: Exception) {
            // Ignore parse exception
        }

        return Pair(System.currentTimeMillis(), "Today")
    }

    /**
     * Local deterministic fallback evaluator for 100% reliable responses
     * when offline or when user asks any standard query or adding an expense.
     */
    suspend fun fallbackEvaluateLocally(query: String, currencySymbol: String = "₹"): LocalEvaluationResult {
        val lower = query.trim().lowercase()
        val allCategories = categoryRepository.getAll()
        val pastExpenses = expenseRepository.observeAll().first().take(200)

        // 1. Delete command detection: "delete the last expense", "remove starbucks"
        if (lower.startsWith("delete") || lower.startsWith("remove")) {
            val drafts = executeDeleteExpenseDraft(JSONObject().put("expense_reference", query))
            if (drafts.isNotEmpty()) {
                val d = drafts[0]
                return LocalEvaluationResult(
                    answerText = "Please confirm deleting this expense:",
                    pendingDrafts = drafts
                )
            }
        }

        // 2. Update command detection: "update last expense to 400", "change yesterday's dinner"
        if (lower.startsWith("update") || lower.startsWith("change") || lower.startsWith("edit")) {
            val parsedAmt = AiExpenseParser.parseAmount(query)
            val updateArgs = JSONObject().apply {
                put("expense_reference", query)
                if (parsedAmt != null) put("new_amount", parsedAmt.amount)
            }
            val drafts = executeUpdateExpenseDraft(updateArgs)
            if (drafts.isNotEmpty()) {
                return LocalEvaluationResult(
                    answerText = "I've prepared this expense update. Please review before saving:",
                    pendingDrafts = drafts
                )
            }
        }

        // 3. Multi-line, comma-separated bulk or single expense detection
        val isBulkOrAdd = query.contains(",") || query.contains(";") || query.contains("\n") ||
                lower.startsWith("add") || lower.startsWith("spent") || lower.startsWith("paid") ||
                lower.startsWith("bought") || lower.startsWith("swiggy") || lower.startsWith("zomato") ||
                lower.startsWith("uber") || lower.startsWith("chai") || lower.startsWith("metro") ||
                lower.contains("rupees") || lower.contains("rs") || lower.contains("₹")

        val isAnalyticsQuestion = lower.contains("balance") || lower.contains("top spend") ||
                lower.contains("reduce") || lower.contains("compare") || lower.contains("which day")

        if (isBulkOrAdd && !isAnalyticsQuestion) {
            // Split by comma, semicolon, newline, or " and "
            val rawSegments = if (query.contains(",") || query.contains(";") || query.contains("\n")) {
                query.split(Regex("[,;\\n]+")).map { it.trim() }.filter { it.isNotBlank() }
            } else if (lower.contains(" and ") && (lower.contains("spent") || lower.contains("paid") || query.count { it.isDigit() } >= 2)) {
                query.split(Regex("(?i)\\s+and\\s+")).map { it.trim() }.filter { it.isNotBlank() }
            } else {
                listOf(query.trim())
            }

            val drafts = mutableListOf<PendingExpenseDraft>()
            for (seg in rawSegments) {
                val parsedAmt = AiExpenseParser.parseAmount(seg) ?: continue
                if (parsedAmt.amount <= 0.0) continue

                val matchResult = AiExpenseParser.matchCategory(seg, allCategories, pastExpenses)
                val matchedCategory = matchResult.category
                val cleanNote = AiExpenseParser.cleanExpenseNote(seg, matchedCategory.name)
                val (epoch, label) = if (lower.contains("yesterday")) {
                    val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                    Pair(c.timeInMillis, "Yesterday")
                } else Pair(System.currentTimeMillis(), "Today")

                drafts.add(
                    PendingExpenseDraft(
                        draftType = ExpenseDraftType.CREATE,
                        amount = parsedAmt.amount,
                        categoryName = matchedCategory.name,
                        categoryId = matchedCategory.id,
                        note = cleanNote,
                        dateEpoch = epoch,
                        dateFormatted = label,
                        paymentSource = if (seg.lowercase().contains("credit")) "CREDIT_CARD" else "INCOME",
                        matchReason = matchResult.matchReason,
                        isOthersFallback = matchResult.isOthersFallback,
                        suggestedCategoryName = matchResult.suggestedCategoryName,
                        candidateCategories = matchResult.candidateCategories,
                        clarificationNeeded = matchResult.clarificationNeeded
                    )
                )
            }

            if (drafts.isNotEmpty()) {
                val promptText = if (drafts.size == 1) {
                    val d = drafts[0]
                    if (d.isOthersFallback) {
                        "I added this under Others because I couldn't find a matching category. You can move it or create a new category:"
                    } else if (d.clarificationNeeded) {
                        "Which category does this belong to? Please confirm:"
                    } else {
                        "I've drafted this expense. Please confirm to log it to your records:"
                    }
                } else {
                    val total = drafts.sumOf { it.amount }
                    "I've drafted ${drafts.size} expenses (Total: $currencySymbol${total.toInt()}). Please confirm to log them:"
                }
                return LocalEvaluationResult(
                    answerText = promptText,
                    pendingDrafts = drafts
                )
            }
        }

        // 4. "Top spend" / "what is most i uses" / "biggest expense"
        if (lower.contains("top spend") || lower.contains("most i uses") || lower.contains("most i use") || lower.contains("highest") || lower.contains("biggest")) {
            val totalsJson = executeGetCategoryTotals(JSONObject().put("date_range", "this_month"))
            val categories = totalsJson.optJSONArray("categories")
            if (categories != null && categories.length() > 0) {
                val top1 = categories.getJSONObject(0)
                val topName = top1.getString("category")
                val topAmt = top1.getDouble("total")
                val topPct = top1.getDouble("percentage")
                return LocalEvaluationResult(
                    answerText = "Your top spend this month is $topName at $currencySymbol${topAmt.toInt()} ($topPct% of total spending). Tip: Setting a daily ceiling here can help you save faster."
                )
            } else {
                return LocalEvaluationResult(answerText = "You haven't logged any expenses for this month yet. Tap + or tell me to add one!")
            }
        }

        // 5. "Remaining balance" / "how much left" / "balance"
        if (lower.contains("balance") || lower.contains("how much left") || lower.contains("remaining")) {
            val balJson = executeGetBalanceSummary()
            val bal = balJson.getDouble("available_balance")
            val salary = balJson.getDouble("salary")
            val ceiling = balJson.getDouble("daily_safe_ceiling")
            val days = balJson.getInt("days_remaining")
            return LocalEvaluationResult(
                answerText = "You have $currencySymbol${bal.toInt()} remaining this month (from salary $currencySymbol${salary.toInt()}). Your safe daily spending ceiling is $currencySymbol${ceiling.toInt()}/day for the next $days days."
            )
        }

        // 6. "Where should I reduce spending?" / "cut spending"
        if (lower.contains("reduce") || lower.contains("cut") || lower.contains("save more")) {
            val trends = executeGetTrends(JSONObject().put("period", "recommend_cuts"))
            val topCats = trends.optJSONArray("top_categories")
            val balJson = executeGetBalanceSummary()
            val ceiling = balJson.getDouble("daily_safe_ceiling")

            if (topCats != null && topCats.length() > 0) {
                val sb = StringBuilder("To optimize your budget, focus on your highest outflow areas:\n")
                for (i in 0 until minOf(3, topCats.length())) {
                    val cat = topCats.getJSONObject(i)
                    sb.append("• ${cat.getString("category")}: $currencySymbol${cat.getDouble("amount").toInt()} (${cat.getDouble("percentage")}%) — suggested cut: $currencySymbol${cat.getDouble("suggested_cut").toInt()}\n")
                }
                sb.append("Keeping your daily expenses under $currencySymbol${ceiling.toInt()}/day will ensure you finish the month in the green.")
                return LocalEvaluationResult(answerText = sb.toString().trim())
            } else {
                return LocalEvaluationResult(answerText = "You have very few expenses logged this month! Your balance is safe.")
            }
        }

        // 7. "Compare this month vs last month"
        if (lower.contains("compare") || lower.contains("vs last month") || lower.contains("last month")) {
            val trends = executeGetTrends(JSONObject().put("period", "compare_last_month"))
            val thisTotal = trends.getDouble("this_month_total")
            val lastTotal = trends.getDouble("last_month_total")
            val diff = trends.getDouble("difference")
            val pct = trends.getDouble("percent_change")

            val direction = if (diff > 0) "more" else "less"
            return LocalEvaluationResult(
                answerText = "This month you've spent $currencySymbol${thisTotal.toInt()} compared to $currencySymbol${lastTotal.toInt()} last month ($currencySymbol${Math.abs(diff).toInt()} $direction, $pct%)."
            )
        }

        // 8. "Which day did I spend most?"
        if (lower.contains("which day") || lower.contains("most day") || lower.contains("highest day")) {
            val trends = executeGetTrends(JSONObject().put("period", "highest_spending_day"))
            val day = trends.getInt("highest_day_of_month")
            val dayTotal = trends.getDouble("highest_day_total")
            return LocalEvaluationResult(
                answerText = "Your highest spending day this month was Day $day, where you spent $currencySymbol${dayTotal.toInt()}."
            )
        }

        // 9. Category specific queries, e.g. "food", "rent", "travel"
        val matchedCat = allCategories.find { lower.contains(it.name.lowercase()) }
        if (matchedCat != null) {
            val expJson = executeGetExpenses(JSONObject().put("category", matchedCat.name))
            val count = expJson.getInt("count")
            val total = expJson.getDouble("total_amount")
            return LocalEvaluationResult(
                answerText = "You've spent $currencySymbol${total.toInt()} on ${matchedCat.name} across $count entries."
            )
        }

        // Default friendly answer
        val balJson = executeGetBalanceSummary()
        val bal = balJson.getDouble("available_balance")
        return LocalEvaluationResult(
            answerText = "You have $currencySymbol${bal.toInt()} remaining this month. Try asking 'Top spend?', 'How much on Food?', or 'Where should I reduce spending?'"
        )
    }
}
