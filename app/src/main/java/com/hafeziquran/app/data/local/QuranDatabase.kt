package com.hafeziquran.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hafeziquran.app.data.local.dao.BookmarkDao
import com.hafeziquran.app.data.local.dao.LastReadDao
import com.hafeziquran.app.data.local.entity.BookmarkEntity
import com.hafeziquran.app.data.local.entity.LastReadEntity

@Database(
    entities = [BookmarkEntity::class, LastReadEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun lastReadDao(): LastReadDao

    companion object {
        const val NAME = "hafezi_quran.db"
    }
}
