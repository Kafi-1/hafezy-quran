package com.hafeziquran.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The single "resume where you left off" row.
 *
 * Modelled as a fixed-id singleton rather than a list so the reader can upsert
 * with `REPLACE` and never has to reason about history or ordering.
 */
@Entity(tableName = "last_read")
data class LastReadEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val pageNumber: Int,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
