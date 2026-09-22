package com.kbul.spicycrab.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A single timed intake of a substance, e.g. `caffeine 40mg at 07:20`. Stored integer-only
 * ([amountInt]) to match the notes server's `value@HHMM` stat format. [key] is a lowercase
 * metric slug (`alcohol`, `caffeine`, `nicotine`, or a custom name).
 */
@Serializable
@Entity(tableName = "substance_entries")
data class SubstanceEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val amountInt: Int,
    val timestampEpoch: Long,
    val lastModifiedEpoch: Long,
    /**
     * True once this sample has been POSTed to the append-only `/api/stats` endpoint. Never
     * re-POST a synced sample (the server would duplicate it). Edits/deletes are local-only and
     * intentionally do NOT propagate to the server (the stats stream is append-only).
     */
    val synced: Boolean = false,
)
