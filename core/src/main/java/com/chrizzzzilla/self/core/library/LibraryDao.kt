package com.chrizzzzilla.self.core.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    /** Alle Eintraege, zuletzt geoeffnete zuerst. */
    @Query("SELECT * FROM library_items ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<LibraryItem>>

    @Query("SELECT * FROM library_items WHERE id = :id")
    suspend fun get(id: String): LibraryItem?

    @Query("SELECT * FROM library_items WHERE kind = :kind AND title = :title AND creator IS :creator LIMIT 1")
    suspend fun findSame(kind: MediaKind, title: String, creator: String?): LibraryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LibraryItem)

    @Query("UPDATE library_items SET progress = :progress, positionJson = :positionJson WHERE id = :id")
    suspend fun updatePosition(id: String, progress: Double, positionJson: String)

    @Query("UPDATE library_items SET lastOpenedAt = :time WHERE id = :id")
    suspend fun markOpened(id: String, time: Long)

    @Query("DELETE FROM library_items WHERE id = :id")
    suspend fun delete(id: String)
}
