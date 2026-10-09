package com.spendwise.intelligence.forecast

import com.spendwise.intelligence.capability.AiFeature
import com.spendwise.intelligence.capability.CapabilityProvider
import com.spendwise.intelligence.domain.feature.DailyForecast
import com.spendwise.intelligence.domain.feature.ForecastConfidence
import com.spendwise.intelligence.domain.feature.ForecastEngine
import com.spendwise.intelligence.domain.feature.MonthForecast
import com.spendwise.intelligence.domain.model.ExpenseRecord
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class ForecastEngineImpl @Inject constructor(
    private val capabilityProvider: CapabilityProvider
) : ForecastEngine {

    override suspend fun forecastMonthEnd(
        year: Int,
        month: Int,
        currentSpend: Double,
        salary: Double,
        history: List<ExpenseRecord>
    ): MonthForecast {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        val totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val todayCal = Calendar.getInstance()
        val currentDay = if (todayCal.get(Calendar.YEAR) == year && (todayCal.get(Calendar.MONTH) + 1) == month) {
            todayCal.get(Calendar.DAY_OF_MONTH).coerceIn(1, totalDaysInMonth)
        } else {
            totalDaysInMonth
        }

        val daysRemaining = (totalDaysInMonth - currentDay).coerceAtLeast(0)

        // Calculate daily burn rate in current month
        val currentDailyRate = if (currentDay > 0) currentSpend / currentDay else 0.0

        // Calculate historical daily burn rate from past records
        val pastExpenses = history.filter {
            val recordCal = Calendar.getInstance().apply { timeInMillis = it.dateEpochMillis }
            val recordYear = recordCal.get(Calendar.YEAR)
            val recordMonth = recordCal.get(Calendar.MONTH) + 1
            recordYear != year || recordMonth != month
        }

        val historicalDailyRate = if (pastExpenses.isNotEmpty()) {
            val totalPastSpend = pastExpenses.sumOf { it.amount }
            val distinctDays = pastExpenses.map {
                val c = Calendar.getInstance().apply { timeInMillis = it.dateEpochMillis }
                "${c.get(Calendar.YEAR)}_${c.get(Calendar.DAY_OF_YEAR)}"
            }.distinct().size.coerceAtLeast(1)
            totalPastSpend / distinctDays
        } else {
            currentDailyRate
        }

        // Weighted blended velocity: 70% current month velocity, 30% historical baseline
        val blendedDailyRate = if (currentDay >= 7) {
            currentDailyRate * 0.75 + historicalDailyRate * 0.25
        } else {
            currentDailyRate * 0.40 + historicalDailyRate * 0.60
        }

        // Expected future spending
        val projectedRemainingSpend = blendedDailyRate * daysRemaining
        val p50 = currentSpend + projectedRemainingSpend

        // Standard deviation / uncertainty bounds based on days remaining
        val dailyStdDev = blendedDailyRate * 0.25
        val uncertainty = dailyStdDev * sqrt(daysRemaining.toDouble().coerceAtLeast(1.0))

        val p10 = (p50 - 1.28 * uncertainty).coerceAtLeast(currentSpend)
        val p90 = p50 + 1.28 * uncertainty

        val projectedBalance = salary - p50
        val isDeficit = p50 > salary

        // Build day-by-day projection series
        val dailyProjections = mutableListOf<DailyForecast>()
        var runningSpend = currentSpend

        for (day in currentDay..totalDaysInMonth) {
            if (day > currentDay) {
                runningSpend += blendedDailyRate
            }
            val dayUncertainty = dailyStdDev * sqrt((day - currentDay).toDouble().coerceAtLeast(0.0))
            dailyProjections.add(
                DailyForecast(
                    dayOfMonth = day,
                    projectedCumulativeSpend = runningSpend,
                    lowerBound = (runningSpend - dayUncertainty).coerceAtLeast(currentSpend),
                    upperBound = runningSpend + dayUncertainty
                )
            )
        }

        return MonthForecast(
            year = year,
            month = month,
            projectedTotalSpend = p50,
            projectedAvailableBalance = projectedBalance,
            confidence = ForecastConfidence(p10 = p10, p50 = p50, p90 = p90),
            dailyProjections = dailyProjections,
            isDeficitProjected = isDeficit
        )
    }
}
