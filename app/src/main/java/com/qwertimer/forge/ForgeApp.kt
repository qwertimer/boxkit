package com.qwertimer.forge

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.qwertimer.forge.enforce.DailyPlanWorker
import com.qwertimer.forge.enforce.EnforcementScheduler
import com.qwertimer.forge.enforce.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ForgeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var scheduler: EnforcementScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        DailyPlanWorker.enqueue(this)
        // Re-arm on every cold start. Arming is idempotent — a single request code means the
        // pending alarm is replaced, never duplicated.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            scheduler.scheduleNextReminder()
        }
    }
}
