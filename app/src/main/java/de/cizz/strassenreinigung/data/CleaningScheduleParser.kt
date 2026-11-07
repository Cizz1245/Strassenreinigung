package de.cizz.strassenreinigung.data

import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

/**
 * Liest die Termine aus dem *Text* der Seite https://www.goerlitz.de/Strassenreinigung.html.
 *
 * Aufbau der Seite (Stand November 2026):
 *
 *     Reinigungstermine November 2026          <- Überschrift pro Monat
 *     Straßenname Termin                       <- Spaltenkopf
 *     Am Museum 10.11.2026                     <- eine Zeile pro Straße/Abschnitt
 *     Bahnhofstraße (rechts von … bis …) 24.11.2026
 *     Jauernicker Straße (links von …)         <- selten: Datum steht in der
 *     16.11.2026                                  nächsten Zeile
 *     …
 *     Wöchentliche Reinigung in den Reinigungsklassen 1 und 5   <- ab hier keine Monatstermine mehr
 *
 * Diese Klasse kennt kein Android und kein HTML – sie bekommt reinen Text
 * (siehe [ScheduleRepository.htmlToText]) und lässt sich deshalb einfach testen.
 */
object CleaningScheduleParser {

    private val MONTHS = mapOf(
        "januar" to 1, "februar" to 2, "märz" to 3, "maerz" to 3, "april" to 4,
        "mai" to 5, "juni" to 6, "juli" to 7, "august" to 8, "september" to 9,
        "oktober" to 10, "november" to 11, "dezember" to 12,
    )

    private val MONTH_HEADING = Regex(
        """Reinigungstermine\s+(\p{L}+)\s+(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    /** Ende des Monatsteils: danach folgen die wöchentlichen Reinigungsklassen. */
    private val END_HEADING = Regex("""Wöchentliche\s+Reinigung""", RegexOption.IGNORE_CASE)

    /**
     * Text (auch leer) gefolgt von einem Datum TT.MM.JJJJ.
     * "(?<!\d)" verhindert, dass z. B. aus "117.11.2026" ein falscher Tag gelesen wird.
     * Mehrere Treffer pro Zeile sind erlaubt – falls die Seite einmal keine
     * Zeilenumbrüche liefern sollte.
     */
    private val ENTRY = Regex("""(.*?)(?<!\d)(\d{1,2})\.(\d{1,2})\.(\d{4})""")

    fun parse(text: String): ParseResult {
        val dates = mutableListOf<CleaningDate>()
        val months = mutableListOf<YearMonth>()

        var inMonthSection = false
        var pendingLabel: String? = null   // Straße ohne Datum aus der vorigen Zeile

        val lines = text.lines()
            .map { it.replace(' ', ' ').replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotEmpty() }

        for (rawLine in lines) {
            var line = rawLine
            val heading = MONTH_HEADING.find(line)
            if (heading != null) {
                val month = MONTHS[heading.groupValues[1].lowercase()]
                val year = heading.groupValues[2].toInt()
                if (month != null) {
                    val ym = YearMonth.of(year, month)
                    if (ym !in months) months += ym
                    inMonthSection = true
                }
                pendingLabel = null
                // Normalerweise steht nach der Überschrift nichts mehr in der Zeile.
                // Falls doch (Seite ohne Zeilenumbrüche), den Rest weiter auswerten.
                line = line.substring(heading.range.last + 1).trim()
                if (line.isEmpty()) continue
            }
            if (END_HEADING.containsMatchIn(line)) {
                inMonthSection = false
                pendingLabel = null
                continue
            }
            if (!inMonthSection) continue
            if (line.startsWith("Straßenname", ignoreCase = true)) {
                pendingLabel = null
                line = line.removePrefix("Straßenname").trim()
                    .removePrefix("Termin").trim()
                if (line.isEmpty()) continue
            }

            val matches = ENTRY.findAll(line).toList()
            if (matches.isEmpty()) {
                // Kein Datum in der Zeile: vermutlich steht es in der nächsten Zeile.
                pendingLabel = line
                continue
            }
            for (m in matches) {
                var label = cleanLabel(m.groupValues[1])
                if (label.isEmpty()) label = pendingLabel?.let(::cleanLabel).orEmpty()
                pendingLabel = null
                if (label.isEmpty()) continue
                val date = try {
                    LocalDate.of(
                        m.groupValues[4].toInt(),
                        m.groupValues[3].toInt(),
                        m.groupValues[2].toInt(),
                    )
                } catch (e: DateTimeException) {
                    continue   // z. B. 31.11. – ungültig, überspringen
                }
                dates += CleaningDate(label, date)
            }
        }
        return ParseResult(dates.distinct(), months)
    }

    private fun cleanLabel(raw: String): String =
        raw.trim().trimEnd(':', '-', '–', ',').trim()
}
