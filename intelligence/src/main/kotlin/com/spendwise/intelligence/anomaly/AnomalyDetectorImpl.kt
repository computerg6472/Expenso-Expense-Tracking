package com.spendwise.intelligence.anomaly

import com.spendwise.intelligence.capability.AiFeature
import com.spendwise.intelligence.capability.CapabilityProvider
import com.spendwise.intelligence.domain.feature.AnomalyDetector
import com.spendwise.intelligence.domain.feature.AnomalySeverity
import com.spendwise.intelligence.domain.feature.ExpenseAnomaly
import com.spendwise.intelligence.domain.model.ExpenseRecord
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

@Singleton
class AnomalyDetectorImpl @Inject constructor(
    private val capabilityProvider: CapabilityProvider
) : AnomalyDetector {

    override suspend fun evaluateExpense(
        expense: ExpenseRecord,
        history: List<ExpenseRecord>
    ): ExpenseAnomaly? {
        val sameCategory = history.filter { it.categoryId == expense.categoryId && it.id != expense.id }
        if (sameCategory.size < 3) {
            // Not enough historical transactions in this category to reliably detect statistical outlier
            return null
        }

        val amounts = sameCategory.map { it.amount }
        val mean = amounts.average()
        val variance = amounts.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance).coerceAtLeast(1.0)

        val deviation = (expense.amount - mean) / stdDev

        // Trigger anomaly if amount is higher than mean + 2.0 stdDev and at least 1.5x of mean
        if (deviation >= 2.0 && expense.amount > mean * 1.5) {
            val severity = when {
                deviation >= 4.0 -> AnomalySeverity.HIGH
                deviation >= 3.0 -> AnomalySeverity.MEDIUM
                else -> AnomalySeverity.LOW
            }

            val lowerBound = (mean - stdDev).coerceAtLeast(0.0)
            val upperBound = mean + 2.0 * stdDev

            val reason = when (severity) {
                AnomalySeverity.HIGH -> "Unusually high expense (${String.format("%.1f", deviation)}σ above average of ₹${mean.toInt()})"
                AnomalySeverity.MEDIUM -> "Significantly above your typical category spend (avg: ₹${mean.toInt()})"
                AnomalySeverity.LOW -> "Slightly higher than normal category spend"
            }

            return ExpenseAnomaly(
                expense = expense,
                reason = reason,
                severity = severity,
                expectedRange = lowerBound..upperBound,
                deviationScore = deviation.toFloat()
            )
        }

        return null
    }
}
