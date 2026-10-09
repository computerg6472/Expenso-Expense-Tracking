package com.spendwise.intelligence.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendwise.intelligence.domain.feature.InsightEngine
import com.spendwise.intelligence.domain.feature.InsightPriority
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class FinancialDigestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val insightEngine: InsightEngine
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val insights = insightEngine.generateInsights()
            val urgentInsights = insights.filter { it.priority == InsightPriority.URGENT || it.priority == InsightPriority.HIGH }

            // High priority insights can be exposed or notified locally
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
