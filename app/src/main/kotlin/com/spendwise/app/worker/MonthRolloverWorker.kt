package com.spendwise.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendwise.app.domain.usecase.MonthRolloverUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MonthRolloverWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val monthRolloverUseCase: MonthRolloverUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            monthRolloverUseCase()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
