package com.spendwise.intelligence.recurring

import com.spendwise.intelligence.capability.AiFeature
import com.spendwise.intelligence.capability.CapabilityProvider
import com.spendwise.intelligence.domain.feature.RecurringDetector
import com.spendwise.intelligence.domain.feature.RecurringExpense
import com.spendwise.intelligence.domain.feature.RecurringFrequency
import com.spendwise.intelligence.domain.model.ExpenseRecord
import com.spendwise.intelligence.domain.model.PaymentSource
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class RecurringDetectorImpl @Inject constructor(
    private val capabilityProvider: CapabilityProvider
) : RecurringDetector {

    override suspend fun detectRecurring(expenses: List<ExpenseRecord>): List<RecurringExpense> {
        if (expenses.size < 2) return emptyList()

        val results = mutableListOf<RecurringExpense>()

        // Group expenses by merchant (or note/category if merchant is blank)
        val grouped = expenses.groupBy { record ->
            val key = record.merchant?.trim()?.lowercase()
                ?: record.note.trim().lowercase().takeIf { it.isNotBlank() }
                ?: "category_${record.categoryId}"
            key
        }

        for ((_, group) in grouped) {
            // Need at least 2 occurrences to establish a recurring pattern
            if (group.size < 2) continue

            val sorted = group.sortedBy { it.dateEpochMillis }
            val intervalsInDays = mutableListOf<Long>()

            for (i in 0 until sorted.size - 1) {
                val diffMillis = sorted[i + 1].dateEpochMillis - sorted[i].dateEpochMillis
                val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)
                if (diffDays > 0) {
                    intervalsInDays.add(diffDays)
                }
            }

            if (intervalsInDays.isEmpty()) continue

            val avgInterval = intervalsInDays.average()
            val (frequency, intervalConfidence) = classifyFrequency(avgInterval, intervalsInDays)

            if (frequency != null && intervalConfidence > 0.5f) {
                val amounts = sorted.map { it.amount }
                val avgAmount = amounts.average()

                // Check amount consistency (variance shouldn't be too wild for subscriptions)
                val amountVariance = amounts.map { abs(it - avgAmount) }.average()
                val amountConsistency = (1.0 - (amountVariance / avgAmount.coerceAtLeast(1.0))).coerceIn(0.2, 1.0).toFloat()

                val overallConfidence = (intervalConfidence * 0.6f + amountConsistency * 0.4f).coerceIn(0f, 1f)
                val lastExpense = sorted.last()

                // Calculate next expected epoch
                val expectedDaysAhead = when (frequency) {
                    RecurringFrequency.WEEKLY -> 7L
                    RecurringFrequency.BI_WEEKLY -> 14L
                    RecurringFrequency.MONTHLY -> 30L
                    RecurringFrequency.QUARTERLY -> 90L
                    RecurringFrequency.YEARLY -> 365L
                }
                val nextExpectedDate = lastExpense.dateEpochMillis + TimeUnit.DAYS.toMillis(expectedDaysAhead)

                val displayName = lastExpense.merchant ?: lastExpense.note.ifBlank { "Recurring Subscription" }

                results.add(
                    RecurringExpense(
                        merchant = displayName,
                        categoryId = lastExpense.categoryId,
                        averageAmount = avgAmount,
                        frequency = frequency,
                        nextExpectedDateEpoch = nextExpectedDate,
                        confidence = overallConfidence,
                        source = lastExpense.source
                    )
                )
            }
        }

        return results.sortedByDescending { it.confidence }
    }

    private fun classifyFrequency(avgInterval: Double, intervals: List<Long>): Pair<RecurringFrequency?, Float> {
        return when (avgInterval) {
            in 5.0..9.0 -> {
                val matchRatio = intervals.count { it in 5..9 }.toFloat() / intervals.size
                Pair(RecurringFrequency.WEEKLY, matchRatio)
            }
            in 12.0..16.0 -> {
                val matchRatio = intervals.count { it in 12..16 }.toFloat() / intervals.size
                Pair(RecurringFrequency.BI_WEEKLY, matchRatio)
            }
            in 25.0..35.0 -> {
                val matchRatio = intervals.count { it in 25..35 }.toFloat() / intervals.size
                Pair(RecurringFrequency.MONTHLY, matchRatio)
            }
            in 80.0..100.0 -> {
                val matchRatio = intervals.count { it in 80..100 }.toFloat() / intervals.size
                Pair(RecurringFrequency.QUARTERLY, matchRatio)
            }
            in 340.0..380.0 -> {
                val matchRatio = intervals.count { it in 340..380 }.toFloat() / intervals.size
                Pair(RecurringFrequency.YEARLY, matchRatio)
            }
            else -> Pair(null, 0f)
        }
    }
}
