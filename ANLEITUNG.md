# Anleitung: Self bauen, installieren und aktualisieren

Diese Anleitung ist für Einsteiger gedacht. Du musst nichts installieren und nichts kompilieren,
denn GitHub baut die App für dich.

---

## 1. Wie die App entsteht (Überblick)

```
Code ändern  →  Push auf GitHub  →  GitHub Actions baut die APK  →  GitHub-Release  →  Obtainium aktualisiert Tablet + Handy
```

- **Repository:** https://github.com/chrizzzzilla/self (öffentlich)
- **Builds verfolgen:** Reiter **Actions**: https://github.com/chrizzzzilla/self/actions
- **Fertige APKs:** Reiter **Releases**: https://github.com/chrizzzzilla/self/releases

Jeder Push auf `main` erzeugt automatisch eine neue Version (`0.1.<Build-Nummer>`).

---

## 2. Build verfolgen

1. Öffne https://github.com/chrizzzzilla/self/actions
2. Ganz oben steht der neueste Lauf **„APK bauen“**:
   - 🟡 gelber Kreis: läuft noch (etwa 5–10 Minuten)
   - ✅ grüner Haken: fertig, die APK liegt im Release
   - ❌ rotes Kreuz: Fehler. Klicke auf den Lauf, dann auf den roten Schritt, dort steht die Meldung.

---

## 3. App installieren: empfohlen mit Obtainium

**Obtainium** ist eine freie App, die Apps direkt von GitHub-Releases installiert und aktualisiert.
Du musst danach nie wieder manuell APKs herunterladen.

Mach das einmal pro Gerät (Tablet **und** Handy):

1. Obtainium installieren, entweder aus **F-Droid** (App „Obtainium“) oder direkt als APK von
   https://github.com/ImranR98/Obtainium/releases (Datei `app-arm64-v8a-release.apk` passt für fast alle Geräte).
2. Beim Installieren fragt Android: *„Aus dieser Quelle installieren?“* → **Einstellungen** → Schalter
   **„Dieser Quelle vertrauen“** einschalten → zurück → **Installieren**.
3. Obtainium öffnen → **„App hinzufügen“** → als Quelle-URL eintragen:
   ```
   https://github.com/chrizzzzilla/self
   ```
   → **Hinzufügen** → **Installieren**.
4. Obtainium braucht einmal die Erlaubnis **„Unbekannte Apps installieren“**. Die gibst du wie in Schritt 2.

**Updates:** Obtainium prüft regelmäßig auf neue Releases und meldet sich. Du kannst auch selbst in
Obtainium nach unten ziehen, dann tippst du auf **Aktualisieren**.

### Alternative ohne Obtainium

1. Auf dem Gerät https://github.com/chrizzzzilla/self/releases öffnen
2. Beim neuesten Release unter **Assets** die Datei `self-0.1.X.apk` antippen und herunterladen
3. Download öffnen → bei Nachfrage „Unbekannte Apps installieren“ für den Browser erlauben → **Installieren**

> Der Reiter **Actions** bietet die APK zusätzlich als **Artifact** an. Das ist eine **ZIP-Datei** (vorher entpacken)
> und nur mit GitHub-Login herunterladbar. Der Weg über die Releases ist einfacher.

---

## 3b. Vorschau-App „Self Dev“: erst testen, dann freigeben

Es gibt zwei Kanäle:

| Branch | App auf dem Gerät | Release auf GitHub |
|---|---|---|
| `dev` | **Self Dev** (eigene App, eigene Bibliothek) | Pre-Release `v0.1.N-dev` |
| `main` | **Self** (die „echte“ App) | Release `v0.1.N` |

Neue Funktionen landen zuerst auf `dev`. Etwa 5 Minuten nach dem Push meldet Obtainium ein Update für **Self Dev**.
Gefällt dir die Änderung, wird `dev` in `main` übernommen. Dann bekommt auch **Self** das Update.

