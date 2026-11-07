# Straßenreinigung Görlitz – Erinnerungs-App (Android)

Erinnert dich rechtzeitig, dein Auto umzuparken, bevor deine Straße gekehrt wird.
Die Termine kommen von <https://www.goerlitz.de/Strassenreinigung.html>.

## Was die App kann

- **Straße suchen und merken** – auch mehrere; Straßen mit Abschnitten
  („rechts von … bis …“) sind einzeln wählbar, weil sie verschiedene Termine haben.
- **Zwei Erinnerungen pro Termin**, beide einzeln ein-/ausschaltbar mit eigener Uhrzeit:
  - am **Vortag** (Standard 18:00 Uhr)
  - am **Reinigungstag** (Standard 06:00 Uhr – also vor 7 Uhr)
- **Ton wählbar**: Standardton, beliebiger Benachrichtigungs-/Weckton oder „Lautlos“
  (nur Push-Nachricht). Optional über die **Wecker-Lautstärke**, dann klingelt es auch im Lautlos-Modus.
- **Automatische Prüfung auf neue Termine** etwa alle 6 Stunden (sobald Internet da ist)
  und bei jedem Öffnen der App. Meldungen:
  - „Neue Reinigungstermine für deine Straße“ – Erinnerungen werden automatisch eingeplant
  - „Die Termine für <Monat> sind online. Deine Straße ist dort nicht aufgeführt.“
  - Hinweis, wenn ein bekannter Termin geändert/gestrichen wurde
- Erinnerungen überleben einen **Neustart** des Handys.

**Nicht enthalten:** die *wöchentliche* Reinigung der Reinigungsklassen 1 und 5 (RK 1/RK 5),
die auf der Webseite ohne Datum steht.

## Projekt in Android Studio öffnen

1. Android Studio starten → **File → Open** → diesen Ordner (`Strassenreinigung`) wählen.
2. Beim ersten Öffnen lädt Android Studio Gradle und alle Bibliotheken herunter (Internet nötig, dauert einige Minuten).
   Falls Android Studio ein Update des „Android Gradle Plugin“ vorschlägt, kannst du es annehmen.
3. Zum Ausprobieren: Handy per USB anschließen (USB-Debugging an) oder einen Emulator starten → grüner ▶-Knopf.

## APK für dein Handy erzeugen

**Schnell (Debug-APK, zum Selbst-Installieren völlig ausreichend):**
**Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
Danach erscheint unten rechts „locate“ – die Datei liegt unter
`app/build/outputs/apk/debug/app-debug.apk`. Diese Datei aufs Handy kopieren und öffnen
(Installation aus „unbekannten Quellen“ erlauben).

**Signierte Release-APK:** **Build → Generate Signed App Bundle / APK → APK**,
einen neuen Schlüssel (Keystore) anlegen, „release“ wählen.
Den Keystore gut aufbewahren – Updates der App müssen mit demselben Schlüssel signiert sein.

## Nach der Installation auf dem Handy

- Benachrichtigungen **erlauben**, wenn gefragt.
- Bei Android 12 erscheint ggf. ein Hinweis „Wecker & Erinnerungen erlauben“ – antippen und erlauben.
- Bei manchen Herstellern (Samsung, Xiaomi, Huawei …) unter *Akku* die App auf
  **„Nicht optimiert“ / „Uneingeschränkt“** stellen, sonst kann die Hintergrundprüfung ausgebremst werden.
- Mit **„Test-Benachrichtigung senden“** prüfen, ob Ton und Anzeige passen.

## Aufbau des Codes

```
app/src/main/
├── AndroidManifest.xml          Berechtigungen, Activity, Empfänger
├── java/de/cizz/strassenreinigung/
│   ├── StrassenreinigungApp.kt  Start: Kanäle anlegen, Hintergrundprüfung einplanen
│   ├── MainActivity.kt          Die Oberfläche (Jetpack Compose)
│   ├── ui/MainViewModel.kt      Verbindet Oberfläche und Logik, hält den Bildschirm-Zustand
│   ├── data/
│   │   ├── CleaningDate.kt              Datenmodell eines Termins
│   │   ├── CleaningScheduleParser.kt    Liest Termine aus dem Seitentext (ohne Android → testbar)
│   │   ├── ScheduleRepository.kt        Lädt die Webseite, erkennt neue/geänderte Termine
│   │   └── Settings.kt                  Speichert Auswahl, Uhrzeiten, Ton, Zwischenstand
│   ├── reminder/
│   │   ├── ReminderScheduler.kt  Plant die Wecker-Alarme (AlarmManager)
│   │   ├── ReminderReceiver.kt   Wird zur Uhrzeit geweckt → zeigt Erinnerung
│   │   ├── Notifications.kt      Benachrichtigungskanäle, Ton, Anzeige
│   │   └── BootReceiver.kt       Plant nach Neustart alles neu
│   └── work/
│       ├── CheckWorker.kt        Hintergrundprüfung alle ~6 Std. (WorkManager)
│       └── UpdateMessage.kt      Text der „Neue Termine“-Meldung
└── res/                          Symbol, Farben, App-Name
app/src/test/…/CleaningScheduleParserTest.kt   Tests mit echten Zeilen der Webseite
```

### Ablauf in Kurzform

1. **Abruf:** `ScheduleRepository.refresh()` lädt die Seite mit *jsoup*, wandelt HTML in Text
   mit Zeilenumbrüchen um und gibt ihn an `CleaningScheduleParser`.
2. **Auslesen:** Der Parser sucht Überschriften „Reinigungstermine <Monat> <Jahr>“ und liest
   darunter jede Zeile „Straßenname (Abschnitt) TT.MM.JJJJ“. Er hört bei
   „Wöchentliche Reinigung …“ auf. Sonderfall: Steht das Datum in der nächsten Zeile, wird es zugeordnet.
3. **Vergleich:** Neue Monate und neue Termine der gewählten Straßen werden mit dem
   gespeicherten Stand verglichen → daraus entsteht die „Neue Termine“-Meldung.
4. **Planen:** `ReminderScheduler.rescheduleAll()` löscht alle Alarme und plant sie aus dem
   aktuellen Stand neu – nach jedem Abruf, jeder Einstellungsänderung und nach Neustart.
5. **Erinnern:** Zur Uhrzeit weckt Android den `ReminderReceiver`, der die Benachrichtigung zeigt.

### Wenn die Stadt die Webseite umbaut

Findet der Parser keine Termine mehr, behält die App den letzten Stand und zeigt
„Abruf fehlgeschlagen … Aufbau der Seite geändert“. Dann muss `CleaningScheduleParser.kt`
angepasst werden; die Tests in `CleaningScheduleParserTest.kt` helfen dabei.

## Hinweise

- Angaben ohne Gewähr – maßgeblich sind die Termine auf goerlitz.de.
  Laut Webseite können Termine im Winter witterungsbedingt entfallen oder sich ändern.
- Die Berechtigung `USE_EXACT_ALARM` ist für Wecker-/Erinnerungs-Apps gedacht. Für die
  private Installation per APK ist das kein Problem; für den Google Play Store müsste das geprüft werden.
- Technik: Kotlin, Jetpack Compose (Material 3), WorkManager, jsoup; minSdk 26 (Android 8.0), targetSdk 35.
