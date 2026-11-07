package de.cizz.strassenreinigung.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Prüft den Parser mit einem Auszug der echten Seite (November/Dezember 2026).
 * In Android Studio: Rechtsklick auf die Datei → "Run 'CleaningScheduleParserTest'".
 */
class CleaningScheduleParserTest {

    private val sample = """
        Straßenreinigung
        Reinigungstermine November 2026
        Straßenname Termin
        Am Brautwiesentunnel 20.11.2026
        Am Hirschwinkel (Fahrbahn K 6334) 25.11.2026
        Bahnhofstraße (rechts von Schillerstraße bis Brautwiesenplatz) 24.11.2026
        Grüner Graben (rechts von Pontestraße bis Platz des 17.Juni) 02.11.2026
        Jauernicker Straße (links von Sattigstraße bis Reichertstraße)
        16.11.2026
        Platz des 17. Juni 30.11.2026
        Etkar-Andre'-Straße 19.11.2026
        Reinigungstermine Dezember 2026
        Straßenname Termin
        Am Feierabendheim 24.12.2026
        Zittauer Straße (zwischen Sattigstraße und Paul-Mühsam-Straße) 21.12.2026
        Wöchentliche Reinigung in den Reinigungsklassen 1 und 5
        Montag
        RK 1 Marienplatz
        Parkmöglichkeiten bei längerer Abwesenheit (z. B. Urlaub) können unter 03581 671834 abgefragt werden
        Anträge bis 31.01.2027
    """.trimIndent()

    @Test
    fun readsMonthsAndDates() {
        val result = CleaningScheduleParser.parse(sample)

        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12)), result.months)
        assertEquals(9, result.dates.size)

        val bahnhof = result.dates.first { it.street == "Bahnhofstraße" }
        assertEquals(LocalDate.of(2026, 11, 24), bahnhof.date)
        assertEquals("rechts von Schillerstraße bis Brautwiesenplatz", bahnhof.section)
    }

    @Test
    fun joinsDateOnNextLine() {
        val result = CleaningScheduleParser.parse(sample)
        val j = result.dates.first { it.street == "Jauernicker Straße" }
        assertEquals("Jauernicker Straße (links von Sattigstraße bis Reichertstraße)", j.label)
        assertEquals(LocalDate.of(2026, 11, 16), j.date)
    }

    @Test
    fun streetNamesWithNumbersStayIntact() {
        val result = CleaningScheduleParser.parse(sample)
        assertTrue(result.dates.any { it.label == "Platz des 17. Juni" && it.date == LocalDate.of(2026, 11, 30) })
        assertTrue(result.dates.any { it.label.startsWith("Grüner Graben") && it.date == LocalDate.of(2026, 11, 2) })
    }

    @Test
    fun ignoresWeeklySectionAndOtherDates() {
        val result = CleaningScheduleParser.parse(sample)
        assertTrue(result.dates.none { it.date.year == 2027 })
        assertTrue(result.dates.none { it.label.startsWith("RK") })
    }

    @Test
    fun worksWithoutLineBreaks() {
        val oneLine = "Reinigungstermine November 2026 Straßenname Termin Am Museum 10.11.2026 Bäckerstraße 10.11.2026"
        val result = CleaningScheduleParser.parse(oneLine)
        assertEquals(listOf("Am Museum", "Bäckerstraße"), result.dates.map { it.label })
    }
}
