package com.chrizzzzilla.self.core.library

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LibraryItem::class], version = 1, exportSchema = false)
abstract class SelfDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}
