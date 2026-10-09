package com.spendwise.app.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.local.entity.Category
import com.spendwise.app.data.local.entity.Expense
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.model.ExpenseWithCategory
import com.spendwise.app.domain.model.PaymentSource
import com.spendwise.app.domain.repository.CategoryRepository
import com.spendwise.app.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HistoryUiState(
    val expenses: List<ExpenseWithCategory> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currencySymbol: String = "\u20B9",
    val searchQuery: String = "",
    val selectedSource: PaymentSource? = null,
    val selectedCategoryId: String? = null,
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val onlySplits: Boolean = false,
    val totalPendingReceivable: Double = 0.0,
    val isLoading: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedSource = MutableStateFlow<PaymentSource?>(null)
    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    private val _onlySplits = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private data class FilterParams(
        val query: String,
        val source: PaymentSource?,
        val catId: String?,
        val month: Int,
        val year: Int,
        val onlySplits: Boolean
    )

    private data class BaseHistoryData(
        val allExpenses: List<Expense>,
        val categories: List<Category>,
        val currency: String,
        val totalPending: Double
    )

    private fun loadData() {
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            val debouncedSearch = _searchQuery.debounce(250L)

            val searchAndSourceFlow = combine(debouncedSearch, _selectedSource, _selectedCategoryId) { q, s, c ->
                Triple(q, s, c)
            }
            val dateAndSplitFlow = combine(_selectedMonth, _selectedYear, _onlySplits) { m, y, sp ->
                Triple(m, y, sp)
            }
            val filterFlow = combine(searchAndSourceFlow, dateAndSplitFlow) { (q, s, c), (m, y, sp) ->
                FilterParams(q, s, c, m, y, sp)
            }

            val baseDataFlow = combine(
                expenseRepository.observeAll(),
                categoryRepository.observeAll(),
                appPreferences.currencySymbol,
                expenseRepository.observeTotalPendingReceivable()
            ) { allExpenses, categories, currency, totalPending ->
                BaseHistoryData(allExpenses, categories, currency, totalPending)
            }

            combine(baseDataFlow, filterFlow) { base, filter ->
                val catMap = base.categories.associateBy { it.id }
                val trimmedQuery = filter.query.trim()
                val filtered = base.allExpenses.filter { exp ->
                    val matchesSearch = trimmedQuery.isEmpty() || exp.note.contains(trimmedQuery, ignoreCase = true)
                    val matchesSource = filter.source == null || exp.paymentSource == filter.source.name
                    val matchesCat = filter.catId == null || exp.categoryId == filter.catId
                    val matchesDate = exp.year == filter.year && exp.month == filter.month
                    val matchesSplit = !filter.onlySplits || exp.isSplit
                    matchesSearch && matchesSource && matchesCat && matchesDate && matchesSplit
                }.map { exp ->
                    ExpenseWithCategory(
                        expense = exp,
                        category = catMap[exp.categoryId] ?: Category(id = exp.categoryId, name = "Unknown")
                    )
                }

                HistoryUiState(
                    expenses = filtered,
                    categories = base.categories,
                    currencySymbol = base.currency,
                    searchQuery = filter.query,
                    selectedSource = filter.source,
                    selectedCategoryId = filter.catId,
                    selectedMonth = filter.month,
                    selectedYear = filter.year,
                    onlySplits = filter.onlySplits,
                    totalPendingReceivable = base.totalPending,
                    isLoading = false
                )
            }.flowOn(kotlinx.coroutines.Dispatchers.Default)
             .collect { newState ->
                 _uiState.value = newState
             }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        // Immediate local reflection for text field responsiveness
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSourceFilterChange(source: PaymentSource?) {
        _selectedSource.value = source
        _uiState.update { it.copy(selectedSource = source) }
    }

    fun onCategoryFilterChange(categoryId: String?) {
        _selectedCategoryId.value = categoryId
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun onMonthChange(month: Int, year: Int) {
        _selectedMonth.value = month
        _selectedYear.value = year
        _uiState.update { it.copy(selectedMonth = month, selectedYear = year) }
    }

    fun toggleSplitFilter() {
        val newVal = !_onlySplits.value
        _onlySplits.value = newVal
        _uiState.update { it.copy(onlySplits = newVal) }
    }

    fun markSplitReimbursed(expenseId: String, isReimbursed: Boolean) {
        viewModelScope.launch {
            expenseRepository.markSplitReimbursed(expenseId, isReimbursed)
        }
    }
}
