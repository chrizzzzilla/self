package com.chrizzzzilla.self.reader

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

/** Fehler mit einer fuer den Nutzer lesbaren (deutschen) Meldung. */
class ReaderException(
    @StringRes val messageRes: Int,
    cause: Throwable? = null,
) : Exception(null, cause)

/**
 * Readium-Bausteine zum Oeffnen von Publikationen (einmal pro Prozess).
 * Ohne ContentProtection → nur DRM-freie EPUBs (kein LCP, kein Adobe-DRM).
 */
internal object ReadiumEngine {

    private class Services(context: Context) {
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
        val publicationOpener = PublicationOpener(
            publicationParser = DefaultPublicationParser(
                context,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = null,
            ),
        )
    }

    @Volatile
    private var services: Services? = null

    private fun services(context: Context): Services =
        services ?: synchronized(this) {
            services ?: Services(context.applicationContext).also { services = it }
        }

    /** Oeffnet ein EPUB aus dem App-Speicher. Der Aufrufer muss die Publication spaeter schliessen. */
    suspend fun open(context: Context, file: File): Publication = withContext(Dispatchers.IO) {
        if (!file.exists()) throw ReaderException(R.string.reader_error_missing)
        val s = services(context)

        val asset = s.assetRetriever.retrieve(file)
            .getOrElse { throw ReaderException(R.string.reader_error_read) }

        val publication = s.publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                throw ReaderException(R.string.reader_error_open)
            }

        if (!publication.conformsTo(Publication.Profile.EPUB) && !publication.conformsTo(Publication.Profile.DIVINA)) {
            publication.close()
            throw ReaderException(R.string.reader_error_not_epub)
        }
        publication
    }
}
