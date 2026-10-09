package com.spendwise.app.ui.screen.addexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.local.entity.Expense
import com.spendwise.app.data.local.entity.Category
import com.spendwise.app.domain.model.PaymentSource
import com.spendwise.app.domain.repository.CategoryRepository
import com.spendwise.app.domain.repository.ExpenseRepository
import com.spendwise.app.domain.usecase.AddExpenseUseCase
import com.spendwise.app.domain.usecase.DeleteExpenseUseCase
import com.spendwise.intelligence.domain.feature.NlpExpenseParser
import com.spendwise.intelligence.domain.feature.SmartCategorizer
import com.spendwise.intelligence.domain.model.CategoryInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AddExpenseUiState(
    val isLoading: Boolean = false,
    val categories: List<Category> = emptyList(),
    val amount: String = "",
    val amountError: String? = null,
    val selectedCategory: Category? = null,
    val paymentSource: PaymentSource = PaymentSource.INCOME,
    val selectedDate: Long = System.currentTimeMillis(),
    val note: String = "",
    val isEditMode: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val error: String? = null,
    val isSplit: Boolean = false,
    val splitPeopleCount: Int = 2,
    val myNetShare: String = "",
    val receivableAmount: String = "",
    val splitValidationError: String? = null,
    val isReimbursed: Boolean = false,
    val extraCreditAmount: String = ""
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val expenseRepository: ExpenseRepository,
    private val addExpenseUseCase: AddExpenseUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase,
    private val nlpExpenseParser: NlpExpenseParser,
    private val smartCategorizer: SmartCategorizer
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    private var editingExpenseId: String? = null

    init {
        viewModelScope.launch {
            categoryRepository.observeAll().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
    }

    fun loadExpense(expenseId: String) {
        viewModelScope.launch {
            val expense = expenseRepository.getExpenseById(expenseId) ?: return@launch
            val category = categoryRepository.getById(expense.categoryId)
            editingExpenseId = expenseId
            val netShare = if (expense.isSplit) {
                if (expense.netUserExpense > 0.0) expense.netUserExpense.toString()
                else (expense.amount - expense.receivableAmount).coerceAtLeast(0.0).toString()
            } else ""
            val rec = if (expense.isSplit && expense.receivableAmount > 0.0) expense.receivableAmount.toString() else ""

            _uiState.update { state ->
                state.copy(
                    amount = expense.amount.toString(),
                    selectedCategory = category,
                    paymentSource = PaymentSource.entries.first { it.name == expense.paymentSource },
                    selectedDate = expense.dateEpoch,
                    note = expense.note,
                    isEditMode = true,
                    isSplit = expense.isSplit,
                    myNetShare = netShare,
                    receivableAmount = rec,
                    splitValidationError = null,
                    isReimbursed = expense.isReimbursed,
                    extraCreditAmount = if (expense.extraCreditAmount > 0.0) expense.extraCreditAmount.toString() else ""
                )
            }
        }
    }

    fun setAmount(amount: String) {
        val cleaned = amount.filter { it.isDigit() || it == '.' }
        _uiState.update { state ->
            val total = cleaned.toDoubleOrNull() ?: 0.0
            if (state.isSplit) {
                val count = state.splitPeopleCount.coerceAtLeast(2)
                val share = if (total > 0) total / count else 0.0
                val rec = (total - share).coerceAtLeast(0.0)
                state.copy(
                    amount = cleaned,
                    amountError = null,
                    myNetShare = if (total > 0) "%.2f".format(java.util.Locale.US, share) else "",
                    receivableAmount = if (total > 0) "%.2f".format(java.util.Locale.US, rec) else "",
                    splitValidationError = null
                )
            } else {
                state.copy(amount = cleaned, amountError = null)
            }
        }
    }

    fun setSplit(enabled: Boolean) {
        _uiState.update { state ->
            val total = state.amount.toDoubleOrNull() ?: 0.0
            if (enabled) {
                val count = state.splitPeopleCount.coerceAtLeast(2)
                val share = if (total > 0) total / count else 0.0
                val rec = (total - share).coerceAtLeast(0.0)
                state.copy(
                    isSplit = true,
                    myNetShare = if (total > 0) "%.2f".format(java.util.Locale.US, share) else "",
                    receivableAmount = if (total > 0) "%.2f".format(java.util.Locale.US, rec) else "",
                    splitValidationError = null
                )
            } else {
                state.copy(isSplit = false, myNetShare = "", receivableAmount = "", splitValidationError = null)
            }
        }
    }

    fun setSplitPeopleCount(count: Int) {
        val safeCount = count.coerceIn(2, 50)
        _uiState.update { state ->
            val total = state.amount.toDoubleOrNull() ?: 0.0
            val share = if (total > 0) total / safeCount else 0.0
            val rec = (total - share).coerceAtLeast(0.0)
            state.copy(
                splitPeopleCount = safeCount,
                myNetShare = if (total > 0) "%.2f".format(java.util.Locale.US, share) else "",
                receivableAmount = if (total > 0) "%.2f".format(java.util.Locale.US, rec) else "",
                splitValidationError = null
            )
        }
    }

    fun setMyNetShare(shareInput: String) {
        val cleaned = shareInput.filter { it.isDigit() || it == '.' }
        _uiState.update { state ->
            val total = state.amount.toDoubleOrNull() ?: 0.0
            val s = cleaned.toDoubleOrNull()
            when {
                s == null -> state.copy(myNetShare = cleaned)
                s > total -> state.copy(
                    myNetShare = cleaned,
                    receivableAmount = "0.00",
                    splitValidationError = "Your share (₹%.2f) cannot exceed total expense (₹%.2f)".format(s, total)
                )
                s < 0.0 -> state.copy(
                    myNetShare = cleaned,
                    splitValidationError = "Share cannot be negative"
                )
                else -> {
                    val rec = (total - s).coerceAtLeast(0.0)
                    state.copy(
                        myNetShare = cleaned,
                        receivableAmount = "%.2f".format(java.util.Locale.US, rec),
                        splitValidationError = null
                    )
                }
            }
        }
    }

    fun setReceivableAmount(recInput: String) {
        val cleaned = recInput.filter { it.isDigit() || it == '.' }
        _uiState.update { state ->
            val total = state.amount.toDoubleOrNull() ?: 0.0
            val r = cleaned.toDoubleOrNull()
            when {
                r == null -> state.copy(receivableAmount = cleaned)
                r > total -> state.copy(
                    receivableAmount = cleaned,
                    myNetShare = "0.00",
                    splitValidationError = "Receivable (₹%.2f) cannot exceed total expense (₹%.2f)".format(r, total)
                )
                r < 0.0 -> state.copy(
                    receivableAmount = cleaned,
                    splitValidationError = "Receivable cannot be negative"
                )
                else -> {
                    val share = (total - r).coerceAtLeast(0.0)
                    state.copy(
                        receivableAmount = cleaned,
                        myNetShare = "%.2f".format(java.util.Locale.US, share),
                        splitValidationError = null
                    )
                }
            }
        }
    }

    fun setReimbursed(reimbursed: Boolean) {
        _uiState.update { it.copy(isReimbursed = reimbursed) }
    }

    fun setExtraCreditAmount(amount: String) {
        val cleaned = amount.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(extraCreditAmount = cleaned) }
    }

    fun setCategory(category: Category) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setPaymentSource(source: PaymentSource) {
        _uiState.update { it.copy(paymentSource = source) }
    }

    fun setDate(epoch: Long) {
        _uiState.update { it.copy(selectedDate = epoch) }
    }

    fun setNote(note: String) {
        _uiState.update { it.copy(note = note.take(200)) }
    }

    fun parseSmartPrompt(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            val parsed = nlpExpenseParser.parse(prompt)

            val formattedAmount = if (parsed.amount != null) {
                val amt = parsed.amount!!
                if (amt % 1.0 == 0.0) amt.toLong().toString() else amt.toString()
            } else {
                val fallbackAmt = com.spendwise.app.data.ai.AiExpenseParser.parseAmount(prompt)?.amount
                if (fallbackAmt != null) {
                    if (fallbackAmt % 1.0 == 0.0) fallbackAmt.toLong().toString() else fallbackAmt.toString()
                } else _uiState.value.amount
            }

            val detectedSource = when (parsed.source) {
                com.spendwise.intelligence.domain.model.PaymentSource.CREDIT_CARD -> PaymentSource.CREDIT_CARD
                com.spendwise.intelligence.domain.model.PaymentSource.INCOME -> PaymentSource.INCOME
                null -> _uiState.value.paymentSource
            }

            val detectedDate = parsed.dateEpochMillis ?: _uiState.value.selectedDate

            val currentCategories = _uiState.value.categories
            val promptLower = prompt.lowercase()

            // 1. Direct match on Category Name or Category Aliases
            val aliasMatched = currentCategories.find { cat ->
                cat.name.equals(parsed.categoryHint, ignoreCase = true) ||
                cat.name.lowercase() in promptLower ||
                cat.aliases.split(",").any { alias ->
                    val a = alias.trim().lowercase()
                    a.isNotBlank() && promptLower.contains(Regex("""\b${Regex.escape(a)}\b"""))
                }
            }

            // 2. AiExpenseParser Semantic Matcher
            val aiCatResult = com.spendwise.app.data.ai.AiExpenseParser.matchCategory(prompt, currentCategories)

            // 3. ML / SmartCategorizer Suggestion
            val categoryInfos = currentCategories.map {
                CategoryInfo(
                    id = it.id,
                    name = it.name,
                    isDefault = it.isDefault,
                    colorHex = it.colorHex,
                    iconName = it.iconName
                )
            }
            val suggestions = smartCategorizer.suggestCategory(
                merchant = parsed.merchant,
                note = parsed.note,
                amount = parsed.amount ?: 0.0,
                categories = categoryInfos
            )

            val matchedCat = aliasMatched
                ?: (if (!aiCatResult.isOthersFallback && aiCatResult.score >= 0.5) aiCatResult.category else null)
                ?: suggestions.firstOrNull()?.category?.let { matchedInfo ->
                    currentCategories.find { it.id == matchedInfo.id }
                }
                ?: (if (!aiCatResult.isOthersFallback) aiCatResult.category else null)
                ?: parsed.categoryHint?.let { hint ->
                    currentCategories.find { it.name.contains(hint, ignoreCase = true) }
                }
                ?: _uiState.value.selectedCategory
                ?: currentCategories.firstOrNull()

            // Build clean note
            val cleanedAiNote = if (matchedCat != null) {
                com.spendwise.app.data.ai.AiExpenseParser.cleanExpenseNote(prompt, matchedCat.name)
            } else ""

            val cleanNote = when {
                parsed.note.isNotBlank() -> parsed.note
                cleanedAiNote.isNotBlank() && !cleanedAiNote.equals("Expense", ignoreCase = true) -> cleanedAiNote
                !parsed.merchant.isNullOrBlank() -> parsed.merchant!!
                matchedCat != null -> matchedCat.name
                else -> prompt.take(50)
            }

            _uiState.update { state ->
                val total = formattedAmount.toDoubleOrNull() ?: 0.0
                val net = if (state.isSplit && total > 0) {
                    val count = state.splitPeopleCount.coerceAtLeast(2)
                    "%.2f".format(java.util.Locale.US, total / count)
                } else state.myNetShare
                val rec = if (state.isSplit && total > 0) {
                    val count = state.splitPeopleCount.coerceAtLeast(2)
                    "%.2f".format(java.util.Locale.US, total - (total / count))
                } else state.receivableAmount

                state.copy(
                    amount = formattedAmount,
                    selectedCategory = matchedCat,
                    paymentSource = detectedSource,
                    selectedDate = detectedDate,
                    note = cleanNote,
                    myNetShare = net,
                    receivableAmount = rec
                )
            }
        }
    }


    fun saveExpense() {
        val state = _uiState.value
        val amount = state.amount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(amountError = "Enter a valid amount") }
            return
        }
        if (state.selectedCategory == null) {
            _uiState.update { it.copy(error = "Please select a category") }
            return
        }

        if (state.isSplit && state.splitValidationError != null) {
            _uiState.update { it.copy(error = state.splitValidationError) }
            return
        }

        val cal = Calendar.getInstance().apply { timeInMillis = state.selectedDate }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1

        val recAmount = if (state.isSplit) (state.receivableAmount.toDoubleOrNull() ?: 0.0) else 0.0
        val netUser = if (state.isSplit) {
            state.myNetShare.toDoubleOrNull() ?: (amount - recAmount).coerceAtLeast(0.0)
        } else amount
        val extraCred = state.extraCreditAmount.toDoubleOrNull() ?: 0.0

        // Optimistic UI update: instantly notify screen to dismiss without blocking user interaction
        _uiState.update { it.copy(savedSuccessfully = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (editingExpenseId != null) {
                    val existing = expenseRepository.getExpenseById(editingExpenseId!!) ?: return@launch
                    val updated = existing.copy(
                        amount = amount,
                        paymentSource = state.paymentSource.name,
                        categoryId = state.selectedCategory.id,
                        dateEpoch = state.selectedDate,
                        year = year,
                        month = month,
                        note = state.note,
                        isSplit = state.isSplit,
                        isReimbursed = state.isReimbursed,
                        receivableAmount = recAmount,
                        netUserExpense = netUser,
                        extraCreditAmount = extraCred,
                        updatedAt = System.currentTimeMillis()
                    )
                    expenseRepository.updateExpense(updated)
                } else {
                    val expense = Expense(
                        amount = amount,
                        paymentSource = state.paymentSource.name,
                        categoryId = state.selectedCategory.id,
                        dateEpoch = state.selectedDate,
                        year = year,
                        month = month,
                        note = state.note,
                        isSplit = state.isSplit,
                        isReimbursed = state.isReimbursed,
                        receivableAmount = recAmount,
                        netUserExpense = netUser,
                        extraCreditAmount = extraCred
                    )
                    addExpenseUseCase(expense)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save: ${e.message}") }
            }
        }
    }

    fun deleteExpense() {
        val id = editingExpenseId ?: return
        viewModelScope.launch {
            try {
                val expense = expenseRepository.getExpenseById(id) ?: return@launch
                deleteExpenseUseCase(expense)
                _uiState.update { it.copy(savedSuccessfully = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete: ${e.message}") }
            }
        }
    }
}
