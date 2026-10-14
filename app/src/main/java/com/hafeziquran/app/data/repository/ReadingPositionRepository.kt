package com.hafeziquran.app.data.repository

import com.hafeziquran.app.data.local.dao.LastReadDao
import com.hafeziquran.app.data.local.entity.LastReadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers the last opened mushaf page so the app resumes instantly. */
@Singleton
class ReadingPositionRepository @Inject constructor(
    private val dao: LastReadDao,
) {
    /** Emits the 1-based page, or `null` before the user has read anything. */
    fun observeLastPage(): Flow<Int?> = dao.observe().map { it?.pageNumber }

    suspend fun saveLastPage(page: Int) {
        dao.upsert(LastReadEntity(pageNumber = page))
    }
}
