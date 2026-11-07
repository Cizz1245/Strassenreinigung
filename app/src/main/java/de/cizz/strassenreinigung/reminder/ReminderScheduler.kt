package de.cizz.strassenreinigung.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.cizz.strassenreinigung.data.CleaningDate
import de.cizz.strassenreinigung.data.Settings
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Plant die Erinnerungen beim AlarmManager von Android ein.
 *
 * Grundprinzip: Es wird nie "nachgebessert", sondern immer alles gelöscht und aus den
 * aktuellen Daten neu geplant. Das ist einfach und verhindert doppelte oder veraltete Alarme.
 * Aufgerufen nach: Abruf der Webseite, Änderung der Einstellungen, Neustart des Handys.
 */
object ReminderScheduler {

    enum class Kind { DAY_BEFORE, SAME_DAY }

    data class Planned(val entry: CleaningDate, val kind: Kind, val at: LocalDateTime) {
        val requestCode: Int get() = (entry.key + "|" + kind.name).hashCode()
    }

    /** Welche Erinnerungen ab [now] anstehen – ohne etwas einzuplanen (auch für die Anzeige). */
    fun plannedReminders(settings: Settings, now: LocalDateTime = LocalDateTime.now()): List<Planned> {
        val selected = settings.selectedLabels
        val result = mutableListOf<Planned>()
        for (entry in settings.cachedDates.filter { it.label in selected }) {
            if (settings.dayBeforeEnabled) {
                val at = entry.date.minusDays(1).atTime(toTime(settings.dayBeforeMinutes))
                if (at.isAfter(now)) result += Planned(entry, Kind.DAY_BEFORE, at)
            }
            if (settings.sameDayEnabled) {
                val at = entry.date.atTime(toTime(settings.sameDayMinutes))
                if (at.isAfter(now)) result += Planned(entry, Kind.SAME_DAY, at)
            }
        }
        return result.sortedBy { it.at }
    }

    fun rescheduleAll(context: Context) {
        val settings = Settings(context)
        val am = context.getSystemService(AlarmManager::class.java)

        // 1. Alle bisher geplanten Alarme löschen
        for (code in settings.scheduledRequestCodes) {
            val pi = reminderIntent(context, code.toInt(), null)
            am.cancel(pi)
            pi.cancel()
        }

        // 2. Neu planen
        val codes = mutableSetOf<String>()
        for (p in plannedReminders(settings)) {
            val trigger = p.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val pi = reminderIntent(context, p.requestCode, p)
            if (canUseExactAlarms(am)) {
                // setAlarmClock = wie ein Wecker: wird auch im Energiesparmodus pünktlich ausgelöst
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(trigger, Notifications.openAppIntent(context)), pi
                )
            } else {
                // Fallback ohne Genauigkeits-Berechtigung: kann einige Minuten später kommen
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
            codes += p.requestCode.toString()
        }
        settings.scheduledRequestCodes = codes
    }

    fun canUseExactAlarms(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun reminderIntent(context: Context, requestCode: Int, p: Planned?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        if (p != null) {
            intent.putExtra(ReminderReceiver.EXTRA_LABEL, p.entry.label)
            intent.putExtra(ReminderReceiver.EXTRA_DATE, p.entry.date.toString())
            intent.putExtra(ReminderReceiver.EXTRA_KIND, p.kind.name)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun toTime(minutes: Int): LocalTime = LocalTime.of(minutes / 60, minutes % 60)
}
