package de.cizz.strassenreinigung.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Wird vom AlarmManager zur eingestellten Uhrzeit geweckt und zeigt die Erinnerung an. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL) ?: return
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val dateText = date.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.", Locale.GERMANY))

        val (title, text) = if (kind == ReminderScheduler.Kind.DAY_BEFORE.name) {
            "Morgen Straßenreinigung!" to
                "$label – $dateText. Bitte das Auto bis morgen früh wegfahren, sonst droht Abschleppen."
        } else {
            "Heute Straßenreinigung – Auto wegfahren!" to
                "$label – heute ($dateText) wird gekehrt. Jetzt umparken, sonst wird abgeschleppt."
        }
        val id = (label + date + kind).hashCode()
        Notifications.showReminder(context, id, title, text)
    }

    companion object {
        const val EXTRA_LABEL = "label"
        const val EXTRA_DATE = "date"
        const val EXTRA_KIND = "kind"
    }
}
