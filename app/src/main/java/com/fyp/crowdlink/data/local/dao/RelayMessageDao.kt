package com.fyp.crowdlink.data.local.dao

import androidx.room.*
import com.fyp.crowdlink.data.local.entity.RelayMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RelayMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: RelayMessageEntity)

    @Query("SELECT * FROM relay_messages WHERE expiresAt > :now ORDER BY timestamp ASC")
    fun getActiveRelayQueue(now: Long = System.currentTimeMillis()): Flow<List<RelayMessageEntity>>

    @Query("DELETE FROM relay_messages WHERE messageId = :messageId")
    suspend fun delete(messageId: String)

    @Query("DELETE FROM relay_messages WHERE expiresAt <= :now")
    suspend fun purgeExpired(now: Long = System.currentTimeMillis())

}