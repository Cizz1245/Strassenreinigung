package de.cizz.strassenreinigung.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.time.YearMonth

/**
 * Holt die Termine von goerlitz.de und vergleicht sie mit dem, was die App schon kennt.
 */
class ScheduleRepository(private val settings: Settings) {

    /** Was sich beim letzten Abruf geändert hat – Grundlage für die "Neue Termine"-Meldung. */
    data class UpdateResult(
        val result: ParseResult,
        /** Monate, die vorher noch nicht auf der Seite standen. */
        val newMonths: List<YearMonth>,
        /** Neue Termine für die gewählten Straßen. */
        val newSelectedDates: List<CleaningDate>,
        /** Termine der gewählten Straßen, die vorher bekannt waren und jetzt fehlen/geändert sind. */
        val removedSelectedKeys: Set<String>,
        /** true beim allerersten Abruf – dann wird nichts als "neu" gemeldet. */
        val firstFetch: Boolean,
    )

    /**
     * Lädt die Seite, liest die Termine und speichert sie.
     * Wirft eine Exception bei Netzwerkfehlern oder wenn keine Termine gefunden wurden
     * (z. B. weil die Stadt die Seite umgebaut hat) – dann bleibt der alte Stand erhalten.
     */
    suspend fun refresh(): UpdateResult = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(URL)
            .userAgent("Strassenreinigung-Goerlitz-App/1.0 (Android)")
            .timeout(20_000)
            .get()
        val result = CleaningScheduleParser.parse(htmlToText(doc.body()))
        if (result.dates.isEmpty()) {
            throw IllegalStateException(
                "Auf der Webseite wurden keine Termine gefunden. " +
                    "Möglicherweise hat sich der Aufbau der Seite geändert."
            )
        }

        val firstFetch = settings.lastFetchMillis == 0L
        val oldMonths = settings.cachedMonths.toSet()
        val newMonths = result.months.filter { it !in oldMonths }

        val selected = settings.selectedLabels
        val today = java.time.LocalDate.now()
        val currentSelected = result.dates.filter { it.label in selected && !it.date.isBefore(today) }
        val known = settings.knownSelectedKeys
        val newSelected = currentSelected.filter { it.key !in known }
        val removed = known.filter { key ->
            if (key.substringBeforeLast('|') !in selected) return@filter false
            val date = runCatching { java.time.LocalDate.parse(key.substringAfterLast('|')) }.getOrNull()
            date != null && !date.isBefore(today) && currentSelected.none { it.key == key }
        }.toSet()

        settings.cachedDates = result.dates
        settings.cachedMonths = result.months
        settings.lastFetchMillis = System.currentTimeMillis()
        settings.knownSelectedKeys = currentSelected.map { it.key }.toSet()

        UpdateResult(result, newMonths, newSelected, removed, firstFetch)
    }

    /** Nach dem Hinzufügen einer Straße: ihre Termine gelten als "bekannt". */
    fun markSelectedAsKnown() {
        val selected = settings.selectedLabels
        settings.knownSelectedKeys = settings.cachedDates
            .filter { it.label in selected }
            .map { it.key }.toSet()
    }

    companion object {
        const val URL = "https://www.goerlitz.de/Strassenreinigung.html"

        /**
         * Wandelt HTML in Text mit Zeilenumbrüchen um: nach <br> und nach jedem
         * Block-Element (p, div, h2, li, tr …) beginnt eine neue Zeile.
         */
        fun htmlToText(root: Element): String {
            val sb = StringBuilder()
            NodeTraversor.traverse(object : NodeVisitor {
                override fun head(node: Node, depth: Int) {
                    when {
                        node is TextNode -> sb.append(node.text())
                        node is Element && node.normalName() == "br" -> sb.append('\n')
                        node is Element && node.isBlock -> sb.append('\n')
                    }
                }

                override fun tail(node: Node, depth: Int) {
                    if (node is Element && node.isBlock) sb.append('\n')
                }
            }, root)
            return sb.toString()
        }
    }
}
