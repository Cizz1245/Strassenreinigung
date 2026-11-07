package de.cizz.strassenreinigung.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.cizz.strassenreinigung.MainActivity
import de.cizz.strassenreinigung.R
import de.cizz.strassenreinigung.data.Settings
import android.provider.Settings as SystemSettings

/**
 * Benachrichtigungskanäle und das Anzeigen der Meldungen.
 *
 * Zwei Kanäle:
 *  - "erinnerung_vN": die Umpark-Erinnerung (wichtig, mit gewähltem Ton)
 *  - "neue_termine":  Hinweis, dass neue/geänderte Termine auf der Webseite stehen
 */
object Notifications {

    private const val CHANNEL_INFO = "neue_termine"
    private const val REMINDER_PREFIX = "erinnerung_v"
    private const val INFO_NOTIFICATION_ID = 1

    fun reminderChannelId(settings: Settings) = REMINDER_PREFIX + settings.channelVersion

    /** Legt die Kanäle an (mehrfacher Aufruf ist unschädlich). */
    fun createChannels(context: Context) {
        val settings = Settings(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val currentId = reminderChannelId(settings)

        // Alte Erinnerungskanäle (mit altem Ton) entfernen
        nm.notificationChannels
            .filter { it.id.startsWith(REMINDER_PREFIX) && it.id != currentId }
            .forEach { nm.deleteNotificationChannel(it.id) }

        val reminder = NotificationChannel(
            currentId, "Erinnerung ans Umparken", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Erinnert dich, das Auto vor der Straßenreinigung wegzufahren."
            enableVibration(true)
            val sound = settings.sound
            if (sound == SOUND_SILENT) {
                setSound(null, null)
            } else {
                val uri = sound?.let(Uri::parse) ?: SystemSettings.System.DEFAULT_NOTIFICATION_URI
                val attrs = AudioAttributes.Builder()
                    .setUsage(
                        if (settings.useAlarmVolume) AudioAttributes.USAGE_ALARM
                        else AudioAttributes.USAGE_NOTIFICATION
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(uri, attrs)
            }
        }
        nm.createNotificationChannel(reminder)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INFO, "Neue Termine", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Meldet, wenn die Stadt neue Reinigungstermine veröffentlicht." }
        )
    }

    /** Nach einer Ton-Änderung: neuen Kanal anlegen, da Android Kanaltöne nicht nachträglich ändert. */
    fun recreateReminderChannel(context: Context) {
        val settings = Settings(context)
        settings.channelVersion = settings.channelVersion + 1
        createChannels(context)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun showReminder(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, reminderChannelId(Settings(context)))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    fun showInfo(context: Context, title: String, text: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(INFO_NOTIFICATION_ID, n)
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    const val SOUND_SILENT = "silent"
}
