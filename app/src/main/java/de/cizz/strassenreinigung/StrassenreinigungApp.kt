package de.cizz.strassenreinigung

import android.app.Application
import de.cizz.strassenreinigung.reminder.Notifications
import de.cizz.strassenreinigung.work.CheckWorker

/** Startet einmal pro App-Prozess: Kanäle anlegen, Hintergrundprüfung sicherstellen. */
class StrassenreinigungApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        CheckWorker.enqueuePeriodic(this)
    }
}
