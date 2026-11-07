package de.cizz.strassenreinigung.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.cizz.strassenreinigung.data.ScheduleRepository
import de.cizz.strassenreinigung.data.Settings
import de.cizz.strassenreinigung.reminder.Notifications
import de.cizz.strassenreinigung.reminder.ReminderScheduler
import java.util.concurrent.TimeUnit

/**
 * Läuft etwa alle 6 Stunden im Hintergrund (sobald Internet da ist):
 * lädt die Webseite, plant die Erinnerungen neu und meldet neue Termine.
 */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        return try {
            val update = ScheduleRepository(settings).refresh()
            ReminderScheduler.rescheduleAll(applicationContext)
            UpdateMessage.build(update, settings)?.let { (title, text) ->
                Notifications.showInfo(applicationContext, title, text)
            }
            Result.success()
        } catch (e: Exception) {
            // Netzwerkfehler o. Ä.: später nochmal versuchen; alter Stand bleibt gültig
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "termine_pruefen"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<CheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
