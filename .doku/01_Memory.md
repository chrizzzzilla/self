# Self — Session-Memory (neueste oben)

## 2026-11-01 — Session 1: Grundgerüst + EPUB-Reader [Rechner: Office]

### Zuletzt fertiggestellt
- Repo `chrizzzzilla/self` (öffentlich) angelegt, Phase 1 komplett: Module `app`, `core`, `feature-reader`,
  `feature-focus` und `feature-game` (die letzten beiden als Platzhalter).
- Reader auf Readium 3.4 (`EpubNavigatorFragment` in eigener `ReaderActivity`, Compose-Leisten darüber).
  Enthalten: Bibliothek (Room), Import per Dateiauswahl und „Öffnen mit“, Leseposition (Locator-JSON),
  Inhaltsverzeichnis, Schriftgröße, System/Hell/Dunkel/Sepia (DataStore), Musik-Knopf als Platzhalter.
- CI: `.github/workflows/build.yml` → signierte Release-APK → GitHub-Release `v0.1.<run>` (für Obtainium).
- Fester Signatur-Schlüssel (PKCS12, Alias `self`) liegt als Secret im Repo, Sicherung unter `C:\Chris\03_Assets\self\`.

### Nächster Schritt
- Chris: Obtainium auf Tablet + Handy, App hinzufügen, mit echten EPUBs testen.
- Schlüssel + Passwort in Vaultwarden ablegen (`self-release-INFO.txt`).
- Danach Phase 2: Fokus-Bereich (generative Musik mit `AudioTrack`, Media3-`MediaSessionService`,
  animierte Hintergründe). Er meldet sich über `SelfCore.focusAudio` an, der Reader braucht dafür keine Änderung.

### Lessons
- AGP 9 hat **eingebautes Kotlin**: `org.jetbrains.kotlin.android` nicht anwenden. Die Kotlin-Version wird
  über `classpath(kotlin-gradle-plugin)` im Root-`buildscript` festgelegt.
- AGP 9.5 braucht mindestens Gradle 9.6.
- Readium: die `FragmentFactory` muss vor `super.onCreate()` stehen. Die Publication wird deshalb vorher
  geöffnet (`ReaderSessions`). Nach einem Prozess-Tod gibt es eine Dummy-Factory und `finish()`.
- Readium-Quellcode zum Nachschlagen: `git clone --depth 1 --branch 3.4.0 https://github.com/readium/kotlin-toolkit`
  (unter Windows mit `core.longpaths=true`).
- Kein lokaler Build (kein JDK/SDK): Fehler aus den Actions-Logs lesen (`gh.cjs`/`watch.cjs`-Muster über die GitHub-API).
