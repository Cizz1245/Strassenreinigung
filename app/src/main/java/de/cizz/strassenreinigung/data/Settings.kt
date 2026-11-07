package de.cizz.strassenreinigung.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/**
 * Alles, was die App sich merkt – gespeichert in SharedPreferences
 * (eine kleine Schlüssel/Wert-Datei im privaten App-Speicher).
 */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("strassenreinigung", Context.MODE_PRIVATE)

    // ---------- Vom Nutzer gewählte Straßen ----------

    /** Gespeicherte Straßen/Abschnitte (= [CleaningDate.label]). */
    var selectedLabels: Set<String>
        get() = prefs.getStringSet(KEY_SELECTED, emptySet())!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_SELECTED, value).apply()

    // ---------- Erinnerungs-Einstellungen ----------

    var dayBeforeEnabled: Boolean
        get() = prefs.getBoolean("day_before_enabled", true)
        set(v) = prefs.edit().putBoolean("day_before_enabled", v).apply()

    /** Uhrzeit am Vortag in Minuten seit Mitternacht (Standard 18:00). */
    var dayBeforeMinutes: Int
        get() = prefs.getInt("day_before_minutes", 18 * 60)
        set(v) = prefs.edit().putInt("day_before_minutes", v).apply()

    var sameDayEnabled: Boolean
        get() = prefs.getBoolean("same_day_enabled", true)
        set(v) = prefs.edit().putBoolean("same_day_enabled", v).apply()

    /** Uhrzeit am Reinigungstag in Minuten seit Mitternacht (Standard 06:00). */
    var sameDayMinutes: Int
        get() = prefs.getInt("same_day_minutes", 6 * 60)
        set(v) = prefs.edit().putInt("same_day_minutes", v).apply()

    /**
     * Ton der Erinnerung:
     *  - null        → Standard-Benachrichtigungston des Handys
     *  - "silent"    → nur Benachrichtigung, kein Ton
     *  - sonst       → content://-Adresse des gewählten Tons
     */
    var sound: String?
        get() = prefs.getString("sound", null)
        set(v) = prefs.edit().putString("sound", v).apply()

    /** Ton über den Wecker-Kanal abspielen (klingelt auch, wenn das Handy auf lautlos steht). */
    var useAlarmVolume: Boolean
        get() = prefs.getBoolean("use_alarm_volume", false)
        set(v) = prefs.edit().putBoolean("use_alarm_volume", v).apply()

    /**
     * Android erlaubt nicht, den Ton eines bestehenden Benachrichtigungskanals zu ändern.
     * Deshalb wird bei jeder Ton-Änderung ein neuer Kanal mit höherer Nummer angelegt.
     */
    var channelVersion: Int
        get() = prefs.getInt("channel_version", 1)
        set(v) = prefs.edit().putInt("channel_version", v).apply()

    // ---------- Zwischenspeicher der Webseite ----------

    var cachedDates: List<CleaningDate>
        get() {
            val json = prefs.getString("cached_dates", null) ?: return emptyList()
            val arr = JSONArray(json)
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                CleaningDate(o.getString("l"), LocalDate.parse(o.getString("d")))
            }
        }
        set(list) {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("l", it.label).put("d", it.date.toString())) }
            prefs.edit().putString("cached_dates", arr.toString()).apply()
        }

    var cachedMonths: List<YearMonth>
        get() = prefs.getString("cached_months", "")!!
            .split(',').filter { it.isNotBlank() }.map { YearMonth.parse(it) }
        set(v) = prefs.edit().putString("cached_months", v.joinToString(",")).apply()

    /** Zeitpunkt (ms) des letzten erfolgreichen Abrufs, 0 = noch nie. */
    var lastFetchMillis: Long
        get() = prefs.getLong("last_fetch", 0L)
        set(v) = prefs.edit().putLong("last_fetch", v).apply()

    /** Termine der gewählten Straßen, über die der Nutzer schon Bescheid weiß. */
    var knownSelectedKeys: Set<String>
        get() = prefs.getStringSet("known_selected_keys", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("known_selected_keys", v).apply()

    /** Request-Codes der aktuell geplanten Alarme (zum Löschen beim Neuplanen). */
    var scheduledRequestCodes: Set<String>
        get() = prefs.getStringSet("scheduled_codes", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("scheduled_codes", v).apply()

    companion object {
        private const val KEY_SELECTED = "selected_labels"
    }
}
