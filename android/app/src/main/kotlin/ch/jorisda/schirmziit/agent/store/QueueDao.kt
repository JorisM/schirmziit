package ch.jorisda.schirmziit.agent.store

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Keyed by hour so re-computing the current hour REPLACES its queued row, the
 * same rule the server applies. `json` is exactly what the core produced; Room
 * never parses it, so a wire-format change needs no schema migration.
 */
@Entity(tableName = "pending_hours")
data class PendingHourRow(
    @PrimaryKey val hourStartMillis: Long,
    val json: String,
    val computedAtMillis: Long,
)

/** The app still in the foreground when the last window closed. Exactly one row. */
@Entity(tableName = "carry_over")
data class CarryOverRow(
    @PrimaryKey val id: Int = 0,
    val packageName: String,
    val sinceMillis: Long,
)

/**
 * Playback state when the last window closed: what was playing, whether the
 * screen was off, and when the current background stretch began. Exactly one
 * row, same shape and same reason as [CarryOverRow].
 */
@Entity(tableName = "playback_carry")
data class PlaybackCarryRow(
    @PrimaryKey val id: Int = 0,
    val playing: String?,
    val screenOff: Boolean,
    val sinceMillis: Long?,
)

/** Debug-only ring buffer. Never uploaded; pruned to 7 days. */
@Entity(tableName = "raw_events")
data class RawEventRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
    val json: String,
)

@Dao
interface QueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(rows: List<PendingHourRow>)

    @Query("SELECT * FROM pending_hours ORDER BY hourStartMillis ASC")
    fun pending(): List<PendingHourRow>

    @Query("DELETE FROM pending_hours WHERE hourStartMillis IN (:hourStarts)")
    fun delete(hourStarts: List<Long>)

    @Query("SELECT COUNT(*) FROM pending_hours")
    fun pendingCount(): Int

    @Query("SELECT * FROM carry_over WHERE id = 0")
    fun carryOver(): CarryOverRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setCarryOver(row: CarryOverRow)

    @Query("SELECT COUNT(*) FROM carry_over")
    fun carryOverCount(): Int

    @Query("DELETE FROM carry_over")
    fun clearCarryOver()

    @Insert
    fun appendRaw(rows: List<RawEventRow>)

    @Query("DELETE FROM raw_events WHERE atMillis < :millis")
    fun pruneRawBefore(millis: Long)

    @Query("SELECT COUNT(*) FROM raw_events")
    fun rawCount(): Int

    @Query("SELECT * FROM playback_carry WHERE id = 0")
    fun playbackCarry(): PlaybackCarryRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setPlaybackCarry(row: PlaybackCarryRow)

    @Query("DELETE FROM playback_carry")
    fun clearPlaybackCarry()
}
