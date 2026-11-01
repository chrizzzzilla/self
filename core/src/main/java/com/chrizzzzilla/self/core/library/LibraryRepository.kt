package com.chrizzzzilla.self.core.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** Zugriff auf die Bibliothek. Kennt keine Buch-Details – das macht das jeweilige Feature-Modul. */
class LibraryRepository(private val dao: LibraryDao) {

    fun observeAll(): Flow<List<LibraryItem>> = dao.observeAll()

    suspend fun get(id: String): LibraryItem? = dao.get(id)

    suspend fun findSame(kind: MediaKind, title: String, creator: String?): LibraryItem? =
        dao.findSame(kind, title, creator)

    suspend fun add(item: LibraryItem) = dao.upsert(item)

    suspend fun updatePosition(id: String, progress: Double, positionJson: String) =
        dao.updatePosition(id, progress.coerceIn(0.0, 1.0), positionJson)

    suspend fun markOpened(id: String) = dao.markOpened(id, System.currentTimeMillis())

    /** Entfernt den Eintrag samt Kopie der Datei und Vorschaubild. */
    suspend fun remove(item: LibraryItem) {
        dao.delete(item.id)
        withContext(Dispatchers.IO) {
            File(item.filePath).delete()
            item.coverPath?.let { File(it).delete() }
        }
    }
}
