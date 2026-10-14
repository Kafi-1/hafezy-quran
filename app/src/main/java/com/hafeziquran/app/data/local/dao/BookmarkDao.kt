package com.hafeziquran.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hafeziquran.app.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    /** Drives the "filled vs outline" bookmark button on the current page. */
    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE pageNumber = :page)")
    fun observeIsBookmarked(page: Int): Flow<Boolean>

    @Query("SELECT * FROM bookmarks WHERE pageNumber = :page LIMIT 1")
    suspend fun findByPage(page: Int): BookmarkEntity?

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