**Self Dev in Obtainium einrichten (einmal pro Gerät):**
1. Obtainium → **App hinzufügen** → URL `https://github.com/chrizzzzilla/self`
2. Darunter die Optionen öffnen:
   - **„Pre-Releases einbeziehen“** einschalten
   - **„Release-Titel mit regulärem Ausdruck filtern“**: `Dev`
3. **Hinzufügen** → **Installieren**

Die normale **Self**-Installation in Obtainium bleibt, wie sie ist. Pre-Releases sind dort standardmäßig aus,
deshalb sieht sie nur die freigegebenen Versionen.

---

## 4. Warum Updates klappen: der feste Signatur-Schlüssel

Android installiert ein Update **nur**, wenn es mit **demselben Schlüssel** signiert ist wie die installierte App.
GitHub Actions würde bei jedem Lauf einen neuen, zufälligen Debug-Schlüssel erzeugen, dann würde jedes Update scheitern.

Deshalb gibt es einen **festen Release-Schlüssel**:

- Er liegt verschlüsselt als **GitHub Secret** im Repository (Settings → Secrets and variables → Actions):
  - `SELF_KEYSTORE_BASE64`: die Schlüsseldatei (Base64-kodiert)
  - `SELF_KEYSTORE_PASSWORD`: das Passwort
  - `SELF_KEY_ALIAS`: der Name des Schlüssels (`self`)
- Eine **Sicherungskopie** liegt auf dem Office-PC unter `C:\Chris\03_Assets\self\`.
  **Datei + Passwort unbedingt auch in Vaultwarden ablegen.**

⚠️ **Geht der Schlüssel verloren, gibt es keine Updates mehr.** Dann musst du die App deinstallieren
(die Bibliothek ist damit weg) und neu installieren.

### Schlüssel neu hinterlegen (nur falls nötig)

Wenn die Secrets einmal fehlen, gehst du so vor:

1. https://github.com/chrizzzzilla/self/settings/secrets/actions öffnen
2. **New repository secret** und die drei Werte von oben anlegen. Für `SELF_KEYSTORE_BASE64` den Inhalt der Datei
   `self-release.p12.base64.txt` aus der Sicherung einfügen.

Fehlen die Secrets, baut die App trotzdem, dann aber mit Debug-Schlüssel. Im Build-Log steht dazu eine Warnung.

---

## 5. Bücher öffnen

- **Aus dem Browser:** EPUB herunterladen → Download antippen → **„Öffnen mit: Self“**.
  Self kopiert das Buch in den eigenen Speicher. Du kannst den Download danach löschen.
- **In der App:** Bereich **Lesen** → **„Buch hinzufügen“** → Datei auswählen.
- Nur **DRM-freie** EPUBs funktionieren. Bücher mit Adobe-DRM oder LCP lassen sich nicht öffnen.

## 6. Bedienung im Reader

| Aktion | Wirkung |
|---|---|
| Tippen am **linken / rechten Rand** | Seite zurück / vor |
| **Wischen** | Seite zurück / vor |
| Tippen in die **Mitte** | Bedienleisten ein-/ausblenden |
| ☰ oben rechts | Inhaltsverzeichnis |
| **Aa** oben rechts | Schriftgröße, Farbmodus (System / Hell / Dunkel / Sepia) |
| ♪ oben rechts | Fokus-Musik (kommt später) |
| Regler unten | zu einer Stelle im Buch springen |

Die Leseposition wird automatisch gespeichert. In der Bibliothek entfernst du ein Buch, indem du es **lange drückst**.

---

## 7. Ohne Git am Code arbeiten (falls nötig)

Kleine Änderungen gehen direkt im Browser:

1. Im Repository die Datei öffnen → Stift-Symbol (**Edit**) → ändern → **Commit changes**
2. Neue Dateien: **Add file → Upload files** → Dateien hineinziehen → **Commit changes**

Jeder Commit auf `main` startet automatisch einen neuen Build.
