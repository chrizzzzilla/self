package com.chrizzzzilla.self.reader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.chrizzzzilla.self.core.SelfCore
import com.chrizzzzilla.self.core.library.LibraryItem
import com.chrizzzzilla.self.core.library.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Importiert ein EPUB in die Bibliothek:
 * Datei in den App-Speicher kopieren (bleibt erhalten, auch wenn der Download geloescht wird),
 * Titel/Autor/Cover auslesen, Eintrag anlegen.
 */
internal object BookImporter {

    private const val COVER_MAX_HEIGHT = 900

    suspend fun import(context: Context, uri: Uri): LibraryItem = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val id = UUID.randomUUID().toString()
        val booksDir = File(app.filesDir, "books").apply { mkdirs() }
        val sourceName = displayName(app, uri, keepExtension = true) ?: uri.lastPathSegment.orEmpty()
        val ext = if (sourceName.endsWith(".cbz", ignoreCase = true)) "cbz" else "epub"
        val file = File(booksDir, "$id.$ext")

        try {
            val input = app.contentResolver.openInputStream(uri)
                ?: throw ReaderException(R.string.reader_error_read)
            input.use { source -> file.outputStream().use { target -> source.copyTo(target) } }
        } catch (e: IOException) {
            file.delete()
            throw ReaderException(R.string.reader_error_read, e)
        } catch (e: SecurityException) {
            file.delete()
            throw ReaderException(R.string.reader_error_read, e)
        }

        val publication = try {
            ReadiumEngine.open(app, file)
        } catch (e: Exception) {
            file.delete()
            throw e
        }

        try {
            val title = publication.metadata.title?.takeIf { it.isNotBlank() }
                ?: displayName(app, uri)
                ?: app.getString(R.string.reader_untitled)
            val creator = publication.metadata.authors
                .joinToString(", ") { it.name }
                .ifBlank { null }

            // Dasselbe Buch schon vorhanden? Dann die neue Kopie verwerfen und das alte oeffnen.
            val kind = if (publication.conformsTo(Publication.Profile.DIVINA)) MediaKind.COMIC else MediaKind.BOOK
            SelfCore.library.findSame(kind, title, creator)?.let { existing ->
                file.delete()
                return@withContext existing
            }

            val now = System.currentTimeMillis()
            val item = LibraryItem(
                id = id,
                kind = kind,
                title = title,
                creator = creator,
                coverPath = saveCover(app, id, publication),
                filePath = file.absolutePath,
                lastOpenedAt = now,
                addedAt = now,
                genre = publication.metadata.subjects.firstOrNull()?.name?.takeIf { it.isNotBlank() },
            )
            SelfCore.library.add(item)
            item
        } finally {
            publication.close()
        }
    }

    private suspend fun saveCover(context: Context, id: String, publication: Publication): String? {
        val bitmap = runCatching { publication.cover() }.getOrNull() ?: return null
        val scaled = if (bitmap.height > COVER_MAX_HEIGHT) {
            val width = (bitmap.width * COVER_MAX_HEIGHT.toFloat() / bitmap.height).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, width, COVER_MAX_HEIGHT, true)
        } else {
            bitmap
        }
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "$id.jpg")
        return runCatching {
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        }.getOrNull()
    }

    private fun displayName(context: Context, uri: Uri, keepExtension: Boolean = false): String? = runCatching {
        val raw = if (uri.scheme == "file") {
            uri.lastPathSegment
        } else {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }
        val name = if (keepExtension) raw else raw?.removeSuffix(".epub")?.removeSuffix(".cbz")
        name?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
