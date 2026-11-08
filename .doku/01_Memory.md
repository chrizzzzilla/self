# Self — Session-Memory (neueste oben)

## 2026-11-08 — Session 2a: Kindle-Quelle + GeckoView raus (APK wieder klein) [Rechner: Tablet]
- Reader-Quellen: neue Quelle KINDLE (BookSource) neben TOLINO. Wird wie Tolino als **App gestartet**
  (`com.amazon.kindle`), nicht im eingebauten Browser – gekaufte Kindle-Bücher haben DRM, das Self/Readium
  nicht selbst öffnen kann.
- `openTolinoApp`/`openKindleApp` teilen sich jetzt `openReaderApp(context, pkg)` (StoreBrowser.kt): App starten,
  sonst Play Store, sonst Store-Website. Fehlt die App → Snackbar (`reader_kindle_app_missing`).
- LibraryScreen: Auswahl KINDLE → `openKindleApp`. Erscheint automatisch im SourceChooser (`BookSource.entries`).
- **GeckoView (Firefox-Engine, ~80 MB native Libs) entfernt** – war nur für den Tolino-*Webreader* nötig, der jetzt
  als App läuft. Eingebauter Shop-Browser wieder auf die **Android-System-WebView** (0 MB extra), das ist die
  bewährte Version von vor dem Gecko-Umbau (Commit 1656b95^): Download per DownloadListener, blob/data-Übernahme
  per JS-Bridge, `downloadToCache` mit Cookies, SecureUpgradeClient (http→https, Absturz abfangen). PC/Tablet-
  Umschalter blieb erhalten (userAgent). Gecko.kt gelöscht, Dep + Mozilla-Maven-Repo raus. APK sollte wieder ~20 MB.
- Offen: am Gerät testen – (1) Kindle-App öffnet sich, (2) Download-Shops (Gutenberg/Standard Ebooks/Beam/
  Libreture/J-Novel/Humble) laden EPUBs noch korrekt in die Bibliothek. Kindle-Icon noch generisches Buch-Symbol.

## 2026-11-01 — Session 1g: Lesen ins Ambiente, Weiterlesen, Spiel Ruhe (bis 0.1.21) [Rechner: Office]
- Ambiente-Panel rechts: LESEN (Hell/Dunkel/Sepia, Schrift, Filter); Aa-Knopf weg. Ambiente startet mit der App (letzte Mischung).
- Soul: „Weiterlesen“ oben mittig (zuletzt geöffnetes Buch).
- Path: Me + Ruhe/Ehrgeiz/Demut. Ruhe = CalmGame.kt: Atem halten/loslassen, 4 Stationen/Tag (See, Strand, Boot, Sturm) à 2,5 Min, Licht aus → Text statt Game Over.
- Progression (PathStore.kt): Station n = n Punkte, Level-Schwelle 10·1,25^(L−1); Fähigkeiten: Vorahnung L2, Schild L3, Ruhiger Atem L4 (CalmAbilities).
- Offen: Ehrgeiz + Demut, Me (RPG/Weltenbau, Lesezeit fließt ein), Feedback zu Ruhe.

## 2026-11-01 — Session 1f: Bibliothek-Fenster, CBZ, echte Naturaufnahmen (bis 0.1.18) [Rechner: Office]
- Naturgeräusche = echte CC0-Aufnahmen (BigSoundBank, assets/nature, QUELLEN.txt), Loop mit 2-s-Überblendung, SampleDecoder (MediaCodec).
- Chris: keine Menschen/Tiere in Szenen, nur laues Lüftchen, kein Play-Knopf (hörbar = spielt), keine Emojis an Reglern (TextSlider mit Schrift im Balken).
- Soul: Arty-Sonne + Bibliotheks-Knopf unten rechts → LibraryPanel (Suche, Genre, +, lange drücken = löschen). DB v2 (genre). CBZ via ImageNavigatorFragment.
- Offen: PDF (Pdfium-Adapter), Foreground-Service, Path-Spiele, Feedback zu Aufnahmen/Szenen.

