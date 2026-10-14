package com.hafeziquran.app.di

import android.content.Context
import androidx.room.Room
import com.hafeziquran.app.data.local.QuranDatabase
import com.hafeziquran.app.data.local.dao.BookmarkDao
import com.hafeziquran.app.data.local.dao.LastReadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): QuranDatabase =
        Room.databaseBuilder(context, QuranDatabase::class.java, QuranDatabase.NAME)
            // No fallbackToDestructiveMigration: losing a reader's bookmarks
            // silently is worse than a visible crash we can fix with a real
            // migration before shipping the next version.
            .build()

    @Provides
    fun provideBookmarkDao(db: QuranDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideLastReadDao(db: QuranDatabase): LastReadDao = db.lastReadDao()
}
