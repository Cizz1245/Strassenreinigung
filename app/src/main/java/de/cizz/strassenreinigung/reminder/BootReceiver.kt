package de.cizz.strassenreinigung.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.cizz.strassenreinigung.work.CheckWorker

/**
 * Android löscht alle geplanten Alarme beim Ausschalten des Handys.
 * Dieser Empfänger plant sie nach dem Neustart (oder App-Update, Zeitänderung) neu.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifications.createChannels(context)
        ReminderScheduler.rescheduleAll(context)
        CheckWorker.enqueuePeriodic(context)
    }
}
