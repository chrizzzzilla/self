package com.chrizzzzilla.self.core.library

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LibraryItem::class], version = 2, exportSchema = false)
abstract class SelfDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        /** v2: Spalte „genre“ */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_items ADD COLUMN genre TEXT")
            }
        }
    }
}
