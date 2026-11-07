package de.cizz.strassenreinigung.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * Ein Reinigungstermin, so wie er auf goerlitz.de steht.
 *
 * @param label voller Text vor dem Datum, z. B.
 *              "Bahnhofstraße (rechts von Schillerstraße bis Brautwiesenplatz)".
 *              Er dient auch als Schlüssel, wenn der Nutzer "seine" Straße speichert,
 *              denn manche Straßen sind in Abschnitte mit verschiedenen Terminen geteilt.
 * @param date  Tag der Reinigung.
 */
data class CleaningDate(val label: String, val date: LocalDate) {

    /** Straßenname ohne Abschnittsangabe in Klammern ("Bahnhofstraße"). */
    val street: String get() = label.substringBefore("(").trim()

    /** Abschnitt in Klammern ("rechts von … bis …") oder null. */
    val section: String?
        get() = if ("(" in label) label.substringAfter("(").substringBeforeLast(")").trim() else null

    /** Eindeutiger Schlüssel eines Termins, z. B. für "schon bekannt?"-Vergleiche. */
    val key: String get() = "$label|$date"
}

/** Ergebnis des Auslesens der Webseite. */
data class ParseResult(
    val dates: List<CleaningDate>,
    /** Monate, für die eine Überschrift "Reinigungstermine <Monat> <Jahr>" gefunden wurde. */
    val months: List<YearMonth>,
)
