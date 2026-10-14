package com.hafeziquran.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hafeziquran.app.data.local.entity.LastReadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LastReadDao {

    @Query("SELECT * FROM last_read WHERE id = ${LastReadEntity.SINGLETON_ID} LIMIT 1")
    fun observe(): Flow<LastReadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LastReadEntity)
}
