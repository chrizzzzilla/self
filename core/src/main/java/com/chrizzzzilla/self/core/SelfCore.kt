package com.chrizzzzilla.self.core

import android.content.Context
import androidx.room.Room
import com.chrizzzzilla.self.core.library.LibraryRepository
import com.chrizzzzilla.self.core.library.SelfDatabase
import com.chrizzzzilla.self.core.playback.FocusAudio
import com.chrizzzzilla.self.core.playback.NoFocusAudio
import com.chrizzzzilla.self.core.settings.SettingsRepository

/**
 * Einfacher Service-Locator fuer die ganze App (bewusst ohne DI-Framework).
 * Wird einmal in `SelfApp.onCreate()` initialisiert.
 */
object SelfCore {

    lateinit var library: LibraryRepository
        private set

    lateinit var settings: SettingsRepository
        private set

    /** Wird in Phase 2 von `feature-focus` ersetzt. */
    @Volatile
    var focusAudio: FocusAudio = NoFocusAudio

    @Synchronized
    fun init(context: Context) {
        if (::library.isInitialized) return
        val app = context.applicationContext
        val db = Room.databaseBuilder(app, SelfDatabase::class.java, "self.db")
            .addMigrations(SelfDatabase.MIGRATION_1_2)
            .build()
        library = LibraryRepository(db.libraryDao())
        settings = SettingsRepository(app)
    }
}
