package com.kbul.spicycrab.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kbul.spicycrab.data.db.entities.SubstanceEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface SubstanceEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SubstanceEntry): Long

    @Update
    suspend fun update(entry: SubstanceEntry)

    @Delete
    suspend fun delete(entry: SubstanceEntry)

    @Query("SELECT * FROM substance_entries ORDER BY timestampEpoch DESC")
    fun observeAll(): Flow<List<SubstanceEntry>>

    @Query(
        "SELECT * FROM substance_entries " +
            "WHERE timestampEpoch >= :startEpoch AND timestampEpoch < :endEpoch " +
            "ORDER BY timestampEpoch ASC"
    )
    fun observeForDay(startEpoch: Long, endEpoch: Long): Flow<List<SubstanceEntry>>

    @Query("SELECT * FROM substance_entries WHERE synced = 0 ORDER BY timestampEpoch ASC")
    suspend fun unsynced(): List<SubstanceEntry>

    @Query("UPDATE substance_entries SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("DELETE FROM substance_entries")
    suspend fun deleteAll()
}