## 2026-11-01 — Session 1e: Soul/Path, Quellen, Ambiente-Panel (bis 0.1.15) [Rechner: Office]
- Menü: Raster-Knopf unten links → Soul (Lesen) / Path (Spiel), animierte Glyphen. Arty = aufgehende Sonne unten.
- Buch-Quellen im eingebauten Browser (StoreBrowser.kt, INTERNET): Tolino-Webreader, Beam, Libreture, Gutenberg, Standard Ebooks, J-Novel Club, Humble; EPUB-Download wird direkt importiert.
- Path: nur Bildschirmaufbau (Welt + Ruhe/Ehrgeiz/Wertschätzung, Tageslimit 10/10/10 + 30 Min). Spielideen + Lesezeit→oberes Spiel noch zu klären.
- Ambiente-Panel Vollbild (AmbientPanel.kt): Visualizer, Presets, Kacheln + senkrechte Lautstärke, Bilder CITY/MOUNTAIN/FOREST/FIELD mit Jahreszeit, Tageszeit, Echtzeit (Sky.kt/Landscapes.kt).
- Offen: Feedback zu den Szenen; evtl. echte CC0-Naturaufnahmen statt Synthese; CBZ für Manga; Foreground-Service.

## 2026-11-01 — Session 1d: Entspannungs-Look (0.1.8) [Rechner: Office]
- Leitbild (Chris): wirklich entspannende App, Stil wie `Desktop/Pitch-Motion.html` (fast schwarz, warmes Licht,
  Partikel-Sprites mit Plus-Blending, Staub mit Tiefe, Lichtfäden Weiß/Gold, Arty-Blau #6888EC, Manrope) – nicht comichaft.
- `core/design/Ambient.kt`: AmbientTheme, AmbientBackground, ArtySwarm, TypewriterLabel (Bounce), ArtyButton, ArtyMini.
- Statusleiste + Navigationsleiste immer aus (`enterImmersive`, auch Cutout). Leere Bibliothek ohne Texte, nur Arty.
- Higgsfield: kein Zugriff von hier; Chris erzeugt Videos selbst (Prompts geliefert) → später Video-Hintergrund einbauen.
- Orell Füssli: nur Wasserzeichen-EPUBs gehen, Adobe DRM (.acsm) nicht.

## 2026-11-01 — Session 1c: Nachtstadt hinter dem Buch + Klang-Mixer (0.1.7) [Rechner: Office]

### Zuletzt fertiggestellt
- Fokus-Reiter entfernt. `CityScene` (perspektivische Nachtstadt) läuft im Reader **hinter** dem Buch:
  Readium bekommt `RsProperties(backgroundColor = "transparent")`, `Theme.LIGHT` + eigene Textfarbe, und
  Pager/WebViews werden nach jedem Seiten-/Einstellungswechsel durchsichtig gemacht (`clearNavigatorBackgrounds`).
  Davor liegt ein Lesefilter (Farbe des Farbmodus, Regler in „Darstellung“).
- Fokusmodus-Taste (Landschafts-Symbol): Buch blendet aus, Bedien-Pille unten („Zum Buch“, Play, Klang).
- Klang-Mixer `AmbientEngine` = `AmbienceLayer` (10) + `NatureLayer` (8, mehrfach) + `PercussionLayer` (10,
  Tempo 60–120, Hell/Dunkel), Lautstärke je Bereich, Presets 1–5 (`AmbientStore`, DataStore „ambiente“).
- Chris wollte: keine roten Blinklichter, keine Stern-Kreuze/-Scheine, Stadt dunkler (umgesetzt in 0.1.6).

### Nächster Schritt
- Chris testet 0.1.7. Offene Frage an ihn: Statusleiste auch in der Bibliothek ausblenden oder Uhr/Akku im Reader?
- Higgsfield: kein Connector → Chris erzeugt Videos selbst; danach Video als Lese-Hintergrund einbauen.
- Foreground-Service für Klang bei ausgeschaltetem Bildschirm.

## 2026-11-01 — Session 1b: Fokus-Bereich v1 [Rechner: Office]

### Zuletzt fertiggestellt
- Nur noch EIN Kanal: Push auf `main` → Release → Obtainium (Chris wollte kein „Self Dev“; der Code dafür liegt
  ungenutzt im Workflow / `SELF_CHANNEL`).
- Fokus v1 (0.1.4): `LofiSynth` (generative Lofi-Musik), `FocusPlayer` (AudioTrack-Thread, Audio-Fokus,
  = `SelfCore.focusAudio`), `WindowScene` (Canvas: Altstadt, Tageszeit nach Uhrzeit, Regen, Pflanze, Tasse, Lampe).

### Nächster Schritt
- Chris' Feedback zu Klang + Bild auf dem Tablet.
- Figur (schreibt / schaut aus dem Fenster): NICHT Lofi Girl nachbauen (geschützt, Repo öffentlich).
  Weg: eigene Bilder von Chris als Ebenen → animieren.
- Foreground-Service + Benachrichtigung (Musik bei ausgeschaltetem Bildschirm), Regler-Werte speichern,
  Fokus-/Sleep-Timer, Presets.

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
