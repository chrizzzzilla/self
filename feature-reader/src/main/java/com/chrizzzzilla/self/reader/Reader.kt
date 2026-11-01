package com.chrizzzzilla.self.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.chrizzzzilla.self.core.SelfCore
import com.chrizzzzilla.self.core.library.LibraryItem
import com.chrizzzzilla.self.core.settings.AppSettings
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import java.io.File

/**
 * Ein geoeffnetes Buch. Lebt im Speicher, solange der Reader offen ist.
 * (Readium braucht die Publication schon in `ReaderActivity.onCreate`, bevor das
 * Navigator-Fragment entsteht – deshalb wird sie vorher hier geoeffnet.)
 */
internal class ReaderSession(
    val item: LibraryItem,
    val publication: Publication,
    initialLocator: Locator?,
    val initialSettings: AppSettings,
) {
    /** Comics/Manga (CBZ) haben das Divina-Profil und brauchen den Bild-Navigator. */
    val isComic: Boolean = publication.conformsTo(Publication.Profile.DIVINA)
    val navigatorFactory by lazy { EpubNavigatorFactory(publication) }

    /** Letzte bekannte Position – fuer einen Neuaufbau der Activity. */
    @Volatile
    var lastLocator: Locator? = initialLocator

    fun close() = publication.close()
}

internal object ReaderSessions {
    @Volatile
    var current: ReaderSession? = null
        private set

    @Synchronized
    fun replace(session: ReaderSession) {
        current?.close()
        current = session
    }

    @Synchronized
    fun close(session: ReaderSession) {
        if (current === session) current = null
        session.close()
    }
}

/** Oeffentliche Einstiegspunkte des Reader-Moduls. */
object Reader {

    internal const val EXTRA_ITEM_ID = "com.chrizzzzilla.self.reader.ITEM_ID"

    /** Oeffnet einen Bibliothekseintrag im Reader. Wirft [ReaderException]. */
    suspend fun open(context: Context, item: LibraryItem) {
        val publication = ReadiumEngine.open(context, File(item.filePath))
        val locator = item.positionJson?.let { json ->
            runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
        }
        val settings = SelfCore.settings.current()
        ReaderSessions.replace(ReaderSession(item, publication, locator, settings))
        SelfCore.library.markOpened(item.id)

        context.startActivity(
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_ITEM_ID, item.id),
        )
    }

    /** Importiert eine Datei (Dateiauswahl oder „Öffnen mit“) und oeffnet sie direkt. */
    suspend fun importAndOpen(context: Context, uri: Uri) {
        val item = BookImporter.import(context, uri)
        open(context, item)
    }
}
