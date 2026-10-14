package com.hafeziquran.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-saved mushaf page.
 *
 * [pageNumber] is 1-based to match the on-screen indicator, so a bookmark shown
 * as "পৃষ্ঠা ১২৩" round-trips to page index 122 internally.
 */
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val pageNumber: Int,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
)
