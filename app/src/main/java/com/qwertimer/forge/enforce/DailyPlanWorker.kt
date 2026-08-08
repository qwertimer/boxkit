package com.qwertimer.forge.enforce

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qwertimer.forge.data.repo.TrainingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Daily safety net.
 *
 * Alarms get dropped — force-stops, aggressive OEM battery managers, a restore onto a new phone.
 * This job runs once a day, makes sure today's plan exists, and re-arms the alarm chain if it went
 * missing. It is not the primary trigger; it is the thing that notices the primary trigger died.
 */
@HiltWorker
class DailyPlanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val training: TrainingRepository,
    private val scheduler: EnforcementScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        training.ensurePlanFor(LocalDate.now())
        scheduler.scheduleNextReminder()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        private const val NAME = "daily_plan"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyPlanWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
