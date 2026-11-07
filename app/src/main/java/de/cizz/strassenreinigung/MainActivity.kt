package de.cizz.strassenreinigung

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.cizz.strassenreinigung.data.CleaningDate
import de.cizz.strassenreinigung.data.ScheduleRepository
import de.cizz.strassenreinigung.reminder.ReminderScheduler
import de.cizz.strassenreinigung.ui.MainViewModel
import de.cizz.strassenreinigung.ui.UiState
import de.cizz.strassenreinigung.work.UpdateMessage
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1E5AA8))) {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}

private val DAY = DateTimeFormatter.ofPattern("EE dd.MM.yyyy", Locale.GERMANY)
private val DAY_TIME = DateTimeFormatter.ofPattern("EE dd.MM. 'um' HH:mm 'Uhr'", Locale.GERMANY)

private fun formatMinutes(m: Int) = "%02d:%02d Uhr".format(m / 60, m % 60)

/** Für die Suche: Groß/Klein und ß/ss egal, "str." findet "straße". */
private fun normalize(s: String) = s.lowercase(Locale.GERMANY)
    .replace("ß", "ss").replace("str.", "strasse")

@Composable
private fun MainScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Ab Android 13: einmal um Erlaubnis für Benachrichtigungen bitten
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Auswahldialog für den Ton (Android-eigener Dialog)
    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            vm.setSound(uri)
        }
    }

    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, current / 60, current % 60, true).show()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.padding(top = 8.dp))
            Text("Straßenreinigung Görlitz", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            StatusLine(state)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.refresh() }, enabled = !state.loading) {
                    Text(if (state.loading) "Lade …" else "Termine aktualisieren")
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ScheduleRepository.URL)))
                }) { Text("Webseite") }
            }
            state.message?.let {
                InfoCard(it, onClose = vm::clearMessage)
            }
            if (!state.exactAlarmsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                InfoCard(
                    "Für pünktliche Erinnerungen bitte \"Wecker & Erinnerungen\" für diese App erlauben.",
                    actionLabel = "Erlauben",
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                        )
                    },
                )
            }
        }

        // ---------- Meine Straßen ----------
        item { SectionTitle("Meine Straßen") }
        if (state.selected.isEmpty()) {
            item { Text("Noch keine Straße gewählt. Suche unten nach deiner Straße und tippe sie an.") }
        }
        items(state.selected.sorted()) { label ->
            SelectedStreet(label, state.dates.filter { it.label == label }, onRemove = { vm.removeStreet(label) })
        }

        // ---------- Suche ----------
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("Straße suchen") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val results = searchResults(state)
        items(results, key = { "search_$it" }) { label ->
            val dates = state.dates.filter { it.label == label }.sortedBy { it.date }
            Column(
                Modifier.fillMaxWidth().clickable { vm.addStreet(label) }.padding(vertical = 6.dp)
            ) {
                Text(label, fontWeight = FontWeight.Medium)
                Text(
                    dates.joinToString(" · ") { it.date.format(DAY) } + "   ➕ hinzufügen",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
        }
        if (state.query.length >= 2 && results.isEmpty()) {
            item {
                Text(
                    "Keine Straße gefunden. Hinweis: Hier stehen nur Straßen mit monatlicher Reinigung, " +
                        "die aktuell auf goerlitz.de veröffentlicht sind."
                )
            }
        }

        // ---------- Erinnerungen ----------
        item { SectionTitle("Erinnerungen") }
        item {
            ReminderRow(
                title = "Am Vortag",
                enabled = state.dayBeforeEnabled,
                minutes = state.dayBeforeMinutes,
                onToggle = { vm.setDayBefore(enabled = it) },
                onPickTime = { pickTime(state.dayBeforeMinutes) { m -> vm.setDayBefore(minutes = m) } },
            )
            ReminderRow(
                title = "Am Reinigungstag",
                enabled = state.sameDayEnabled,
                minutes = state.sameDayMinutes,
                onToggle = { vm.setSameDay(enabled = it) },
                onPickTime = { pickTime(state.sameDayMinutes) { m -> vm.setSameDay(minutes = m) } },
            )
            if (state.sameDayEnabled && state.sameDayMinutes >= 7 * 60) {
                Text(
                    "Hinweis: Die Erinnerung am Reinigungstag kommt erst ab 7 Uhr – evtl. zu spät.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ton")
                    Text(state.soundName, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = {
                    soundPicker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Ton für die Erinnerung")
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, vm.currentSoundUri())
                    )
                }) { Text("Ton wählen") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wecker-Lautstärke")
                    Text(
                        "Klingelt auch, wenn das Handy auf lautlos steht",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = state.useAlarmVolume, onCheckedChange = vm::setUseAlarmVolume)
            }
            TextButton(onClick = vm::sendTestNotification) { Text("Test-Benachrichtigung senden") }
        }

        // ---------- Nächste Erinnerungen ----------
        item { SectionTitle("Geplante Erinnerungen") }
        if (state.upcoming.isEmpty()) {
            item { Text("Keine Erinnerungen geplant.") }
        }
        items(state.upcoming.take(10)) { p ->
            val kind = if (p.kind == ReminderScheduler.Kind.DAY_BEFORE) "Vortag" else "Reinigungstag"
            Text("${p.at.format(DAY_TIME)} – $kind: ${p.entry.label}", style = MaterialTheme.typography.bodyMedium)
        }

        item {
            Text(
                "Angaben ohne Gewähr. Maßgeblich sind die Termine auf goerlitz.de. " +
                    "Im Winter können Termine witterungsbedingt entfallen oder sich ändern. " +
                    "Die App prüft die Webseite automatisch etwa alle 6 Stunden.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

/** Straßen, die zur Sucheingabe passen (erst ab 2 Zeichen). */
private fun searchResults(state: UiState): List<String> {
    if (state.query.trim().length < 2) return emptyList()
    val q = normalize(state.query.trim())
    return state.dates.map { it.label }.distinct()
        .filter { it !in state.selected && normalize(it).contains(q) }
        .sorted()
        .take(50)
}

@Composable
private fun StatusLine(state: UiState) {
    val months = if (state.months.isEmpty()) "noch keine" else state.months.joinToString(", ") { UpdateMessage.monthName(it) }
    val checked = if (state.lastFetchMillis == 0L) "noch nie" else
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.GERMANY)
            .format(Date(state.lastFetchMillis))
    Text("Veröffentlichte Termine: $months", style = MaterialTheme.typography.bodyMedium)
    Text("Zuletzt geprüft: $checked", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SelectedStreet(label: String, dates: List<CleaningDate>, onRemove: () -> Unit) {
    val today = LocalDate.now()
    val upcoming = dates.filter { !it.date.isBefore(today) }.sortedBy { it.date }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium)
                Text(
                    if (upcoming.isEmpty()) "Kein kommender Termin veröffentlicht"
                    else "Nächste Reinigung: " + upcoming.joinToString(" · ") { it.date.format(DAY) },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onRemove) { Text("Entfernen") }
        }
    }
}

@Composable
private fun ReminderRow(
    title: String,
    enabled: Boolean,
    minutes: Int,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, Modifier.weight(1f))
        OutlinedButton(onClick = onPickTime, enabled = enabled) { Text(formatMinutes(minutes)) }
        Spacer(Modifier.width(8.dp))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun InfoCard(text: String, onClose: (() -> Unit)? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(text)
            Row {
                if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
                if (onClose != null) TextButton(onClick = onClose) { Text("OK") }
            }
        }
    }
}
