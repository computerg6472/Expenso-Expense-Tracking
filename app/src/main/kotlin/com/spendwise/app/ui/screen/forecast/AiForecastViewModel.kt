package com.spendwise.app.ui.screen.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.model.CategorySpend
import com.spendwise.app.domain.model.DashboardData
import com.spendwise.app.domain.usecase.CalculateBalanceUseCase
import com.spendwise.app.domain.usecase.GetCategorySpendUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class CategoryVelocityRisk(
    val categoryName: String,
    val iconName: String,
    val colorHex: String,
    val totalSpend: Double,
    val dailyBurn: Double,
    val riskLevel: String // "High Burn", "Moderate", "Optimal"
)

data class AiForecastUiState(
    val isLoading: Boolean = true,
    val dashboard: DashboardData? = null,
    val categorySpends: List<CategorySpend> = emptyList(),
    val currencySymbol: String = "₹",
    val healthScore: Int = 85,
    val healthStatus: String = "Optimal Stability",
    val dailyBurnRate: Double = 0.0,
    val projectedMonthEndSpend: Double = 0.0,
    val remainingRunwayDays: Int = 0,
    val daysInMonth: Int = 30,
    val currentDay: Int = 1,
    val projectedSurplus: Double = 0.0,
    val safeDailyCeiling: Double = 0.0,
    val aiInsightNudge: String = "",
    val categoryVelocities: List<CategoryVelocityRisk> = emptyList(),
    val whatIfDailyReduction: Double = 200.0,
    val whatIfProjectedSavings: Double = 0.0
)

@HiltViewModel
class AiForecastViewModel @Inject constructor(
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val getCategorySpendUseCase: GetCategorySpendUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val cal = Calendar.getInstance()
    private val year = cal.get(Calendar.YEAR)
    private val month = cal.get(Calendar.MONTH) + 1

    private val _uiState = MutableStateFlow(AiForecastUiState())
    val uiState: StateFlow<AiForecastUiState> = _uiState.asStateFlow()

    init {
        loadForecastData()
    }

    private fun loadForecastData() {
        viewModelScope.launch {
            combine(
                calculateBalanceUseCase(year, month),
                getCategorySpendUseCase(year, month),
                appPreferences.currencySymbol
            ) { dashboard, categorySpends, currency ->
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val remainingDays = (daysInMonth - dayOfMonth).coerceAtLeast(0)

                val totalSpent = dashboard.spentViaIncome + dashboard.creditCardSpendThisMonth
                val burnRate = if (dayOfMonth > 0) totalSpent / dayOfMonth else 0.0
                val projectedEnd = burnRate * daysInMonth
                val surplus = dashboard.salary - projectedEnd

                val safeCeiling = if (remainingDays > 0) {
                    (dashboard.availableBalance / remainingDays).coerceAtLeast(0.0)
                } else 0.0

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
                    else -> "High Burn"
                }

                val aiNudge = when {
                    dashboard.salary == 0.0 -> "Configure your monthly salary in Settings to enable precision cashflow pacing."
                    surplus > 10000 -> "Pacing is optimal! You are on track to save over $currency${surplus.toInt().coerceAtLeast(0)} this month. Keep up this daily velocity!"
                    surplus >= 0 -> "Velocity is balanced with your runway. Safe daily ceiling: $currency${safeCeiling.toInt()}/day to preserve positive cashflow."
                    else -> "Burn rate exceeds salary runway by $currency${(-surplus).toInt()}. Trim discretionary spends by $currency${((-surplus) / remainingDays.coerceAtLeast(1)).toInt()}/day for the next $remainingDays days."
                }

                val catVelocities = categorySpends.map { cat ->
                    val catDaily = if (dayOfMonth > 0) cat.totalSpend / dayOfMonth else 0.0
                    val risk = when {
                        cat.percentage >= 35.0 -> "High Burn"
                        cat.percentage >= 18.0 -> "Moderate"
                        else -> "Optimal"
                    }
                    CategoryVelocityRisk(
                        categoryName = cat.categoryName,
                        iconName = cat.iconName,
                        colorHex = cat.colorHex,
                        totalSpend = cat.totalSpend,
                        dailyBurn = catDaily,
                        riskLevel = risk
                    )
                }.sortedByDescending { it.dailyBurn }

                val currentReduction = _uiState.value.whatIfDailyReduction
                val whatIfSavings = currentReduction * remainingDays

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        dashboard = dashboard,
                        categorySpends = categorySpends,
                        currencySymbol = currency,
                        healthScore = healthScore,
                        healthStatus = healthStatus,
                        dailyBurnRate = burnRate,
                        projectedMonthEndSpend = projectedEnd,
                        remainingRunwayDays = remainingDays,
                        daysInMonth = daysInMonth,
                        currentDay = dayOfMonth,
                        projectedSurplus = surplus,
                        safeDailyCeiling = safeCeiling,
                        aiInsightNudge = aiNudge,
                        categoryVelocities = catVelocities,
                        whatIfProjectedSavings = whatIfSavings
                    )
                }
            }.collect()
        }
    }

    fun updateWhatIfReduction(amount: Double) {
        _uiState.update { current ->
            val remainingDays = current.remainingRunwayDays
            current.copy(
                whatIfDailyReduction = amount,
                whatIfProjectedSavings = amount * remainingDays
            )
        }
    }
}
