package com.spendwise.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.spendwise.app.domain.repository.CategoryRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SpendWiseApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var categoryRepository: CategoryRepository

    @Inject
    lateinit var database: com.spendwise.app.data.local.SpendWiseDatabase

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }

        // Enforce automatic WAL checkpoint using ProcessLifecycleOwner whenever app enters background
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                try {
                    database.checkpointWal(truncate = true)
                    android.util.Log.d("SpendWiseApp", "ProcessLifecycleOwner onStop -> PRAGMA wal_checkpoint(TRUNCATE) executed")
                } catch (e: Exception) {
                    android.util.Log.w("SpendWiseApp", "Background wal_checkpoint error: ${e.message}")
                }
            }

            override fun onPause(owner: androidx.lifecycle.LifecycleOwner) {
                try {
                    database.checkpointWal(truncate = true)
                } catch (e: Exception) {
                    android.util.Log.w("SpendWiseApp", "Pause wal_checkpoint error: ${e.message}")
                }
            }
        })

        // Defer non-critical startup work off the Main Thread for instant cold start
        CoroutineScope(Dispatchers.Default).launch {
            try {
                com.spendwise.app.notification.NotificationHelper.createChannels(this@SpendWiseApplication)
                com.spendwise.app.worker.WorkManagerScheduler.scheduleMonthRollover(this@SpendWiseApplication)
                com.spendwise.app.worker.WorkManagerScheduler.scheduleDailyReminder(this@SpendWiseApplication)
                categoryRepository.insertDefaults()
                categoryRepository.ensureOthersCategory()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
