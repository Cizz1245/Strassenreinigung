package de.cizz.strassenreinigung.work

import de.cizz.strassenreinigung.data.ScheduleRepository
import de.cizz.strassenreinigung.data.Settings
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Baut aus dem Ergebnis eines Abrufs die Meldung "Neue Termine sind da" –
 * oder null, wenn es nichts Neues gibt.
 */
object UpdateMessage {

    private val DAY = DateTimeFormatter.ofPattern("EE dd.MM.", Locale.GERMANY)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)

    fun build(update: ScheduleRepository.UpdateResult, settings: Settings): Pair<String, String>? {
        if (update.firstFetch) return null
        val hasStreets = settings.selectedLabels.isNotEmpty()

        if (update.newSelectedDates.isNotEmpty() || update.removedSelectedKeys.isNotEmpty()) {
            val lines = mutableListOf<String>()
            update.newSelectedDates.sortedBy { it.date }.forEach {
                lines += "• ${it.label}: ${it.date.format(DAY)}"
            }
            if (update.removedSelectedKeys.isNotEmpty()) {
                lines += "Achtung: Bisherige Termine (${update.removedSelectedKeys.size}) wurden " +
                    "geändert oder gestrichen."
            }
            lines += "Erinnerungen sind eingeplant – tippe hier, um die Uhrzeiten zu prüfen."
            return "Neue Reinigungstermine für deine Straße" to lines.joinToString("\n")
        }

        if (update.newMonths.isNotEmpty()) {
            val months = update.newMonths.joinToString(", ") { it.format(MONTH) }
            val text = if (hasStreets) {
                "Die Termine für $months sind online. Deine Straße ist dort nicht aufgeführt."
            } else {
                "Die Termine für $months sind online. Tippe hier und wähle deine Straße, " +
                    "damit du erinnert wirst."
            }
            return "Neue Reinigungstermine veröffentlicht" to text
        }
        return null
    }

    fun monthName(ym: YearMonth): String = ym.format(MONTH)
}
