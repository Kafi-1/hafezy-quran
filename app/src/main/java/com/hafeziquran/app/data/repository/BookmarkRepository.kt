package com.hafeziquran.app.data.repository

import com.hafeziquran.app.data.local.dao.BookmarkDao
import com.hafeziquran.app.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Bookmark persistence. Pure pass-through today; kept as a seam so the
 *  ViewModel never talks to Room directly (Clean Architecture boundary). */
@Singleton
class BookmarkRepository @Inject constructor(
    private val dao: BookmarkDao,
) {
    fun observeBookmarks(): Flow<List<BookmarkEntity>> = dao.observeAll()

    fun observeIsBookmarked(page: Int): Flow<Boolean> = dao.observeIsBookmarked(page)

    suspend fun addBookmark(page: Int, title: String): Long =
        dao.insert(BookmarkEntity(pageNumber = page, title = title))

    suspend fun updateBookmark(bookmark: BookmarkEntity) = dao.update(bookmark)

    suspend fun deleteBookmark(bookmark: BookmarkEntity) = dao.delete(bookmark)

    suspend fun deleteBookmark(id: Long) = dao.deleteById(id)

    /**
     * Flips the bookmark on [page]. Returns `true` if the page is bookmarked
     * afterwards, so the caller can pick the right confirmation message.
     */
    suspend fun toggleBookmark(page: Int, title: String): Boolean {
        val existing = dao.findByPage(page)
        return if (existing != null) {
            dao.delete(existing)
            false
        } else {
            dao.insert(BookmarkEntity(pageNumber = page, title = title))
            true
        }
    }
}
