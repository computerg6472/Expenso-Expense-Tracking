package com.spendwise.app.data.ai

import org.json.JSONArray
import org.json.JSONObject

enum class ExpenseDraftType {
    CREATE,
    UPDATE,
    DELETE
}

data class PendingExpenseDraft(
    val id: String = java.util.UUID.randomUUID().toString(),
    val draftType: ExpenseDraftType = ExpenseDraftType.CREATE,
    val targetExpenseId: String? = null,
    val originalAmount: Double? = null,
    val originalCategoryName: String? = null,
    val originalNote: String? = null,
    val amount: Double,
    val categoryName: String,
    val categoryId: String,
    val note: String,
    val dateEpoch: Long,
    val dateFormatted: String,
    val paymentSource: String = "INCOME",
    val matchReason: String = "",
    val isOthersFallback: Boolean = false,
    val suggestedCategoryName: String? = null,
    val candidateCategories: List<String> = emptyList(),
    val clarificationNeeded: Boolean = false
)

object AiTools {

    /**
     * Build the Gemini Function Declarations JSONArray for the API request with dynamic category enum.
     */
    fun getToolDeclarations(validCategoryNames: List<String> = emptyList()): JSONArray {
        // 1. get_expenses
        val getExpenses = JSONObject().apply {
            put("name", "get_expenses")
            put("description", "Fetch a filtered list of expenses from the user's ledger.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("category", JSONObject().put("type", "STRING").put("description", "Category name, e.g. Food, Rent, Travel, Shopping"))
                    put("date_from", JSONObject().put("type", "STRING").put("description", "Start date YYYY-MM-DD"))
                    put("date_to", JSONObject().put("type", "STRING").put("description", "End date YYYY-MM-DD"))
                    put("min_amount", JSONObject().put("type", "NUMBER").put("description", "Minimum expense amount"))
                    put("max_amount", JSONObject().put("type", "NUMBER").put("description", "Maximum expense amount"))
                    put("note_contains", JSONObject().put("type", "STRING").put("description", "Search query in note or description"))
                }
                put("properties", props)
            })
        }

        // 2. get_category_totals
        val getCategoryTotals = JSONObject().apply {
            put("name", "get_category_totals")
            put("description", "Get total spending grouped by category for a period (e.g. this_month, last_month, or all_time).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("date_range", JSONObject().put("type", "STRING").put("description", "Period: 'this_month', 'last_month', or 'all_time'"))
                }
                put("properties", props)
            })
        }

        // 3. get_balance_summary
        val getBalanceSummary = JSONObject().apply {
            put("name", "get_balance_summary")
            put("description", "Get the current balance summary: salary, total spent this month, available balance, daily safe spending ceiling, and credit card due.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        }

        // 4. get_trends
        val getTrends = JSONObject().apply {
            put("name", "get_trends")
            put("description", "Get spending comparison and trends (compare_last_month, highest_spending_day, top_categories).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("period", JSONObject().put("type", "STRING").put("description", "'compare_last_month' or 'highest_spending_day' or 'recommend_cuts'"))
                }
                put("properties", props)
            })
        }

        // 5. add_expense (Supports single and bulk)
        val addExpense = JSONObject().apply {
            put("name", "add_expense")
            put("description", "Draft adding one or more expenses to the user's ledger for user confirmation.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    val itemsObj = JSONObject().apply {
                        put("type", "ARRAY")
                        put("description", "List of expense items to add")
                        val itemSchema = JSONObject().apply {
                            put("type", "OBJECT")
                            val itemProps = JSONObject().apply {
                                put("amount", JSONObject().put("type", "NUMBER").put("description", "Amount spent"))
                                val categoryProp = JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Category name strictly matching one of user's categories or 'Others'")
                                    if (validCategoryNames.isNotEmpty()) {
                                        put("enum", JSONArray(validCategoryNames))
                                    }
                                }
                                put("category", categoryProp)
                                put("note", JSONObject().put("type", "STRING").put("description", "Cleaned concise description or merchant name (NOT the whole sentence)"))
                                put("date", JSONObject().put("type", "STRING").put("description", "Date in YYYY-MM-DD format (resolve 'today', 'yesterday' to actual date)"))
                                put("payment_method", JSONObject().put("type", "STRING").put("description", "Payment source: 'INCOME' (default/cash/upi) or 'CREDIT_CARD'"))
                            }
                            put("properties", itemProps)
                            put("required", JSONArray().put("amount").put("category"))
                        }
                        put("items", itemSchema)
                    }
                    put("items", itemsObj)
                }
                put("properties", props)
                put("required", JSONArray().put("items"))
            })
        }

        // 6. update_expense
        val updateExpense = JSONObject().apply {
            put("name", "update_expense")
            put("description", "Draft updating an existing expense. Resolves natural references like 'the last one', 'yesterday's dinner', 'the 500 rs swiggy order'.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("expense_reference", JSONObject().put("type", "STRING").put("description", "Natural reference to the expense: e.g. 'the last one', 'yesterday's dinner', '500 rs swiggy'"))
                    put("new_amount", JSONObject().put("type", "NUMBER").put("description", "Updated expense amount if changed"))
                    val catProp = JSONObject().apply {
                        put("type", "STRING")
                        put("description", "New category if changed")
                        if (validCategoryNames.isNotEmpty()) {
                            put("enum", JSONArray(validCategoryNames))
                        }
                    }
                    put("new_category", catProp)
                    put("new_note", JSONObject().put("type", "STRING").put("description", "Updated note/description if changed"))
                    put("new_date", JSONObject().put("type", "STRING").put("description", "Updated date YYYY-MM-DD if changed"))
                    put("new_payment_method", JSONObject().put("type", "STRING").put("description", "Updated payment method ('INCOME' or 'CREDIT_CARD')"))
                }
                put("properties", props)
                put("required", JSONArray().put("expense_reference"))
            })
        }

        // 7. delete_expense
        val deleteExpense = JSONObject().apply {
            put("name", "delete_expense")
            put("description", "Draft deleting an existing expense. Resolves natural references like 'delete the last expense', 'remove the starbucks one'.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("expense_reference", JSONObject().put("type", "STRING").put("description", "Natural reference to the expense to remove: e.g. 'the last expense', 'the 500 rs coffee'"))
                }
                put("properties", props)
                put("required", JSONArray().put("expense_reference"))
            })
        }

        val functionDeclarations = JSONArray().apply {
            put(getExpenses)
            put(getCategoryTotals)
            put(getBalanceSummary)
            put(getTrends)
            put(addExpense)
            put(updateExpense)
            put(deleteExpense)
        }

        return JSONArray().apply {
            put(JSONObject().put("functionDeclarations", functionDeclarations))
        }
    }
}
