package de.cizz.strassenreinigung.ui

import android.app.AlarmManager
import android.app.Application
import android.media.RingtoneManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.cizz.strassenreinigung.data.CleaningDate
import de.cizz.strassenreinigung.data.ScheduleRepository
import de.cizz.strassenreinigung.data.Settings
import de.cizz.strassenreinigung.reminder.Notifications
import de.cizz.strassenreinigung.reminder.ReminderScheduler
import de.cizz.strassenreinigung.work.UpdateMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Alles, was der Bildschirm anzeigt. */
data class UiState(
    val dates: List<CleaningDate> = emptyList(),
    val months: List<YearMonth> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val dayBeforeEnabled: Boolean = true,
    val dayBeforeMinutes: Int = 18 * 60,
    val sameDayEnabled: Boolean = true,
    val sameDayMinutes: Int = 6 * 60,
    val soundName: String = "",
    val useAlarmVolume: Boolean = false,
    val lastFetchMillis: Long = 0L,
    val loading: Boolean = false,
    val message: String? = null,
    val upcoming: List<ReminderScheduler.Planned> = emptyList(),
    val exactAlarmsAllowed: Boolean = true,
)

/**
 * Verbindet Oberfläche und Logik. Jede Änderung wird sofort gespeichert,
 * danach werden die Erinnerungen neu geplant und der Bildschirm aktualisiert.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private val repository = ScheduleRepository(settings)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        reload()
        refresh(silent = true)   // beim Öffnen der App immer aktuellen Stand holen
    }

    /** Liest den gespeicherten Stand neu ein. */
    private fun reload(message: String? = _state.value.message, loading: Boolean = false) {
        val am = getApplication<Application>().getSystemService(AlarmManager::class.java)
        _state.value = _state.value.copy(
            dates = settings.cachedDates,
            months = settings.cachedMonths,
            selected = settings.selectedLabels,
            dayBeforeEnabled = settings.dayBeforeEnabled,
            dayBeforeMinutes = settings.dayBeforeMinutes,
            sameDayEnabled = settings.sameDayEnabled,
            sameDayMinutes = settings.sameDayMinutes,
            soundName = soundName(),
            useAlarmVolume = settings.useAlarmVolume,
            lastFetchMillis = settings.lastFetchMillis,
            upcoming = ReminderScheduler.plannedReminders(settings),
            exactAlarmsAllowed = ReminderScheduler.canUseExactAlarms(am),
            loading = loading,
            message = message,
        )
    }

    private fun rescheduleAndReload() {
        ReminderScheduler.rescheduleAll(getApplication())
        reload()
    }

    fun refresh(silent: Boolean = false) {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val msg = try {
                val update = repository.refresh()
                ReminderScheduler.rescheduleAll(getApplication())
                UpdateMessage.build(update, settings)?.second
                    ?: if (silent) null else "Termine sind aktuell."
            } catch (e: Exception) {
                "Abruf fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}. " +
                    "Der zuletzt gespeicherte Stand wird verwendet."
            }
            reload(message = msg, loading = false)
        }
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun addStreet(label: String) {
        settings.selectedLabels = settings.selectedLabels + label
        repository.markSelectedAsKnown()
        _state.value = _state.value.copy(query = "")
        rescheduleAndReload()
    }

    fun removeStreet(label: String) {
        settings.selectedLabels = settings.selectedLabels - label
        repository.markSelectedAsKnown()
        rescheduleAndReload()
    }

    fun setDayBefore(enabled: Boolean? = null, minutes: Int? = null) {
        enabled?.let { settings.dayBeforeEnabled = it }
        minutes?.let { settings.dayBeforeMinutes = it }
        rescheduleAndReload()
    }

    fun setSameDay(enabled: Boolean? = null, minutes: Int? = null) {
        enabled?.let { settings.sameDayEnabled = it }
        minutes?.let { settings.sameDayMinutes = it }
        rescheduleAndReload()
    }

    /** @param uri gewählter Ton; null = "Lautlos" im Auswahldialog. */
    fun setSound(uri: Uri?) {
        settings.sound = when (uri) {
            null -> Notifications.SOUND_SILENT
            android.provider.Settings.System.DEFAULT_NOTIFICATION_URI -> null
            else -> uri.toString()
        }
        Notifications.recreateReminderChannel(getApplication())
        reload()
    }

    fun setUseAlarmVolume(v: Boolean) {
        settings.useAlarmVolume = v
        Notifications.recreateReminderChannel(getApplication())
        reload()
    }

    fun currentSoundUri(): Uri? = when (val s = settings.sound) {
        null -> android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
        Notifications.SOUND_SILENT -> null
        else -> Uri.parse(s)
    }

    fun sendTestNotification() {
        Notifications.showReminder(
            getApplication(), 4711,
            "Test: Straßenreinigung",
            "So sieht deine Erinnerung aus – mit dem eingestellten Ton.",
        )
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** Nach Rückkehr in die App (z. B. aus den Systemeinstellungen) neu einlesen. */
    fun onResume() = rescheduleAndReload()

    private fun soundName(): String = when (val s = settings.sound) {
        null -> "Standard-Benachrichtigungston"
        Notifications.SOUND_SILENT -> "Kein Ton (nur Benachrichtigung)"
        else -> runCatching {
            RingtoneManager.getRingtone(getApplication(), Uri.parse(s))?.getTitle(getApplication())
        }.getOrNull() ?: "Eigener Ton"
    }
}
