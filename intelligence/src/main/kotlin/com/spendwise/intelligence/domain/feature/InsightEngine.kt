package com.spendwise.intelligence.domain.feature

enum class InsightType {
    SPENDING_ALERT,
    SAVINGS_OPPORTUNITY,
    BUDGET_PACING,
    MILESTONE,
    TREND
}

enum class InsightPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}

data class ActionProposal(
    val label: String,
    val actionType: String,
    val payload: Map<String, String> = emptyMap()
)

data class FinancialInsight(
    val id: String,
    val title: String,
    val message: String,
    val type: InsightType,
    val priority: InsightPriority,
    val timestampEpoch: Long = System.currentTimeMillis(),
    val actionProposal: ActionProposal? = null
)

interface InsightEngine {
    /**
     * Generates proactive, actionable financial tips and alerts based on current pacing and historical patterns.
     */
    suspend fun generateInsights(): List<FinancialInsight>
}
