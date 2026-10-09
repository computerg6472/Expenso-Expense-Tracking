package com.spendwise.app.data.ai

import com.spendwise.app.data.cloud.GeminiCallResult
import com.spendwise.app.data.cloud.GeminiClient
import com.spendwise.app.data.local.entity.Expense
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.repository.CategoryRepository
import com.spendwise.app.domain.repository.ExpenseRepository
import com.spendwise.app.domain.repository.IncomeRepository
import com.spendwise.app.domain.usecase.AddExpenseUseCase
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class InsightType {
    SPENDING_SUMMARY,
    OVERSPENDING_ALERTS,
    MONTH_END_PREDICTION_EXPLANATION
}

data class AiChatResponse(
    val text: String,
    val pendingDrafts: List<PendingExpenseDraft>? = null,
    val isError: Boolean = false,
    val isOffline: Boolean = false
)

data class SaveDraftsResult(
    val summary: String,
    val savedExpenseIds: List<String>
)

@Singleton
class AiIntelligenceService @Inject constructor(
    private val geminiClient: GeminiClient,
    private val aiToolExecutor: AiToolExecutor,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val incomeRepository: IncomeRepository,
    private val addExpenseUseCase: AddExpenseUseCase,
    private val appPreferences: AppPreferences
) {

    // In-memory cache for app-wide insights: Key -> Pair(dataVersionHash, insightText)
    private val insightCache = ConcurrentHashMap<InsightType, Pair<String, String>>()

    // In-memory audit map for undo actions (keyed by expense ID)
    private val undoActions = ConcurrentHashMap<String, suspend () -> Unit>()

    /**
     * Natural language question answering and intent handling with tools.
     * Always loads category list fresh from the database on every request.
     * Supplies dynamic category enum so Gemini cannot output hallucinated categories.
     */
    suspend fun askAi(
        query: String,
        recentHistory: List<Pair<Boolean, String>> // List of (isUser, text)
    ): AiChatResponse {
        val currency = appPreferences.currencySymbol.first()
        val currentIncome = incomeRepository.getOrCreateCurrentMonth()
        val allCategories = categoryRepository.getAll()
        val categoryNames = allCategories.map { it.name }

        val systemPrompt = GeminiConfig.buildSystemPrompt(
            currencySymbol = currency,
            salary = currentIncome.salary,
            creditLimit = 50000.0,
            categoryNames = categoryNames
        )

        // Build contents array with short conversation memory (up to last 6 messages)
        val contentsArray = JSONArray()
        val trimmedHistory = recentHistory.takeLast(6)
        trimmedHistory.forEach { (isUser, text) ->
            contentsArray.put(JSONObject().apply {
                put("role", if (isUser) "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", text)))
            })
        }
        // Append current user message
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", query)))
        })

        // Step 1: Call Gemini with dynamic valid category names enum
        val initialCall = geminiClient.callGemini(
            systemInstruction = systemPrompt,
            contents = contentsArray,
            includeTools = true,
            validCategoryNames = categoryNames
        )

        return when (initialCall) {
            is GeminiCallResult.FunctionCallRequest -> {
                // Execute tool locally
                val toolResult = aiToolExecutor.executeTool(initialCall.callName, initialCall.callArgs)

                // If adding, updating, or deleting expense, return confirmation drafts directly!
                if (toolResult.pendingDrafts != null && toolResult.pendingDrafts.isNotEmpty()) {
                    val drafts = toolResult.pendingDrafts
                    val first = drafts.first()
                    val summaryText = when (first.draftType) {
                        ExpenseDraftType.CREATE -> {
                            if (drafts.size == 1) {
                                if (first.isOthersFallback) {
                                    "I added this under Others because I couldn't find a matching category. You can move it or create a new category:"
                                } else if (first.clarificationNeeded) {
                                    "Which category does this belong to? Please select:"
                                } else {
                                    "I've drafted this expense. Please confirm to log it to your records:"
                                }
                            } else {
                                val total = drafts.sumOf { it.amount }
                                "I've drafted ${drafts.size} expenses (Total: $currency${total.toInt()}). Please confirm to log them:"
                            }
                        }
                        ExpenseDraftType.UPDATE -> "I've prepared this expense update. Please review before saving:"
                        ExpenseDraftType.DELETE -> "Please confirm deleting this expense:"
                    }
                    return AiChatResponse(
                        text = summaryText,
                        pendingDrafts = toolResult.pendingDrafts
                    )
                }

                // Send tool execution output back to Gemini to draft the final natural language answer
                val toolCallObj = JSONObject().apply {
                    put("role", "model")
                    val callPart = JSONObject().apply {
                        put("functionCall", JSONObject().apply {
                            put("name", initialCall.callName)
                            put("args", initialCall.callArgs)
                        })
                    }
                    put("parts", JSONArray().put(callPart))
                }

                val toolResponseObj = JSONObject().apply {
                    put("role", "user")
                    val responsePart = JSONObject().apply {
                        put("functionResponse", JSONObject().apply {
                            put("name", initialCall.callName)
                            put("response", toolResult.resultJson)
                        })
                    }
                    put("parts", JSONArray().put(responsePart))
                }

                contentsArray.put(toolCallObj)
                contentsArray.put(toolResponseObj)

                val finalCall = geminiClient.callGemini(systemPrompt, contentsArray, includeTools = false)
                when (finalCall) {
                    is GeminiCallResult.TextSuccess -> AiChatResponse(text = finalCall.responseText)
                    is GeminiCallResult.FunctionCallRequest -> AiChatResponse(text = "Analysis complete.")
                    is GeminiCallResult.Error -> {
                        // Fallback to local evaluation
                        val fallback = aiToolExecutor.fallbackEvaluateLocally(query, currency)
                        AiChatResponse(
                            text = fallback.answerText,
                            pendingDrafts = fallback.pendingDrafts,
                            isOffline = finalCall.isNetworkOffline
                        )
                    }
                }
            }

            is GeminiCallResult.TextSuccess -> {
                AiChatResponse(text = initialCall.responseText)
            }

            is GeminiCallResult.Error -> {
                // Network failure or offline: execute locally with 100% accurate Room data
                val fallback = aiToolExecutor.fallbackEvaluateLocally(query, currency)
                AiChatResponse(
                    text = fallback.answerText,
                    pendingDrafts = fallback.pendingDrafts,
                    isOffline = initialCall.isNetworkOffline
                )
            }
        }
    }

    /**
     * Confirms and persists draft expenses (Create, Update, or Delete) to Room and Cloud.
     * Refreshes Home, History, and Reports immediately.
     */
    suspend fun confirmAndSaveDrafts(drafts: List<PendingExpenseDraft>): SaveDraftsResult {
        val cal = Calendar.getInstance()
        var lastCategoryName = ""
        var lastMonthSpend = 0.0
        val savedIds = mutableListOf<String>()

        for (draft in drafts) {
            when (draft.draftType) {
                ExpenseDraftType.CREATE -> {
                    cal.timeInMillis = draft.dateEpoch
                    val year = cal.get(Calendar.YEAR)
                    val month = cal.get(Calendar.MONTH) + 1

                    val expense = Expense(
                        amount = draft.amount,
                        paymentSource = draft.paymentSource,
                        categoryId = draft.categoryId,
                        dateEpoch = draft.dateEpoch,
                        year = year,
                        month = month,
                        note = draft.note
                    )
                    addExpenseUseCase(expense)
                    savedIds.add(expense.id)
                    undoActions[expense.id] = {
                        expenseRepository.deleteExpense(expense)
                    }

                    lastCategoryName = draft.categoryName
                    val expensesForCat = expenseRepository.getByCategory(draft.categoryId)
                    lastMonthSpend = expensesForCat.filter { it.year == year && it.month == month }.sumOf { it.amount }
                }

                ExpenseDraftType.UPDATE -> {
                    val targetId = draft.targetExpenseId ?: continue
                    val existing = expenseRepository.getExpenseById(targetId)
                    if (existing != null) {
                        val originalCopy = existing.copy()
                        cal.timeInMillis = draft.dateEpoch
                        val updated = existing.copy(
                            amount = draft.amount,
                            paymentSource = draft.paymentSource,
                            categoryId = draft.categoryId,
                            dateEpoch = draft.dateEpoch,
                            year = cal.get(Calendar.YEAR),
                            month = cal.get(Calendar.MONTH) + 1,
                            note = draft.note
                        )
                        expenseRepository.updateExpense(updated)
                        savedIds.add(updated.id)
                        undoActions[updated.id] = {
                            expenseRepository.updateExpense(originalCopy)
                        }
                    }
                }

                ExpenseDraftType.DELETE -> {
                    val targetId = draft.targetExpenseId ?: continue
                    val existing = expenseRepository.getExpenseById(targetId)
                    if (existing != null) {
                        val deletedCopy = existing.copy()
                        expenseRepository.deleteExpense(existing)
                        savedIds.add(existing.id)
                        undoActions[existing.id] = {
                            expenseRepository.updateExpense(deletedCopy.copy(isDeleted = false, updatedAt = System.currentTimeMillis()))
                        }
                    }
                }
            }
        }

        // Invalidate insights cache because data changed
        insightCache.clear()

        val first = drafts.firstOrNull()
        val summary = when (first?.draftType) {
            ExpenseDraftType.DELETE -> "Deleted ${first.note} (₹${first.amount.toInt()}) from ${first.categoryName}."
            ExpenseDraftType.UPDATE -> "Updated ${first.note} (₹${first.amount.toInt()} in ${first.categoryName})."
            else -> {
                if (drafts.size == 1) {
                    val d = drafts[0]
                    "Added ₹${d.amount.toInt()} to ${d.categoryName} (${d.note}). Total $lastCategoryName this month: ₹${lastMonthSpend.toInt()}."
                } else {
                    val total = drafts.sumOf { it.amount }
                    "Added ${drafts.size} expenses successfully (Total: ₹${total.toInt()})."
                }
            }
        }
        return SaveDraftsResult(summary, savedIds)
    }

    suspend fun undoSaveDrafts(expenseIds: List<String>) {
        for (id in expenseIds) {
            val action = undoActions[id]
            if (action != null) {
                action.invoke()
                undoActions.remove(id)
            } else {
                val exp = expenseRepository.getExpenseById(id)
                if (exp != null) {
                    expenseRepository.deleteExpense(exp)
                }
            }
        }
        insightCache.clear()
    }

    /**
     * Auto-generate 5 to 10 aliases per category at creation time using Gemini Flash.
     * Store them in the category table in Room and keep them synced with Firestore.
     */
    suspend fun generateAliasesForCategory(categoryName: String): List<String> {
        val prompt = "Generate 6 to 8 short lowercase comma-separated synonyms, merchants, and common expense keywords for the expense category '$categoryName' in India (e.g. for Food: dinner, lunch, snacks, swiggy, zomato, cafe, tea). Output ONLY the comma-separated words, nothing else."
        val contents = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            })
        }
        val result = geminiClient.callGemini(
            systemInstruction = "You are a concise financial taxonomy assistant. Output ONLY comma-separated lowercase aliases.",
            contents = contents,
            includeTools = false
        )
        if (result is GeminiCallResult.TextSuccess) {
            val list = result.responseText.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() && it.length >= 2 }
            if (list.size >= 3) return list
        }
        return getFallbackAliases(categoryName)
    }

    private fun getFallbackAliases(name: String): List<String> {
        val lower = name.lowercase().trim()
        return when {
            lower.contains("gym") || lower.contains("fitness") -> listOf("gym", "workout", "fitness", "protein", "trainer", "cult")
            lower.contains("pet") || lower.contains("dog") || lower.contains("cat") -> listOf("pet", "dog", "cat", "vet", "pet food", "pedigree", "grooming")
            lower.contains("book") || lower.contains("education") -> listOf("books", "course", "tuition", "stationery", "exam", "udemy")
            lower.contains("movie") || lower.contains("entertainment") -> listOf("movie", "cinema", "pvr", "netflix", "tickets", "show")
            lower.contains("gift") -> listOf("gift", "presents", "birthday", "anniversary", "flowers", "cards")
            lower.contains("charity") || lower.contains("donation") -> listOf("donation", "charity", "temple", "ngo", "help")
            else -> listOf(lower, "${lower} expense", "${lower} bill", "${lower} fee")
        }
    }

    /**
     * App-wide analytics insights for Forecast, Reports, and Home.
     * Cached by data version hash to eliminate redundant API calls.
     */
    suspend fun getInsights(type: InsightType, forceRefresh: Boolean = false): String {
        val allExpenses = expenseRepository.observeAll().first()
        val dataVersion = "${allExpenses.size}-${allExpenses.sumOf { it.amount }}"

        if (!forceRefresh) {
            val cached = insightCache[type]
            if (cached != null && cached.first == dataVersion) {
                return cached.second
            }
        }

        val prompt = when (type) {
            InsightType.SPENDING_SUMMARY -> "Provide a concise 2-sentence spending summary for this month."
            InsightType.OVERSPENDING_ALERTS -> "Are there any overspending patterns or high category outflows this month? Keep it to 2 sentences."
            InsightType.MONTH_END_PREDICTION_EXPLANATION -> "Explain in 2 sentences whether current month spending trajectory is on-track or over budget based on daily burn rate."
        }

        val response = askAi(prompt, emptyList())
        val resultText = response.text
        insightCache[type] = Pair(dataVersion, resultText)
        return resultText
    }
}
