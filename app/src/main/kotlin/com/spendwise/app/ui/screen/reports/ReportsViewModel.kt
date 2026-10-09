package com.spendwise.app.ui.screen.reports

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.local.dao.MonthlyTotal
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.model.CategorySpend
import com.spendwise.app.domain.repository.ExpenseRepository
import com.spendwise.app.domain.usecase.ExportDataUseCase
import com.spendwise.app.domain.usecase.GetCategorySpendUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.spendwise.app.data.local.entity.Expense
import java.util.Calendar
import javax.inject.Inject

data class ReportsUiState(
    val isLoading: Boolean = true,
    val categorySpends: List<CategorySpend> = emptyList(),
    val monthlyIncomeSpendTotals: List<MonthlyTotal> = emptyList(),
    val monthlyCreditSpendTotals: List<MonthlyTotal> = emptyList(),
    val currencySymbol: String = "\u20B9",
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val selectedCategory: CategorySpend? = null,
    val categoryExpenses: List<Expense> = emptyList(),
    val dailyVelocity: Double = 0.0,
    val projectedMonthEnd: Double = 0.0,
    val daysInMonth: Int = 30,
    val currentDay: Int = 1,
    val isExporting: Boolean = false,
    val exportMessage: String? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val getCategorySpendUseCase: GetCategorySpendUseCase,
    private val expenseRepository: ExpenseRepository,
    private val exportDataUseCase: ExportDataUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val year = _uiState.value.selectedYear
            val month = _uiState.value.selectedMonth

            combine(
                getCategorySpendUseCase(year, month),
                expenseRepository.getMonthlyIncomeTotals(),
                expenseRepository.getMonthlyCreditTotals(),
                appPreferences.currencySymbol
            ) { catSpends, incomeTotals, creditTotals, currency ->
                val cal = Calendar.getInstance()
                val isCurrent = year == cal.get(Calendar.YEAR) && month == (cal.get(Calendar.MONTH) + 1)
                val cDay = if (isCurrent) cal.get(Calendar.DAY_OF_MONTH) else cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val totalSpent = catSpends.sumOf { it.totalSpend }
                val velocity = if (cDay > 0) totalSpent / cDay else 0.0
                val projected = velocity * maxDays

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        categorySpends = catSpends,
                        monthlyIncomeSpendTotals = incomeTotals,
                        monthlyCreditSpendTotals = creditTotals,
                        currencySymbol = currency,
                        dailyVelocity = velocity,
                        projectedMonthEnd = projected,
                        daysInMonth = maxDays,
                        currentDay = cDay
                    )
                }
            }.flowOn(kotlinx.coroutines.Dispatchers.Default)
             .collect()
        }
    }

    fun selectCategory(category: CategorySpend?) {
        _uiState.update { it.copy(selectedCategory = category) }
        if (category != null) {
            val year = _uiState.value.selectedYear
            val month = _uiState.value.selectedMonth
            viewModelScope.launch {
                expenseRepository.observeByYearMonthAndCategory(year, month, category.categoryId)
                    .collect { expenses ->
                        _uiState.update { it.copy(categoryExpenses = expenses) }
                    }
            }
        } else {
            _uiState.update { it.copy(categoryExpenses = emptyList()) }
        }
    }

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val uri = exportDataUseCase.exportCsv(
                    context,
                    _uiState.value.selectedYear,
                    _uiState.value.selectedMonth
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share CSV Export"))
                _uiState.update { it.copy(isExporting = false, exportMessage = "CSV exported successfully") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, exportMessage = "Export failed: ${e.message}") }
            }
        }
    }
}
