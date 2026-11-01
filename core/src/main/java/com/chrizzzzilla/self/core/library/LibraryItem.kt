package com.chrizzzzilla.self.core.library

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Art eines Bibliothekseintrags. Bewusst allgemein gehalten:
 * spaeter koennen z. B. Hoerbuecher oder Fokus-Presets dazukommen.
 */
enum class MediaKind { BOOK }

/**
 * Ein Eintrag in der Bibliothek – nicht nur fuer Buecher gedacht.
 *
 * @property filePath Kopie der Datei im App-Speicher (bleibt, auch wenn das Original geloescht wird).
 * @property coverPath Vorschaubild im App-Speicher, falls vorhanden.
 * @property progress Gesamtfortschritt 0.0 … 1.0.
 * @property positionJson Genaue Leseposition (beim Reader: Readium-Locator als JSON).
 */
@Entity(tableName = "library_items")
data class LibraryItem(
    @PrimaryKey val id: String,
    val kind: MediaKind,
    val title: String,
    val creator: String?,
    val coverPath: String?,
    val filePath: String,
    val progress: Double = 0.0,
    val positionJson: String? = null,
    val lastOpenedAt: Long,
    val addedAt: Long,
)
