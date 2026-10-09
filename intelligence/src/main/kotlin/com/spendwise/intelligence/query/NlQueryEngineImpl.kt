package com.spendwise.intelligence.query

import com.spendwise.intelligence.capability.AiFeature
import com.spendwise.intelligence.capability.CapabilityProvider
import com.spendwise.intelligence.domain.contract.CategoryReadApi
import com.spendwise.intelligence.domain.contract.ExpenseReadApi
import com.spendwise.intelligence.domain.contract.SalaryReadApi
import com.spendwise.intelligence.domain.feature.NlQueryAnswer
import com.spendwise.intelligence.domain.feature.NlQueryEngine
import com.spendwise.intelligence.domain.model.ExpenseRecord
import com.spendwise.intelligence.domain.model.PaymentSource
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NlQueryEngineImpl @Inject constructor(
    private val expenseReadApi: ExpenseReadApi,
    private val categoryReadApi: CategoryReadApi,
    private val salaryReadApi: SalaryReadApi,
    private val capabilityProvider: CapabilityProvider
) : NlQueryEngine {

    override suspend fun answerQuery(query: String): NlQueryAnswer {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return NlQueryAnswer(query = query, answerText = "Please ask a question about your expenses, budgets, or balances.")
        }

        val lower = trimmed.lowercase()
        val allExpenses = expenseReadApi.getAllExpenses()
        val categories = categoryReadApi.getCategories()
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1

        val currentMonthSummary = expenseReadApi.getMonthSummary(currentYear, currentMonth)

        // 1. "How much left" / "Balance" / "Available"
        if (lower.contains("balance") || lower.contains("how much left") || lower.contains("remaining")) {
            val balance = currentMonthSummary?.availableBalance ?: 0.0
            val salary = currentMonthSummary?.salary ?: 0.0
            return NlQueryAnswer(
                query = query,
                answerText = "You have ₹${balance.toInt()} available balance remaining this month from your salary of ₹${salary.toInt()}.",
                totalAmount = balance
            )
        }

        // 2. "Highest expense" / "Biggest spend" / "Max"
        if (lower.contains("highest") || lower.contains("biggest") || lower.contains("most expensive") || lower.contains("maximum")) {
            val maxExpense = allExpenses.maxByOrNull { it.amount }
            if (maxExpense != null) {
                val catName = categories.find { it.id == maxExpense.categoryId }?.name ?: "Expense"
                return NlQueryAnswer(
                    query = query,
                    answerText = "Your highest recorded expense was ₹${maxExpense.amount.toInt()} on $catName${if (maxExpense.note.isNotBlank()) " (${maxExpense.note})" else ""}.",
                    matchedExpenses = listOf(maxExpense),
                    totalAmount = maxExpense.amount
                )
            }
        }

        // 3. Credit Card spend queries
        if (lower.contains("credit card") || lower.contains("card spend") || lower.contains("on credit")) {
            val ccExpenses = allExpenses.filter { it.source == PaymentSource.CREDIT_CARD }
            val total = ccExpenses.sumOf { it.amount }
            return NlQueryAnswer(
                query = query,
                answerText = "You have spent a total of ₹${total.toInt()} via Credit Card across ${ccExpenses.size} transactions.",
                matchedExpenses = ccExpenses.take(10),
                totalAmount = total
            )
        }

        // 4. Category-specific queries: e.g. "food", "travel", "groceries", "rent"
        val matchedCategory = categories.find { cat ->
            lower.contains(cat.name.lowercase()) ||
            (cat.name.equals("Food", ignoreCase = true) && (lower.contains("dining") || lower.contains("restaurant") || lower.contains("swiggy"))) ||
            (cat.name.equals("Travel", ignoreCase = true) && (lower.contains("uber") || lower.contains("petrol") || lower.contains("transport"))) ||
            (cat.name.equals("Grocery", ignoreCase = true) && (lower.contains("groceries") || lower.contains("blinkit") || lower.contains("supermarket"))) ||
            (cat.name.contains("Hair", ignoreCase = true) && (lower.contains("hair") || lower.contains("salon") || lower.contains("barber") || lower.contains("haircut"))) ||
            (cat.name.contains("Skin", ignoreCase = true) && (lower.contains("skin") || lower.contains("skincare") || lower.contains("sunscreen") || lower.contains("facial"))) ||
            (cat.name.contains("Order", ignoreCase = true) && (lower.contains("order") || lower.contains("amazon") || lower.contains("flipkart") || lower.contains("online shopping") || lower.contains("myntra")))
        }

        if (matchedCategory != null) {
            val categoryExpenses = allExpenses.filter { it.categoryId == matchedCategory.id }
            val total = categoryExpenses.sumOf { it.amount }
            return NlQueryAnswer(
                query = query,
                answerText = "You've spent ₹${total.toInt()} on ${matchedCategory.name} across ${categoryExpenses.size} transactions.",
                matchedExpenses = categoryExpenses.take(10),
                totalAmount = total
            )
        }

        // 5. Total spending
        if (lower.contains("total") || lower.contains("how much spent") || lower.contains("spent so far")) {
            val currentMonthExpenses = allExpenses.filter {
                val c = Calendar.getInstance().apply { timeInMillis = it.dateEpochMillis }
                c.get(Calendar.YEAR) == currentYear && (c.get(Calendar.MONTH) + 1) == currentMonth
            }
            val total = currentMonthExpenses.sumOf { it.amount }
            return NlQueryAnswer(
                query = query,
                answerText = "You've spent ₹${total.toInt()} so far this month across ${currentMonthExpenses.size} expenses.",
                matchedExpenses = currentMonthExpenses.take(10),
                totalAmount = total
            )
        }

        // Fallback search by keyword
        val matched = allExpenses.filter {
            it.note.contains(trimmed, ignoreCase = true) || it.merchant?.contains(trimmed, ignoreCase = true) == true
        }

        return if (matched.isNotEmpty()) {
            val total = matched.sumOf { it.amount }
            NlQueryAnswer(
                query = query,
                answerText = "Found ${matched.size} expenses matching '$trimmed', totaling ₹${total.toInt()}.",
                matchedExpenses = matched.take(10),
                totalAmount = total
            )
        } else {
            NlQueryAnswer(
                query = query,
                answerText = "I couldn't find specific expenses matching '$trimmed'. Try asking 'How much on Food?' or 'What is my balance?'"
            )
        }
    }
}
