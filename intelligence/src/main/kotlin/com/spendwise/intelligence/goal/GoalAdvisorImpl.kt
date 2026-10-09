package com.spendwise.intelligence.goal

import com.spendwise.intelligence.capability.AiFeature
import com.spendwise.intelligence.capability.CapabilityProvider
import com.spendwise.intelligence.domain.feature.FinancialGoal
import com.spendwise.intelligence.domain.feature.GoalAdvisor
import com.spendwise.intelligence.domain.feature.GoalPlan
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalAdvisorImpl @Inject constructor(
    private val capabilityProvider: CapabilityProvider
) : GoalAdvisor {

    override suspend fun evaluateGoal(
        goal: FinancialGoal,
        averageMonthlyDiscretionarySpend: Double,
        averageMonthlySavings: Double
    ): GoalPlan {
        val remainingAmount = (goal.targetAmount - goal.currentSavedAmount).coerceAtLeast(0.0)
        val nowMillis = System.currentTimeMillis()
        val diffDays = TimeUnit.MILLISECONDS.toDays(goal.targetDateEpoch - nowMillis).coerceAtLeast(1)
        val monthsRemaining = (diffDays / 30.0).coerceAtLeast(1.0)

        val requiredMonthlySavings = remainingAmount / monthsRemaining

        // Feasibility check
        val availableSavingsCapacity = averageMonthlySavings + averageMonthlyDiscretionarySpend * 0.40
        val feasibilityScore = if (requiredMonthlySavings <= 0.0) {
            1.0f
        } else {
            (availableSavingsCapacity / requiredMonthlySavings).coerceIn(0.0, 1.0).toFloat()
        }

        val isFeasible = feasibilityScore >= 0.70f
        val recommendations = mutableListOf<String>()

        if (requiredMonthlySavings <= averageMonthlySavings) {
            recommendations.add("You're on track! Your current savings rate is sufficient to reach this goal on time.")
        } else {
            val deficit = requiredMonthlySavings - averageMonthlySavings
            recommendations.add("You need an additional ₹${deficit.toInt()}/month to meet this target.")
            if (averageMonthlyDiscretionarySpend > 0) {
                val cutPercent = ((deficit / averageMonthlyDiscretionarySpend) * 100).toInt().coerceIn(5, 50)
                recommendations.add("Reducing discretionary spending (dining, shopping, cabs) by $cutPercent% covers this shortfall.")
            }
            recommendations.add("Consider setting automatic recurring transfers at the start of each month.")
        }

        return GoalPlan(
            goal = goal,
            monthlySavingsNeeded = requiredMonthlySavings,
            feasibilityScore = feasibilityScore,
            isFeasible = isFeasible,
            recommendedAdjustments = recommendations
        )
    }
}
