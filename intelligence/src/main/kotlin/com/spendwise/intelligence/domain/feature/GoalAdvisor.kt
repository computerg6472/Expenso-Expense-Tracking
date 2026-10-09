package com.spendwise.intelligence.domain.feature

data class FinancialGoal(
    val id: String,
    val title: String,
    val targetAmount: Double,
    val targetDateEpoch: Long,
    val currentSavedAmount: Double = 0.0
)

data class GoalPlan(
    val goal: FinancialGoal,
    val monthlySavingsNeeded: Double,
    val feasibilityScore: Float, // 0.0 to 1.0
    val isFeasible: Boolean,
    val recommendedAdjustments: List<String>
)

interface GoalAdvisor {
    /**
     * Evaluates goal feasibility against user income and discretionary spend velocity,
     * offering cut suggestions to reach the target on time.
     */
    suspend fun evaluateGoal(
        goal: FinancialGoal,
        averageMonthlyDiscretionarySpend: Double,
        averageMonthlySavings: Double
    ): GoalPlan
}
