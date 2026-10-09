package com.spendwise.app.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.model.CategorySpend
import com.spendwise.app.domain.model.DashboardData
import com.spendwise.app.domain.usecase.CalculateBalanceUseCase
import com.spendwise.app.domain.usecase.GetCategorySpendUseCase
import com.spendwise.app.domain.usecase.MonthRolloverUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

import com.spendwise.app.data.local.entity.Expense
import com.spendwise.app.domain.repository.CategoryRepository
import com.spendwise.app.domain.repository.ExpenseRepository
import com.spendwise.app.domain.repository.IncomeRepository

data class HabitChip(
    val id: String,
    val title: String,
    val amount: Double,
    val categoryName: String,
    val paymentSource: String, // "INCOME" or "CREDIT_CARD"
    val iconEmoji: String
)

data class FinancialHealthMetrics(
    val healthScore: Int = 88,
    val healthStatus: String = "Optimal Stability",
    val dailyBurnRate: Double = 0.0,
    val projectedMonthEndSpend: Double = 0.0,
    val remainingRunwayDays: Int = 0,
    val projectedSurplus: Double = 0.0,
    val aiInsightNudge: String = ""
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val dashboard: DashboardData? = null,
    val categorySpends: List<CategorySpend> = emptyList(),
    val currencySymbol: String = "\u20B9",
    val salaryNotSet: Boolean = false,
    val habitChips: List<HabitChip> = listOf(
        HabitChip("1", "Chai / Tea", 40.0, "Food", "INCOME", "☕"),
        HabitChip("2", "Lunch Snack", 150.0, "Food", "INCOME", "🥪"),
        HabitChip("3", "Fuel / Metro", 500.0, "Transport", "INCOME", "⛽"),
        HabitChip("4", "Grocery Mart", 650.0, "Shopping", "CREDIT_CARD", "🛒"),
        HabitChip("5", "Coffee Cafe", 180.0, "Food", "CREDIT_CARD", "🥤"),
        HabitChip("6", "Cab Ride", 220.0, "Transport", "INCOME", "🚕")
    ),
    val aiHealth: FinancialHealthMetrics = FinancialHealthMetrics(),
    val aiFloatingButtonEnabled: Boolean = true,
    val quickLogSuccessMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val getCategorySpendUseCase: GetCategorySpendUseCase,
    private val monthRolloverUseCase: MonthRolloverUseCase,
    private val appPreferences: AppPreferences,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val incomeRepository: IncomeRepository
) : ViewModel() {

    private val cal = Calendar.getInstance()
    private val year = cal.get(Calendar.YEAR)
    private val month = cal.get(Calendar.MONTH) + 1

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { monthRolloverUseCase() }
        loadDashboard()
    }

    fun setSalary(amount: Double) {
        viewModelScope.launch {
            incomeRepository.setSalary(year, month, amount)
            appPreferences.setFirstLaunchSalarySet(true)
        }
    }

    fun addExtraCredit(amount: Double) {
        if (amount <= 0) return
        viewModelScope.launch {
            incomeRepository.addExtraCredit(year, month, amount)
            _uiState.update {
                it.copy(quickLogSuccessMessage = "Added ${it.currencySymbol}${amount.toInt()} extra wallet credit!")
            }
        }
    }

    fun setExtraCredit(amount: Double) {
        viewModelScope.launch {
            incomeRepository.setExtraCredit(year, month, amount)
        }
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            combine(
                calculateBalanceUseCase(year, month),
                getCategorySpendUseCase(year, month),
                appPreferences.currencySymbol,
                appPreferences.aiFloatingButtonEnabled
            ) { dashboard, categorySpends, currency, aiFloatingEnabled ->
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val remainingDays = (daysInMonth - dayOfMonth).coerceAtLeast(0)

                val totalSpent = dashboard.spentViaIncome + dashboard.creditCardSpendThisMonth
                val burnRate = if (dayOfMonth > 0) totalSpent / dayOfMonth else 0.0
                val projectedEnd = burnRate * daysInMonth
                val surplus = dashboard.salary - projectedEnd

                val savingsRate = if (dashboard.salary > 0) ((dashboard.salary - projectedEnd) / dashboard.salary) else 0.0
                val healthScore = when {
                    dashboard.salary == 0.0 -> 75
                    savingsRate >= 0.30 -> 94
                    savingsRate >= 0.15 -> 86
                    savingsRate >= 0.05 -> 74
                    savingsRate >= 0.0 -> 62
                    else -> (45 + (savingsRate * 40)).coerceIn(15.0, 58.0).toInt()
                }

                val healthStatus = when {
                    healthScore >= 85 -> "Optimal Stability"
                    healthScore >= 70 -> "Healthy Pace"
                    healthScore >= 55 -> "Moderate Burn"
                    else -> "High Burn Alert"
                }

                val aiNudge = when {
                    dashboard.salary == 0.0 -> "Configure your monthly salary in Settings to enable precision cashflow pacing."
                    surplus > 10000 -> "Pacing is exceptional! You are trending towards a ₹${surplus.toInt().coerceAtLeast(0)} savings surplus."
                    surplus >= 0 -> "Velocity is balanced with your runway. Safe daily ceiling: ₹${(dashboard.availableBalance / remainingDays.coerceAtLeast(1)).toInt()}/day."
                    else -> "Burn rate exceeds salary runway by ₹${(-surplus).toInt()}. Trim discretionary spends for the next $remainingDays days."
                }

                val aiMetrics = FinancialHealthMetrics(
                    healthScore = healthScore,
                    healthStatus = healthStatus,
                    dailyBurnRate = burnRate,
                    projectedMonthEndSpend = projectedEnd,
                    remainingRunwayDays = remainingDays,
                    projectedSurplus = surplus,
                    aiInsightNudge = aiNudge
                )

                _uiState.value.copy(
                    isLoading = false,
                    dashboard = dashboard,
                    categorySpends = categorySpends,
                    currencySymbol = currency,
                    salaryNotSet = dashboard.salary == 0.0,
                    aiHealth = aiMetrics,
                    aiFloatingButtonEnabled = aiFloatingEnabled
                )
            }.catch { e ->
                emit(_uiState.value.copy(isLoading = false, error = e.message))
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun quickLogHabit(habit: HabitChip) {
        viewModelScope.launch {
            val categories = categoryRepository.getAll()
            val category = categories.find { it.name.contains(habit.categoryName, ignoreCase = true) }
                ?: categories.firstOrNull()
                ?: return@launch

            val now = System.currentTimeMillis()
            val c = Calendar.getInstance()
            val expense = Expense(
                amount = habit.amount,
                categoryId = category.id,
                paymentSource = habit.paymentSource,
                note = "${habit.iconEmoji} ${habit.title}",
                dateEpoch = now,
                month = c.get(Calendar.MONTH) + 1,
                year = c.get(Calendar.YEAR)
            )
            expenseRepository.addExpense(expense)
            _uiState.update {
                it.copy(quickLogSuccessMessage = "1-Tap Logged: ${habit.title} (${it.currencySymbol}${habit.amount.toInt()})")
            }
        }
    }

    fun clearQuickLogMessage() {
        _uiState.update { it.copy(quickLogSuccessMessage = null) }
    }
}
