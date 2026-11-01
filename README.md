# Self

Eine native Android-App (Kotlin + Jetpack Compose) für den Eigengebrauch auf Tablet und Handy.
Sie ist frei von Tracking und nutzt nur freie Bibliotheken. Ziel ist, dass sie F-Droid-tauglich bleibt.

| Bereich | Stand |
|---|---|
| **Lesen** | EPUB-Reader mit Bibliothek, Leseposition, Inhaltsverzeichnis, Schriftgröße, Hell/Dunkel/Sepia |
| **Fokus** | Platzhalter. Später: generative Musik auf dem Gerät, animierte Hintergründe, Timer |
| **Spiel** | Platzhalter. Später: ein kleines, ruhiges Spiel (Compose Canvas) |

## Module

```
app/            App-Start, Navigation (Leiste unten / Rail am Tablet), Theme, „Öffnen mit“
core/           Bibliothek (Room), Einstellungen (DataStore), Design (Farben, Window Size Classes),
                Schnittstelle FocusAudio für die spätere Fokus-Musik
feature-reader/ EPUB-Reader auf Basis des Readium Kotlin Toolkit 3.x
feature-focus/  Fokus-Bereich (Platzhalter)
feature-game/   Spiel (Platzhalter)
```

## Technik

- Kotlin 2.4, Android Gradle Plugin 9.5 (mit eingebautem Kotlin), Gradle 9.8, Compose BOM 2026.09
- Readium Kotlin Toolkit 3.4 (`readium-shared`, `readium-streamer`, `readium-navigator`), nur DRM-freie EPUBs
- minSdk 26, targetSdk/compileSdk 37
- Keine Google-Play-Dienste, kein Firebase, keine Tracker oder Analytics

## Bauen

Die APK baut **GitHub Actions** (`.github/workflows/build.yml`) bei jedem Push auf `main`.
Sie landet als Artifact und als **GitHub-Release**. Installation und Updates erklärt die [ANLEITUNG.md](ANLEITUNG.md).

Lokal geht es auch, wenn ein JDK 17+ und das Android SDK vorhanden sind: `./gradlew assembleRelease`.

## Lizenzen der Abhängigkeiten

Readium (BSD-3-Clause), AndroidX/Jetpack (Apache 2.0) und Kotlin/Coroutines (Apache 2.0).
